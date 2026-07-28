package com.fenn.callshield.domain.usecase

import com.fenn.callshield.Phase2Flags
import com.fenn.callshield.billing.BillingManager
import com.fenn.callshield.data.local.ContactsLookupHelper
import com.fenn.callshield.data.local.VipContactsLookupHelper
import com.fenn.callshield.data.preferences.ScreeningPreferences
import com.fenn.callshield.domain.model.BehavioralSignals
import com.fenn.callshield.domain.model.BlockingPreset
import com.fenn.callshield.domain.model.CallDecision
import com.fenn.callshield.domain.model.DecisionSource
import com.fenn.callshield.domain.model.ReputationSource
import com.fenn.callshield.domain.repository.BlocklistRepository
import com.fenn.callshield.domain.repository.PrefixRuleRepository
import com.fenn.callshield.domain.repository.ReputationRepository
import com.fenn.callshield.domain.repository.WhitelistRepository
import com.fenn.callshield.screening.CallFrequencyAnalyzer
import com.fenn.callshield.util.PhoneNumberHasher
import javax.inject.Inject

/**
 * Core screening logic. Implements the call decision priority:
 *   Whitelist → Blocklist → Prefix → Behavioral (Phase 2) → Seed DB → Allow
 *
 * Spam calls are always Silenced (ring suppressed, shows in missed calls) — never
 * auto-rejected. Confidence score only changes labeling (Likely Spam vs Known Spam).
 *
 * Called from [CallShieldScreeningService] within the 1500ms Android budget.
 */
class ScreenCallUseCase @Inject constructor(
    private val hasher: PhoneNumberHasher,
    private val whitelistRepo: WhitelistRepository,
    private val blocklistRepo: BlocklistRepository,
    private val prefixRuleRepo: PrefixRuleRepository,
    private val reputationRepo: ReputationRepository,
    private val settingsUseCase: GetScreeningSettingsUseCase,
    private val billingManager: BillingManager,
    private val frequencyAnalyzer: CallFrequencyAnalyzer,
    private val screeningPreferences: ScreeningPreferences,
    private val contactsLookupHelper: ContactsLookupHelper,
    private val vipContactsLookupHelper: VipContactsLookupHelper,
    private val evaluateAdvancedBlocking: EvaluateAdvancedBlockingUseCase,
) {

    suspend fun execute(rawNumber: String?): CallDecision {
        val isPro = billingManager.isPro.value
        val settings = settingsUseCase.get()

        // ── 1. Hidden number ─────────────────────────────────────────────────
        if (rawNumber.isNullOrBlank()) {
            return if (isPro && settings.blockHiddenNumbers) {
                CallDecision.Reject(DecisionSource.HIDDEN)
            } else {
                CallDecision.Allow
            }
        }

        val e164 = hasher.normalise(rawNumber)
        val hash = if (e164 != null) hasher.hash(rawNumber) else null

        // ── 2. Whitelist ──────────────────────────────────────────────────────
        if (hash != null && whitelistRepo.contains(hash)) {
            return CallDecision.Allow
        }

        // ── 3. Blocklist ──────────────────────────────────────────────────────
        if (hash != null && blocklistRepo.contains(hash)) {
            return CallDecision.Reject(DecisionSource.BLOCKLIST)
        }

        val advPolicy = screeningPreferences.getAdvancedBlockingPolicy()

        // ── 3.5. VIP Contacts Only (Pro) ─────────────────────────────────────
        // Silence, not Reject — this screen's own copy promises "silenced", not disconnected.
        if (isPro && advPolicy.vipContactsOnlyEnabled && e164 != null) {
            if (!vipContactsLookupHelper.isVip(e164)) {
                return CallDecision.Silence(1.0, "vip_only", DecisionSource.ADVANCED_BLOCKING)
            }
        }

        // ── 3b. Advanced Blocking Policies ───────────────────────────────────
        val isContact = if (e164 != null) contactsLookupHelper.isInContacts(e164) else false
        if (advPolicy.preset != BlockingPreset.BALANCED || advPolicy.isCustomized()) {
            evaluateAdvancedBlocking.evaluate(e164, isContact, advPolicy, isPro)
                ?.let { return it }
        }

        // ── 4. Prefix rules ───────────────────────────────────────────────────
        if (e164 != null) {
            val prefixMatch = prefixRuleRepo.findMatch(e164)
            if (prefixMatch != null) {
                return when (prefixMatch.action) {
                    "block" -> CallDecision.Reject(DecisionSource.PREFIX)
                    "silence" -> CallDecision.Silence(1.0, null, DecisionSource.PREFIX)
                    "allow" -> CallDecision.Allow
                    else -> CallDecision.Allow
                }
            }
        }

        // ── 4b. Behavioral signals (Phase 2) ─────────────────────────────────
        val behavioral: BehavioralSignals = if (Phase2Flags.BEHAVIORAL_DETECTION && hash != null) {
            BehavioralSignals(
                frequencyAnomaly = frequencyAnalyzer.isFrequencyAnomaly(hash),
                burstPattern = frequencyAnalyzer.isBurstPattern(hash),
                shortRing = frequencyAnalyzer.hadRecentShortRing(hash),
            )
        } else {
            BehavioralSignals.NONE
        }

        // Burst pattern alone is strong enough to flag the call — skip contacts
        if (behavioral.burstPattern && !isContact) {
            return CallDecision.Flag(0.5, "burst_pattern", DecisionSource.BEHAVIORAL)
        }

        // ── 5. Seed DB ─────────────────────────────────────────────────────────
        if (hash != null) {
            val result = reputationRepo.lookup(hash)
            if (result.source == ReputationSource.SEED_DB) {
                return CallDecision.Silence(result.confidenceScore, result.category, DecisionSource.SEED_DB)
            }

            // Unknown reputation but behavioral signals present → Flag
            if (behavioral.hasAnySignal) {
                val behavioralCategory = when {
                    behavioral.burstPattern -> "burst_pattern"
                    behavioral.frequencyAnomaly -> "frequency_anomaly"
                    behavioral.shortRing -> "short_ring"
                    else -> "behavioral"
                }
                return CallDecision.Flag(0.3, behavioralCategory, DecisionSource.BEHAVIORAL)
            }
        }

        // ── 7. Default: allow ─────────────────────────────────────────────────
        return CallDecision.Allow
    }
}
