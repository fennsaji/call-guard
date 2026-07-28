package com.fenn.callshield.ui.screens.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenn.callshield.data.local.dao.BlocklistDao
import com.fenn.callshield.data.local.dao.CallHistoryDao
import com.fenn.callshield.data.local.dao.PrefixRuleDao
import com.fenn.callshield.data.local.dao.SeedDbDao
import com.fenn.callshield.data.local.dao.WhitelistDao
import com.fenn.callshield.data.preferences.ScreeningPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PrivacyDashboardState(
    val traiReportsCount: Int = 0,
)

@HiltViewModel
class PrivacyDashboardViewModel @Inject constructor(
    private val seedDbDao: SeedDbDao,
    private val blocklistDao: BlocklistDao,
    private val whitelistDao: WhitelistDao,
    private val prefixRuleDao: PrefixRuleDao,
    private val callHistoryDao: CallHistoryDao,
    private val screeningPreferences: ScreeningPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(PrivacyDashboardState())
    val state: StateFlow<PrivacyDashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                traiReportsCount = screeningPreferences.getTraiReportsCount(),
            )
        }
    }

    /** PRD §3.9: one-tap option to delete all local data. */
    fun deleteAllData() {
        viewModelScope.launch {
            // Clear all user data from Room — preferences intentionally preserved
            // (user settings should survive a data reset)
            callHistoryDao.pruneOldEntries()
            // Wipe via a direct clear approach — Room doesn't have a bulk delete, use DAO queries
            deleteAllTables()
            _state.value = PrivacyDashboardState()
        }
    }

    private suspend fun deleteAllTables() {
        blocklistDao.deleteAll()
        whitelistDao.deleteAll()
        prefixRuleDao.deleteAll()
        callHistoryDao.deleteAll()
        seedDbDao.clearAll()
    }
}
