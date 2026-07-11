package com.fenn.callshield.ui.screens.home

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenn.callshield.billing.BillingManager
import com.fenn.callshield.billing.PlanType
import com.fenn.callshield.data.local.dao.DndCommandDao
import com.fenn.callshield.data.local.dao.ScamDigestDao
import com.fenn.callshield.data.local.dao.TraiReportDao
import com.fenn.callshield.data.local.entity.CallHistoryEntry
import com.fenn.callshield.data.local.entity.DndCommandEntry
import com.fenn.callshield.data.local.entity.ScamDigestEntry
import com.fenn.callshield.data.local.entity.TraiReportEntry
import com.fenn.callshield.data.preferences.ScreeningPreferences
import com.fenn.callshield.domain.repository.BlocklistRepository
import com.fenn.callshield.domain.repository.CallHistoryRepository
import com.fenn.callshield.domain.repository.CallStats
import com.fenn.callshield.domain.repository.WhitelistRepository
import com.fenn.callshield.domain.usecase.MarkNotSpamUseCase
import com.fenn.callshield.util.DndOperator
import com.fenn.callshield.util.HomeCountryProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentCalls: List<CallHistoryEntry> = emptyList(),
    val stats: CallStats = CallStats(0, 0),
    val isScreeningActive: Boolean = true,
    val scamDigest: ScamDigestEntry? = null,
    val blockHidden: Boolean = false,
    val dndOperator: DndOperator? = null,
    val dndCommand: String? = null,       // "FULL", "PROMO", "PARTIAL", or null
    val dndConfirmed: Boolean = false,    // false = sent but awaiting TRAI reply
    val isIndiaDevice: Boolean = true,    // DND management is India-only
    val blockedHashes: Set<String> = emptySet(),
    val whitelistedHashes: Set<String> = emptySet(),
    val traiReportedHashes: Set<String> = emptySet(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val callHistoryRepo: CallHistoryRepository,
    private val markNotSpamUseCase: MarkNotSpamUseCase,
    private val scamDigestDao: ScamDigestDao,
    private val screeningPreferences: ScreeningPreferences,
    private val traiReportDao: TraiReportDao,
    private val billingManager: BillingManager,
    private val dndCommandDao: DndCommandDao,
    private val blocklistRepo: BlocklistRepository,
    private val whitelistRepo: WhitelistRepository,
    private val homeCountryProvider: HomeCountryProvider,
) : ViewModel() {

    val isPro: StateFlow<Boolean> = billingManager.isPro

    /** True when the user holds a Lifetime plan but still has an active subscription running. */
    val showLifetimeConflictReminder: StateFlow<Boolean> =
        billingManager.planType
            .combine(billingManager.hasConflictingSubscription) { plan, conflict ->
                plan == PlanType.PRO_LIFETIME && conflict
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _snackbarMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private val _protectionState = screeningPreferences.observeBlockHiddenNumbers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _isScreeningActive = MutableStateFlow(false)

    /** Call on every screen resume to reflect current Call Screening role status. */
    fun refreshScreeningRole(context: Context) {
        _isScreeningActive.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                context.getSystemService(RoleManager::class.java)
                    ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
            } catch (_: Exception) {
                // Some OEM ROMs (e.g. MIUI) throw on RoleManager calls — treat as inactive
                false
            }
        } else {
            true
        }
    }

    private data class CallsState(
        val recentCalls: List<CallHistoryEntry>,
        val scamDigest: ScamDigestEntry?,
        val stats: CallStats,
        val blockHidden: Boolean,
        val isScreeningActive: Boolean,
    )

    private data class ListsState(
        val dndOperator: DndOperator?,
        val dndEntry: DndCommandEntry?,
        val blockedHashes: Set<String>,
        val whitelistedHashes: Set<String>,
        val traiReportedHashes: Set<String>,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            callHistoryRepo.observeRecent(),
            scamDigestDao.observeLatest(),
            callHistoryRepo.observeStats(),
            _protectionState,
            _isScreeningActive,
        ) { calls, digest, stats, blockHidden, isActive ->
            CallsState(calls, digest, stats, blockHidden, isActive)
        },
        combine(
            screeningPreferences.observeDndOperator(),
            dndCommandDao.observeLatestActive(),
            blocklistRepo.observeAll().map { list -> list.map { it.numberHash }.toSet() },
            whitelistRepo.observeAll().map { list -> list.map { it.numberHash }.toSet() },
            traiReportDao.observeAllHashes().map { it.toSet() },
        ) { dndOperator, dndEntry, blockedHashes, whitelistedHashes, traiReportedHashes ->
            ListsState(dndOperator, dndEntry, blockedHashes, whitelistedHashes, traiReportedHashes)
        },
    ) { calls, lists ->
        HomeUiState(
            recentCalls = calls.recentCalls,
            stats = calls.stats,
            isScreeningActive = calls.isScreeningActive,
            scamDigest = calls.scamDigest,
            blockHidden = calls.blockHidden,
            dndOperator = lists.dndOperator,
            dndCommand = lists.dndEntry?.command?.takeIf { it != "DEACTIVATE" },
            dndConfirmed = lists.dndEntry?.confirmedByUser ?: false,
            isIndiaDevice = homeCountryProvider.isoCode == "IN",
            blockedHashes = lists.blockedHashes,
            whitelistedHashes = lists.whitelistedHashes,
            traiReportedHashes = lists.traiReportedHashes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun markNotSpam(numberHash: String, displayLabel: String) {
        viewModelScope.launch {
            val result = markNotSpamUseCase.execute(numberHash, displayLabel)
            _snackbarMessage.tryEmit(
                if (result.isSuccess) "Marked as not spam and whitelisted"
                else "Could not submit correction. Try again."
            )
        }
    }

    fun dismissScamDigest(id: Int) {
        viewModelScope.launch {
            scamDigestDao.dismiss(id)
        }
    }

    fun setBlockHidden(v: Boolean) {
        viewModelScope.launch { screeningPreferences.setBlockHiddenNumbers(v) }
    }

    fun blockNumber(numberHash: String, displayLabel: String) {
        viewModelScope.launch {
            blocklistRepo.add(numberHash, displayLabel)
            whitelistRepo.remove(numberHash) // can't be both
            _snackbarMessage.tryEmit("$displayLabel blocked")
        }
    }

    fun unblockNumber(numberHash: String) {
        viewModelScope.launch {
            blocklistRepo.remove(numberHash)
            _snackbarMessage.tryEmit("Removed from blocklist")
        }
    }

    fun whitelistNumber(numberHash: String, displayLabel: String) {
        viewModelScope.launch {
            whitelistRepo.add(numberHash, displayLabel)
            blocklistRepo.remove(numberHash) // can't be both
            _snackbarMessage.tryEmit("$displayLabel whitelisted — calls will always be allowed")
        }
    }

    fun unwhitelistNumber(numberHash: String) {
        viewModelScope.launch {
            whitelistRepo.remove(numberHash)
            _snackbarMessage.tryEmit("Removed from whitelist")
        }
    }

    fun recordTraiReport(numberHash: String, displayLabel: String) {
        viewModelScope.launch {
            traiReportDao.insert(TraiReportEntry(numberHash = numberHash, displayLabel = displayLabel))
            screeningPreferences.incrementTraiReportsCount()
        }
    }
}
