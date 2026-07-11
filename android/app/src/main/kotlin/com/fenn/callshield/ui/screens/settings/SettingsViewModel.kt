package com.fenn.callshield.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenn.callshield.billing.BillingManager
import com.fenn.callshield.data.preferences.ScreeningPreferences
import com.fenn.callshield.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val blockHidden: Boolean = false,
    val notifyOnReject: Boolean = true,
    val notifyOnSilence: Boolean = true,
    val notifyOnFlag: Boolean = true,
    val notifyOnNightGuard: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: ScreeningPreferences,
    private val billingManager: BillingManager,
) : ViewModel() {

    val isPro: StateFlow<Boolean> = billingManager.isPro

    val themeMode: StateFlow<ThemeMode> = prefs.observeTheme()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val state: StateFlow<SettingsState> = prefs.observeAllSettingsFlags()
        .map { flags ->
            SettingsState(
                blockHidden    = flags.blockHiddenNumbers,
                notifyOnReject = flags.notifyOnReject,
                notifyOnSilence = flags.notifyOnSilence,
                notifyOnFlag   = flags.notifyOnFlag,
                notifyOnNightGuard = flags.notifyOnNightGuard,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())

    suspend fun setBlockHidden(v: Boolean) { prefs.setBlockHiddenNumbers(v) }
    suspend fun setNotifyOnReject(v: Boolean) { prefs.setNotifyOnReject(v) }
    suspend fun setNotifyOnSilence(v: Boolean) { prefs.setNotifyOnSilence(v) }
    suspend fun setNotifyOnFlag(v: Boolean) { prefs.setNotifyOnFlag(v) }
    suspend fun setNotifyOnNightGuard(v: Boolean) { prefs.setNotifyOnNightGuard(v) }
    fun setTheme(mode: ThemeMode) { viewModelScope.launch { prefs.setTheme(mode) } }
}
