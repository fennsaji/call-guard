package com.fenn.callshield.domain.model

/**
 * Manual Report Flow categories — drives the category picker and the
 * auto-filled TRAI SMS description. Local-only, not sent anywhere.
 */
enum class SpamCategory(val displayName: String) {
    TELEMARKETING("Telemarketing / Promotional"),
    LOAN_SCAM("Loan or Financial Scam"),
    INVESTMENT_SCAM("Investment Scam"),
    IMPERSONATION("Impersonation (Bank / Govt)"),
    PHISHING("Phishing"),
    JOB_SCAM("Job or Work From Home Scam"),
    OTHER("Other"),
}
