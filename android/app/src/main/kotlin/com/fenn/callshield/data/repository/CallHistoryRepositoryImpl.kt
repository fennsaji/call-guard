package com.fenn.callshield.data.repository

import com.fenn.callshield.data.local.dao.CallHistoryDao
import com.fenn.callshield.data.local.entity.CallHistoryEntry
import com.fenn.callshield.domain.model.CallDecision
import com.fenn.callshield.domain.repository.CallHistoryRepository
import com.fenn.callshield.domain.repository.CallStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class CallHistoryRepositoryImpl @Inject constructor(
    private val dao: CallHistoryDao,
) : CallHistoryRepository {

    override fun observeRecent(): Flow<List<CallHistoryEntry>> = dao.observeRecent()

    override suspend fun record(
        numberHash: String,
        displayLabel: String,
        decision: CallDecision,
        confidenceScore: Double,
        category: String?,
        decisionSource: String,
    ) {
        val outcome = when (decision) {
            is CallDecision.Reject -> "rejected"
            is CallDecision.Silence -> "silenced"
            is CallDecision.Allow -> "allowed"
            is CallDecision.Flag -> "flagged"
        }
        dao.insert(
            CallHistoryEntry(
                numberHash = numberHash,
                displayLabel = displayLabel,
                outcome = outcome,
                confidenceScore = confidenceScore,
                category = category,
                decisionSource = decisionSource,
            )
        )
        dao.pruneOldEntries()
    }

    override suspend fun stats(): CallStats = CallStats(
        totalScreened = dao.totalCount(),
        totalBlocked = dao.blockedCount(),
    )

    override fun observeStats(): Flow<CallStats> =
        dao.observeTotalCount().combine(dao.observeBlockedCount()) { total, blocked ->
            CallStats(totalScreened = total, totalBlocked = blocked)
        }
}
