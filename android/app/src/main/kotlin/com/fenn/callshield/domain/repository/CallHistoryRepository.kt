package com.fenn.callshield.domain.repository

import com.fenn.callshield.data.local.entity.CallHistoryEntry
import com.fenn.callshield.domain.model.CallDecision
import kotlinx.coroutines.flow.Flow

interface CallHistoryRepository {
    fun observeRecent(): Flow<List<CallHistoryEntry>>
    suspend fun record(
        numberHash: String,
        displayLabel: String,
        decision: CallDecision,
        confidenceScore: Double,
        category: String?,
        decisionSource: String,
    )
    suspend fun stats(): CallStats
    fun observeStats(): Flow<CallStats>
}

data class CallStats(
    val totalScreened: Int,
    val totalBlocked: Int,
)
