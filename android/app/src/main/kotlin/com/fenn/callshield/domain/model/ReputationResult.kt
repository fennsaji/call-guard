package com.fenn.callshield.domain.model

data class ReputationResult(
    val confidenceScore: Double,
    val category: String?,
    val reportCount: Int,
    val uniqueReporters: Int,
    val source: ReputationSource,
)

enum class ReputationSource { SEED_DB, REMOTE, NOT_FOUND }

// Thresholds — kept in domain layer so they're testable without Android
const val CONFIDENCE_BLOCK_THRESHOLD = 0.8   // silence threshold for Known Spam (never auto-rejects)
const val CONFIDENCE_FLAG_THRESHOLD = 0.6    // post risk notification
const val MIN_REPORTERS_TO_ACT = 3           // guard against single-reporter abuse
