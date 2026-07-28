package com.fenn.callshield.ui.screens.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fenn.callshield.data.local.dao.TraiReportDao
import com.fenn.callshield.data.local.entity.TraiReportEntry
import com.fenn.callshield.data.preferences.ScreeningPreferences
import com.fenn.callshield.domain.repository.BlocklistRepository
import com.fenn.callshield.domain.repository.WhitelistRepository
import com.fenn.callshield.util.HomeCountryProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportSpamState(
    val submitted: Boolean = false,
    val isIndiaDevice: Boolean = true,
)

@HiltViewModel
class ReportSpamViewModel @Inject constructor(
    private val blocklistRepo: BlocklistRepository,
    private val whitelistRepo: WhitelistRepository,
    private val traiReportDao: TraiReportDao,
    private val screeningPreferences: ScreeningPreferences,
    private val homeCountryProvider: HomeCountryProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportSpamState(isIndiaDevice = homeCountryProvider.isoCode == "IN"))
    val state: StateFlow<ReportSpamState> = _state.asStateFlow()

    fun saveTraiReport(numberHash: String, displayLabel: String) {
        viewModelScope.launch {
            traiReportDao.insert(TraiReportEntry(numberHash = numberHash, displayLabel = displayLabel))
            screeningPreferences.incrementTraiReportsCount()
        }
    }

    /** Reporting a call as spam is a local-only action — adds it to the blocklist. */
    fun submitReport(numberHash: String, displayLabel: String) {
        viewModelScope.launch {
            blocklistRepo.add(numberHash, displayLabel)
            whitelistRepo.remove(numberHash) // can't be both
            _state.value = _state.value.copy(submitted = true)
        }
    }
}
