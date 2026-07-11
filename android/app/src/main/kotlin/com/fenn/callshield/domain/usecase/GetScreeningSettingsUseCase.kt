package com.fenn.callshield.domain.usecase

import com.fenn.callshield.data.preferences.ScreeningPreferences
import javax.inject.Inject

data class ScreeningSettings(
    val blockHiddenNumbers: Boolean,
    val notifyOnReject: Boolean,
    val notifyOnSilence: Boolean,
    val notifyOnFlag: Boolean,
    val notifyOnNightGuard: Boolean,
)

class GetScreeningSettingsUseCase @Inject constructor(
    private val prefs: ScreeningPreferences,
) {
    suspend fun get(): ScreeningSettings {
        val flags = prefs.getScreeningFlags()
        return ScreeningSettings(
            blockHiddenNumbers      = flags.blockHiddenNumbers,
            notifyOnReject          = flags.notifyOnReject,
            notifyOnSilence         = flags.notifyOnSilence,
            notifyOnFlag            = flags.notifyOnFlag,
            notifyOnNightGuard      = flags.notifyOnNightGuard,
        )
    }
}
