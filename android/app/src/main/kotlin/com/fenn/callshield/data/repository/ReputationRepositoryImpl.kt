package com.fenn.callshield.data.repository

import com.fenn.callshield.data.local.dao.SeedDbDao
import com.fenn.callshield.domain.model.ReputationResult
import com.fenn.callshield.domain.model.ReputationSource
import com.fenn.callshield.domain.repository.ReputationRepository
import com.fenn.callshield.network.ApiClient
import com.fenn.callshield.util.DeviceTokenManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReputationRepositoryImpl @Inject constructor(
    private val apiClient: ApiClient,
    private val seedDbDao: SeedDbDao,
    private val deviceTokenManager: DeviceTokenManager,
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

    override suspend fun submitReport(numberHash: String, category: String): Result<Unit> {
        return try {
            val ok = apiClient.postReport(
                numberHash = numberHash,
                deviceTokenHash = deviceTokenManager.deviceTokenHash,
                category = category,
            )
            if (ok) Result.success(Unit)
            else Result.failure(Exception("Server rejected the report"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun submitCorrection(numberHash: String): Result<Unit> {
        return try {
            val ok = apiClient.postCorrect(
                numberHash = numberHash,
                deviceTokenHash = deviceTokenManager.deviceTokenHash,
            )
            if (ok) Result.success(Unit)
            else Result.failure(Exception("Server rejected the correction"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun notFound() = ReputationResult(
        confidenceScore = 0.0,
        category = null,
        source = ReputationSource.NOT_FOUND,
    )
}
