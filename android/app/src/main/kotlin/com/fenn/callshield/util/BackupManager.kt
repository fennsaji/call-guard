package com.fenn.callshield.util

import com.fenn.callshield.data.local.entity.BlocklistEntry
import com.fenn.callshield.data.local.entity.PrefixRule
import com.fenn.callshield.data.local.entity.VipContactEntry
import com.fenn.callshield.data.local.entity.WhitelistEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles encrypted backup and restore of user data (Phase 3 — local file approach).
 *
 * File format:
 *   [4-byte magic "CSBK"][1-byte version][16-byte PBKDF2 salt][12-byte GCM IV][AES-256-GCM ciphertext]
 *
 * Key derivation: PBKDF2WithHmacSHA256(pin, salt, 100_000 iterations, 256-bit key)
 * Encryption:     AES-256-GCM, 128-bit authentication tag
 *
 * Only hashed number values are stored in the backup — no raw phone numbers.
 */
@Singleton
class BackupManager @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Export ────────────────────────────────────────────────────────────────

    fun exportBackup(
        blocklist: List<BlocklistEntry>,
        whitelist: List<WhitelistEntry>,
        prefixRules: List<PrefixRule>,
        vipContacts: List<VipContactEntry> = emptyList(),
        isPro: Boolean = false,
        settings: BackupSettings? = null,
        pin: String,
    ): ByteArray {
        val payload = BackupPayload(
            exportedAt = System.currentTimeMillis(),
            exportedWithPro = isPro,
            blocklist = blocklist.map { BackupBlocklistEntry(it.numberHash, it.displayLabel, it.addedAt) },
            whitelist = whitelist.map { BackupWhitelistEntry(it.numberHash, it.displayLabel, it.addedAt) },
            prefixRules = prefixRules.map { BackupPrefixRule(it.pattern, it.matchType, it.action, it.label, it.addedAt) },
            vipContacts = vipContacts.map { BackupVipContact(it.numberHash, it.displayLabel, it.addedAt) },
            settings = settings,
        )
        val plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)

        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(pin, salt)

        val cipher = Cipher.getInstance(CIPHER_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)

        return MAGIC + byteArrayOf(FORMAT_VERSION) + salt + iv + ciphertext
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Decrypts and deserializes a backup file.
     * @throws WrongPinException if the PIN is incorrect (AEADBadTagException from GCM).
     * @throws IllegalArgumentException if the file is not a valid CallShield backup.
     */
    fun importBackup(data: ByteArray, pin: String): BackupPayload {
        val minSize = MAGIC.size + 1 + SALT_LEN + IV_LEN + 1
        require(data.size > minSize) { "File too small to be a valid backup" }
        require(data.sliceArray(0..3).contentEquals(MAGIC)) { "Not a CallShield backup file" }
        // index 4 = version byte (reserved for future format migrations)

        val salt = data.sliceArray(5 until 5 + SALT_LEN)
        val iv = data.sliceArray(5 + SALT_LEN until 5 + SALT_LEN + IV_LEN)
        val ciphertext = data.sliceArray(5 + SALT_LEN + IV_LEN until data.size)

        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance(CIPHER_ALGO)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))

        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (_: javax.crypto.AEADBadTagException) {
            throw WrongPinException()
        }

        return json.decodeFromString<BackupPayload>(String(plaintext, Charsets.UTF_8))
    }

    // ── Conversion helpers ────────────────────────────────────────────────────

    fun toRoomEntities(payload: BackupPayload) = BackupRoomEntities(
        blocklist = payload.blocklist.map { BlocklistEntry(it.numberHash, it.displayLabel, it.addedAt) },
        whitelist = payload.whitelist.map { WhitelistEntry(it.numberHash, it.displayLabel, it.addedAt) },
        prefixRules = payload.prefixRules.map { PrefixRule(pattern = it.pattern, matchType = it.matchType, action = it.action, label = it.label, addedAt = it.addedAt) },
        vipContacts = payload.vipContacts.map { VipContactEntry(it.numberHash, it.displayLabel, it.addedAt) },
    )

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LEN_BITS)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGO)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    private companion object {
        val MAGIC = byteArrayOf(0x43, 0x53, 0x42, 0x4B) // "CSBK"
        const val FORMAT_VERSION: Byte = 1
        const val SALT_LEN = 16
        const val IV_LEN = 12
        const val KEY_LEN_BITS = 256
        const val PBKDF2_ITERATIONS = 100_000
        const val GCM_TAG_BITS = 128
        const val CIPHER_ALGO = "AES/GCM/NoPadding"
        const val KEY_DERIVATION_ALGO = "PBKDF2WithHmacSHA256"
    }
}

// ── Backup file payload ───────────────────────────────────────────────────────

@Serializable
data class BackupPayload(
    val version: Int = 2,
    val exportedAt: Long,
    val exportedWithPro: Boolean = false,
    val blocklist: List<BackupBlocklistEntry>,
    val whitelist: List<BackupWhitelistEntry>,
    val prefixRules: List<BackupPrefixRule>,
    val vipContacts: List<BackupVipContact> = emptyList(),
    val settings: BackupSettings? = null,
)

@Serializable
data class BackupSettings(
    // Screening
    val blockHiddenNumbers: Boolean = false,
    // Notifications
    val notifyOnReject: Boolean = true,
    val notifyOnSilence: Boolean = true,
    val notifyOnFlag: Boolean = true,
    val notifyOnNightGuard: Boolean = false,
    // Theme
    val themeMode: String = "SYSTEM",
    // Advanced Blocking Policy
    val abpPreset: String = "BALANCED",
    val abpAllowContactsOnly: Boolean = false,
    val abpSilenceUnknown: Boolean = false,
    val abpNightGuardEnabled: Boolean = false,
    val abpNightGuardStart: Int = 22,
    val abpNightGuardEnd: Int = 7,
    val abpNightGuardAction: String = "SILENCE",
    val abpBlockInternational: Boolean = false,
    val abpCountryFilterMode: String = "OFF",
    val abpCountryFilterList: String = "",
    // Phase 5 extended fields
    val abpVipContactsOnly: Boolean = false,
    val abpNightGuardDays: String = "0,1,2,3,4,5,6",
    val abpWorkFocusEnabled: Boolean = false,
    val abpWorkFocusStart: Int = 9,
    val abpWorkFocusEnd: Int = 18,
    val abpWorkFocusAction: String = "SILENCE",
    val abpWorkFocusDays: String = "0,1,2,3,4",
    val abpBlockUnrecognizedIsd: Boolean = false,
    val abpBlocklistAgingEnabled: Boolean = false,
    val abpBlocklistAgingDays: Int = 30,
)

@Serializable
data class BackupBlocklistEntry(
    val numberHash: String,
    val displayLabel: String,
    val addedAt: Long,
)

@Serializable
data class BackupWhitelistEntry(
    val numberHash: String,
    val displayLabel: String,
    val addedAt: Long,
)

@Serializable
data class BackupPrefixRule(
    val pattern: String,
    val matchType: String = "prefix",
    val action: String,
    val label: String,
    val addedAt: Long,
)

@Serializable
data class BackupVipContact(
    val numberHash: String,
    val displayLabel: String,
    val addedAt: Long,
)

data class BackupRoomEntities(
    val blocklist: List<BlocklistEntry>,
    val whitelist: List<WhitelistEntry>,
    val prefixRules: List<PrefixRule>,
    val vipContacts: List<VipContactEntry>,
)

/** Thrown when AES-GCM authentication tag verification fails — wrong PIN. */
class WrongPinException : Exception("Incorrect PIN — backup cannot be decrypted")
