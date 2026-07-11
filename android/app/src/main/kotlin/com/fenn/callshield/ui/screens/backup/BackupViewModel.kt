package com.fenn.callshield.ui.screens.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenn.callshield.billing.BillingManager
import com.fenn.callshield.data.local.dao.BlocklistDao
import com.fenn.callshield.data.local.dao.PrefixRuleDao
import com.fenn.callshield.data.local.dao.VipContactsDao
import com.fenn.callshield.data.local.dao.WhitelistDao
import com.fenn.callshield.data.preferences.ScreeningPreferences
import com.fenn.callshield.util.BackupManager
import com.fenn.callshield.util.BackupPayload
import com.fenn.callshield.util.WrongPinException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BackupUiState(
    val status: BackupStatus = BackupStatus.Idle,
    val showPinDialog: PinDialogPurpose? = null,
    val pendingUri: Uri? = null,
    val pendingPayload: BackupPayload? = null,
    val showImportConfirm: Boolean = false,
    val showProUpgradeDialog: Boolean = false,
    val freeRestoreOnly: Boolean = false,
)

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Processing : BackupStatus
    data class Success(val message: String) : BackupStatus
    data class Error(val message: String) : BackupStatus
}

enum class PinDialogPurpose { EXPORT, IMPORT }

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val blocklistDao: BlocklistDao,
    private val whitelistDao: WhitelistDao,
    private val prefixRuleDao: PrefixRuleDao,
    private val vipContactsDao: VipContactsDao,
    private val billingManager: BillingManager,
    private val screeningPreferences: ScreeningPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val _navigateToPaywall = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToPaywall: SharedFlow<Unit> = _navigateToPaywall.asSharedFlow()

    fun onExportFileChosen(uri: Uri) {
        _state.value = _state.value.copy(pendingUri = uri, showPinDialog = PinDialogPurpose.EXPORT)
    }

    fun onImportFileChosen(uri: Uri) {
        _state.value = _state.value.copy(pendingUri = uri, showPinDialog = PinDialogPurpose.IMPORT)
    }

    fun onPinEntered(pin: String) {
        val purpose = _state.value.showPinDialog ?: return
        val uri = _state.value.pendingUri ?: return
        _state.value = _state.value.copy(showPinDialog = null)
        when (purpose) {
            PinDialogPurpose.EXPORT -> doExport(uri, pin)
            PinDialogPurpose.IMPORT -> doImport(uri, pin)
        }
    }

    fun onPinDialogDismissed() {
        _state.value = _state.value.copy(showPinDialog = null, pendingUri = null)
    }

    fun onImportConfirmed() {
        val payload = _state.value.pendingPayload ?: return
        val freeOnly = _state.value.freeRestoreOnly
        _state.value = _state.value.copy(showImportConfirm = false, status = BackupStatus.Processing)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val entities = backupManager.toRoomEntities(payload)
                val prefixRulesToRestore = if (freeOnly)
                    entities.prefixRules.take(FREE_PREFIX_RULE_LIMIT)
                else
                    entities.prefixRules
                blocklistDao.deleteAll()
                whitelistDao.deleteAll()
                prefixRuleDao.deleteAll()
                vipContactsDao.deleteAll()
                entities.blocklist.forEach { blocklistDao.insert(it) }
                entities.whitelist.forEach { whitelistDao.insert(it) }
                prefixRulesToRestore.forEach { prefixRuleDao.insert(it) }
                entities.vipContacts.forEach { vipContactsDao.insert(it) }

                payload.settings?.let { s ->
                    val settingsToRestore = if (freeOnly) s.copy(
                        // Screening — pro only
                        blockHiddenNumbers = false,
                        // Night Guard — enabled is free, but custom hours and REJECT action are pro
                        abpNightGuardStart = 22,
                        abpNightGuardEnd = 7,
                        abpNightGuardAction = if (s.abpNightGuardAction == "REJECT") "SILENCE" else s.abpNightGuardAction,
                        // Region policies — blockInternational toggle is FREE; Country Filter is pro only
                        abpCountryFilterMode = "OFF",
                        abpCountryFilterList = "",
                        // Reset pro-only presets to BALANCED to avoid inconsistent state
                        abpPreset = if (s.abpPreset in PRO_ONLY_PRESETS) "BALANCED" else s.abpPreset,
                    ) else s
                    screeningPreferences.restoreFromBackup(settingsToRestore)
                }
            }
            val total = payload.blocklist.size + payload.whitelist.size + payload.vipContacts.size +
                if (freeOnly) minOf(payload.prefixRules.size, FREE_PREFIX_RULE_LIMIT) else payload.prefixRules.size
            _state.value = _state.value.copy(
                status = BackupStatus.Success("Restored $total entries"),
                pendingPayload = null,
                freeRestoreOnly = false,
            )
        }
    }

    fun onImportCancelled() {
        _state.value = _state.value.copy(showImportConfirm = false, pendingPayload = null)
    }

    /** User tapped "View Pro Plans" in the pro-upgrade dialog → navigate to paywall. */
    fun onProUpgradeClicked() {
        _state.value = _state.value.copy(showProUpgradeDialog = false)
        _navigateToPaywall.tryEmit(Unit)
    }

    /** User tapped "Restore Free Content" — proceed but cap prefix rules at the free limit. */
    fun onRestoreFreeContentOnly() {
        _state.value = _state.value.copy(
            showProUpgradeDialog = false,
            freeRestoreOnly = true,
            showImportConfirm = true,
        )
    }

    fun clearStatus() {
        _state.value = _state.value.copy(status = BackupStatus.Idle)
    }

    private fun doExport(uri: Uri, pin: String) {
        _state.value = _state.value.copy(status = BackupStatus.Processing, pendingUri = null)
        viewModelScope.launch {
            try {
                val blocklist    = withContext(Dispatchers.IO) { blocklistDao.observeAll().first() }
                val whitelist    = withContext(Dispatchers.IO) { whitelistDao.observeAll().first() }
                val prefixRules  = withContext(Dispatchers.IO) { prefixRuleDao.observeAll().first() }
                val vipContacts  = withContext(Dispatchers.IO) { vipContactsDao.getAll() }
                val settings = withContext(Dispatchers.IO) { screeningPreferences.readAllForBackup() }
                val bytes = withContext(Dispatchers.Default) {
                    backupManager.exportBackup(
                        blocklist, whitelist, prefixRules,
                        vipContacts = vipContacts,
                        isPro = billingManager.isPro.value,
                        settings = settings,
                        pin = pin,
                    )
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("Could not open output stream")
                }
                val total = blocklist.size + whitelist.size + prefixRules.size + vipContacts.size
                _state.value = _state.value.copy(status = BackupStatus.Success("Exported $total entries"))
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    status = BackupStatus.Error("Export failed: ${e.message}")
                )
            }
        }
    }

    private fun doImport(uri: Uri, pin: String) {
        _state.value = _state.value.copy(status = BackupStatus.Processing, pendingUri = null)
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                        ?: error("Could not read file")
                }
                val payload = withContext(Dispatchers.Default) {
                    backupManager.importBackup(bytes, pin)
                }
                val backupHasPro = payload.exportedWithPro
                val deviceIsFree = !billingManager.isPro.value
                if (backupHasPro && deviceIsFree) {
                    _state.value = _state.value.copy(
                        status = BackupStatus.Idle,
                        pendingPayload = payload,
                        showProUpgradeDialog = true,
                    )
                } else {
                    _state.value = _state.value.copy(
                        status = BackupStatus.Idle,
                        pendingPayload = payload,
                        showImportConfirm = true,
                    )
                }
            } catch (_: WrongPinException) {
                _state.value = _state.value.copy(
                    status = BackupStatus.Error("Incorrect PIN. Please try again.")
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    status = BackupStatus.Error("Import failed: ${e.message}")
                )
            }
        }
    }

    companion object {
        private const val FREE_PREFIX_RULE_LIMIT = 5
        /** Presets that rely exclusively on pro-only features — reset to BALANCED on free restore. */
        private val PRO_ONLY_PRESETS = setOf("INTERNATIONAL_LOCK", "AGGRESSIVE")
    }
}
