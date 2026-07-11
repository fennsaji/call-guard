package com.fenn.callshield.domain.model

/**
 * The outcome produced by ScreeningOrchestrator for a given incoming call.
 *
 * Priority order (highest first):
 *   1. WHITELIST  — always allow
 *   2. BLOCKLIST  — reject (disconnect)
 *   3. PREFIX     — reject or silence per rule
 *   4. HIDDEN     — silence or reject based on user setting
 *   5. SEED_DB    — silence (Known Spam)
 *   6. REMOTE     — silence if Likely Spam
 *   7. ALLOW      — no signal found; pass through
 *
 * Silence vs Reject:
 *   Silence = setDisallowCall(true) + setRejectCall(false) — ring suppressed, goes to missed call
 *   Reject  = setDisallowCall(true) + setRejectCall(true)  — call disconnected immediately
 */
sealed class CallDecision {
    data object Allow : CallDecision()

    /** Ring suppressed. Call goes to missed calls list. Free-tier default for detected spam. */
    data class Silence(
        val confidenceScore: Double,
        val category: String?,
        val source: DecisionSource,
    ) : CallDecision()

    /** Call disconnected. Used for blocklist entries and policy-based rejects (never for confidence score alone). */
    data class Reject(
        val source: DecisionSource,
    ) : CallDecision()

    /**
     * Call rings normally but a risk notification is posted.
     * Used when confidence is below the silence threshold.
     */
    data class Flag(
        val confidenceScore: Double,
        val category: String?,
        val source: DecisionSource,
    ) : CallDecision()
}

enum class DecisionSource(val displayLabel: String) {
    WHITELIST("In your whitelist"),
    BLOCKLIST("In your blocklist"),
    PREFIX("Matched prefix rule"),
    HIDDEN("Hidden / private number"),
    ADVANCED_BLOCKING("Blocked by protection policy"),
    SEED_DB("Found in local spam database"),
    REMOTE("Reported by community"),
    BEHAVIORAL("Suspicious call pattern"),
    DEFAULT("Unknown"),
}
