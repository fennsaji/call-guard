package com.fenn.callshield.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import com.fenn.callshield.data.preferences.ScreeningPreferences
import com.fenn.callshield.data.seeddb.ScamDigestSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: ScreeningPreferences,
    private val scamDigestSeeder: ScamDigestSeeder,
) : ViewModel() {

    suspend fun markOnboardingComplete() {
        prefs.setOnboardingComplete(true)
        scamDigestSeeder.seedIfNeeded()
    }
}
