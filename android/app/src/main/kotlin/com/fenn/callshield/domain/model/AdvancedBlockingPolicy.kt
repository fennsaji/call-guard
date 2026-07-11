package com.fenn.callshield.domain.model

enum class BlockingPreset { BALANCED, AGGRESSIVE, CONTACTS_ONLY, NIGHT_GUARD, INTERNATIONAL_LOCK, CUSTOM }

enum class UnknownCallAction { ALLOW, SILENCE, REJECT }

/** Whether the country filter list acts as a whitelist or blacklist. */
enum class CountryFilterMode { OFF, ALLOW_ONLY, BLOCK_LISTED }

data class AdvancedBlockingPolicy(
    val preset: BlockingPreset = BlockingPreset.BALANCED,
    // Contact policies
    val allowContactsOnly: Boolean = false,
    val silenceUnknownNumbers: Boolean = false,
    // VIP Contacts Only (Pro) — only manually-added numbers ring through
    val vipContactsOnlyEnabled: Boolean = false,
    // Night Guard (Free: fixed 10PM–7AM silence only)
    val nightGuardEnabled: Boolean = false,
    val nightGuardStartHour: Int = 22,
    val nightGuardEndHour: Int = 7,
    val nightGuardAction: UnknownCallAction = UnknownCallAction.SILENCE, // Pro can set REJECT
    // Night Guard day schedule (Pro) — 0=Mon … 6=Sun; default = all days
    val nightGuardDays: Set<Int> = setOf(0, 1, 2, 3, 4, 5, 6),
    // Work Focus Window (Pro)
    val workFocusEnabled: Boolean = false,
    val workFocusStartHour: Int = 9,
    val workFocusEndHour: Int = 18,
    val workFocusAction: UnknownCallAction = UnknownCallAction.SILENCE,
    val workFocusDays: Set<Int> = setOf(0, 1, 2, 3, 4), // Mon–Fri
    // Region policy
    val blockInternational: Boolean = false,
    // Country filter (Pro) — ISO 3166-1 alpha-2 codes
    val countryFilterMode: CountryFilterMode = CountryFilterMode.OFF,
    val countryFilterList: Set<String> = emptySet(),
    // Block unrecognized ISD codes (Pro)
    val blockUnrecognizedIsd: Boolean = false,
    // Escalation
    val autoEscalateEnabled: Boolean = false,
    val autoEscalateThreshold: Int = 3,
    // Blocklist aging (Pro) — auto-remove entries that haven't called in N days
    val blocklistAgingEnabled: Boolean = false,
    val blocklistAgingDays: Int = 30,
) {
    /** True if any non-default option is active beyond just the preset field. */
    fun isCustomized(): Boolean =
        allowContactsOnly || silenceUnknownNumbers || vipContactsOnlyEnabled ||
                nightGuardEnabled || workFocusEnabled ||
                blockInternational || countryFilterMode != CountryFilterMode.OFF ||
                blockUnrecognizedIsd || autoEscalateEnabled || blocklistAgingEnabled
}

fun BlockingPreset.toDefaultPolicy(): AdvancedBlockingPolicy = when (this) {
    BlockingPreset.BALANCED -> AdvancedBlockingPolicy(preset = this)
    BlockingPreset.AGGRESSIVE -> AdvancedBlockingPolicy(
        preset = this,
        silenceUnknownNumbers = true,
        autoEscalateEnabled = true,
        autoEscalateThreshold = 2,
    )
    BlockingPreset.CONTACTS_ONLY -> AdvancedBlockingPolicy(
        preset = this,
        allowContactsOnly = true,
    )
    BlockingPreset.NIGHT_GUARD -> AdvancedBlockingPolicy(
        preset = this,
        nightGuardEnabled = true,
    )
    BlockingPreset.INTERNATIONAL_LOCK -> AdvancedBlockingPolicy(
        preset = this,
        blockInternational = true,
    )
    BlockingPreset.CUSTOM -> AdvancedBlockingPolicy(preset = this)
}
