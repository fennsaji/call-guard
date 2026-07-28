package com.fenn.callshield.domain.usecase

import com.fenn.callshield.domain.repository.WhitelistRepository
import javax.inject.Inject

/**
 * "Not Spam" Correction Flow: re-add the number to the personal whitelist,
 * overriding all future detections for it.
 */
class MarkNotSpamUseCase @Inject constructor(
    private val whitelistRepo: WhitelistRepository,
) {
    /**
     * @param numberHash  HMAC-SHA256 of the phone number
     * @param displayLabel Display-safe label (e.g. "****3210")
     */
    suspend fun execute(numberHash: String, displayLabel: String): Result<Unit> {
        whitelistRepo.add(numberHash, displayLabel)
        return Result.success(Unit)
    }
}
