package com.fenn.callshield.domain.repository

import com.fenn.callshield.domain.model.ReputationResult

interface ReputationRepository {
    /**
     * Looks up reputation for [numberHash] in the local seed DB.
     * Returns NOT_FOUND if it's not in the seed DB.
     */
    suspend fun lookup(numberHash: String): ReputationResult
}
