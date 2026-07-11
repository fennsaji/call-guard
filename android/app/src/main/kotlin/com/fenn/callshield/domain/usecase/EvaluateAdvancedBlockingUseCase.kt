package com.fenn.callshield.domain.usecase

import com.fenn.callshield.domain.model.AdvancedBlockingPolicy
import com.fenn.callshield.domain.model.BlockingPreset
import com.fenn.callshield.domain.model.CallDecision
import com.fenn.callshield.domain.model.CountryFilterMode
import com.fenn.callshield.domain.model.DecisionSource
import com.fenn.callshield.domain.model.UnknownCallAction
import com.fenn.callshield.domain.repository.BlocklistRepository
import com.fenn.callshield.domain.repository.CallHistoryRepository
import com.fenn.callshield.util.HomeCountryProvider
import java.util.Calendar
import javax.inject.Inject

/**
 * Evaluates the Advanced Blocking Policy for an incoming call.
 *
 * Returns null = no match (continue pipeline).
 * Returns non-null = policy decision (short-circuit).
 *
 * Evaluation order:
 *   1. Night Guard — silence/reject during configured hours (Pro: day-of-week aware)
 *   1b. Work Focus Window (Pro) — second silence window for work hours
 *   2. Contacts Only — reject unknown callers
 *   3. Silence Unknown — silence non-contacts
 *   4. International Lock — silence/reject numbers outside the device's home country
 *   4b. Country filter (Pro) — whitelist or blacklist specific countries
 *   4c. Block Unrecognized ISD (Pro) — block calls with unresolvable country codes
 *   5. Auto-Escalate — auto-add to blocklist after N rejections
 */
class EvaluateAdvancedBlockingUseCase @Inject constructor(
    private val callHistoryRepo: CallHistoryRepository,
    private val blocklistRepo: BlocklistRepository,
    private val homeCountryProvider: HomeCountryProvider,
) {

    suspend fun evaluate(
        e164Number: String?,
        numberHash: String?,
        isContact: Boolean,
        policy: AdvancedBlockingPolicy,
        isPro: Boolean,
    ): CallDecision? {
        // Balanced preset with no customisation: skip entirely to preserve existing behaviour
        if (policy.preset == BlockingPreset.BALANCED && !policy.isCustomized()) return null

        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        // ISO day: 0=Mon … 6=Sun (Calendar.DAY_OF_WEEK: 1=Sun, 2=Mon … 7=Sat)
        val todayIsoDay = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        // 1. Night Guard
        if (policy.nightGuardEnabled) {
            val activeToday = !isPro || todayIsoDay in policy.nightGuardDays
            if (activeToday &&
                isInTimeWindow(currentHour, policy.nightGuardStartHour, policy.nightGuardEndHour) &&
                !isContact
            ) {
                return when {
                    isPro && policy.nightGuardAction == UnknownCallAction.REJECT ->
                        CallDecision.Reject(DecisionSource.ADVANCED_BLOCKING)
                    else ->
                        CallDecision.Silence(1.0, "night_guard", DecisionSource.ADVANCED_BLOCKING)
                }
            }
        }

        // 1b. Work Focus Window (Pro)
        if (isPro && policy.workFocusEnabled) {
            val activeToday = todayIsoDay in policy.workFocusDays
            if (activeToday &&
                isInTimeWindow(currentHour, policy.workFocusStartHour, policy.workFocusEndHour) &&
                !isContact
            ) {
                return when (policy.workFocusAction) {
                    UnknownCallAction.REJECT -> CallDecision.Reject(DecisionSource.ADVANCED_BLOCKING)
                    else -> CallDecision.Silence(1.0, "work_focus", DecisionSource.ADVANCED_BLOCKING)
                }
            }
        }

        // 2. Contacts Only — reject immediately; caller gets busy signal, no missed call in your log
        if (policy.allowContactsOnly && !isContact) {
            return CallDecision.Reject(DecisionSource.ADVANCED_BLOCKING)
        }

        // 3. Silence Unknown
        if (policy.silenceUnknownNumbers && !isContact) {
            return CallDecision.Silence(0.5, "silence_unknown", DecisionSource.ADVANCED_BLOCKING)
        }

        // 4. International Lock — numbers outside the device's home country
        val homePrefix = homeCountryProvider.callingCodePrefix
        if (policy.blockInternational && e164Number != null && !e164Number.startsWith(homePrefix)) {
            return CallDecision.Silence(1.0, "international_lock", DecisionSource.ADVANCED_BLOCKING)
        }

        // 4b. Country filter (Pro) — whitelist or blacklist specific countries
        if (isPro && policy.countryFilterMode != CountryFilterMode.OFF &&
            e164Number != null && policy.countryFilterList.isNotEmpty()
        ) {
            val callerIso = homeCountryProvider.isoFromE164(e164Number)
            if (callerIso != null) {
                when (policy.countryFilterMode) {
                    CountryFilterMode.ALLOW_ONLY ->
                        if (callerIso !in policy.countryFilterList)
                            return CallDecision.Silence(1.0, "country_not_allowed", DecisionSource.ADVANCED_BLOCKING)
                    CountryFilterMode.BLOCK_LISTED ->
                        if (callerIso in policy.countryFilterList)
                            return CallDecision.Silence(1.0, "country_blocked", DecisionSource.ADVANCED_BLOCKING)
                    CountryFilterMode.OFF -> Unit
                }
            }
        }

        // 4c. Block Unrecognized ISD (Pro) — no resolvable country code
        if (isPro && policy.blockUnrecognizedIsd && e164Number != null) {
            val callerIso = homeCountryProvider.isoFromE164(e164Number)
            if (callerIso == null) {
                return CallDecision.Silence(1.0, "unrecognized_isd", DecisionSource.ADVANCED_BLOCKING)
            }
        }

        // 5. Auto-Escalate — count only the last 30 days to avoid permanently penalising
        // numbers that were blocked long ago and later manually cleared.
        if (policy.autoEscalateEnabled && numberHash != null) {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1_000
            val rejections = callHistoryRepo.countRejections(numberHash, since = thirtyDaysAgo)
            if (rejections >= policy.autoEscalateThreshold &&
                !blocklistRepo.contains(numberHash)
            ) {
                blocklistRepo.add(numberHash, "Auto-blocked (${rejections} rejections)")
                return CallDecision.Reject(DecisionSource.ADVANCED_BLOCKING)
            }
        }

        return null
    }

    private fun isInTimeWindow(hour: Int, start: Int, end: Int): Boolean =
        if (start > end) {
            // Crosses midnight, e.g. 22–7
            hour >= start || hour < end
        } else {
            hour in start until end
        }
}

