package com.fenn.callshield.domain.model

data class ReputationResult(
    val confidenceScore: Double,
    val category: String?,
    val source: ReputationSource,
)

enum class ReputationSource { SEED_DB, NOT_FOUND }

// Threshold — kept in domain layer so it's testable without Android. Used only for
// UI display (confidence bar color), since seed DB hits always Silence regardless of score.
const val CONFIDENCE_BLOCK_THRESHOLD = 0.8
