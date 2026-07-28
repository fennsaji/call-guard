package com.fenn.callshield.data.repository

import com.fenn.callshield.data.local.dao.SeedDbDao
import com.fenn.callshield.domain.model.ReputationResult
import com.fenn.callshield.domain.model.ReputationSource
import com.fenn.callshield.domain.repository.ReputationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReputationRepositoryImpl @Inject constructor(
    private val seedDbDao: SeedDbDao,
) : ReputationRepository {

    override suspend fun lookup(numberHash: String): ReputationResult {
        val seedEntry = seedDbDao.lookup(numberHash)
        if (seedEntry != null) {
            return ReputationResult(
                confidenceScore = seedEntry.confidenceScore,
                category = seedEntry.category,
                source = ReputationSource.SEED_DB,
            )
        }
        return notFound()
    }

    private fun notFound() = ReputationResult(
        confidenceScore = 0.0,
        category = null,
        source = ReputationSource.NOT_FOUND,
    )
}
