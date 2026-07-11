package com.fenn.callshield.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fenn.callshield.domain.model.AdvancedBlockingPolicy
import com.fenn.callshield.util.BackupSettings
import com.fenn.callshield.domain.model.BlockingPreset
import com.fenn.callshield.domain.model.CountryFilterMode
import com.fenn.callshield.domain.model.UnknownCallAction
import com.fenn.callshield.ui.theme.ThemeMode
import com.fenn.callshield.util.DndOperator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "screening_prefs")

@Singleton
class ScreeningPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val BLOCK_HIDDEN = booleanPreferencesKey("block_hidden_numbers")
        val NOTIFY_ON_REJECT = booleanPreferencesKey("notify_on_reject")
        val NOTIFY_ON_SILENCE = booleanPreferencesKey("notify_on_silence")
        val NOTIFY_ON_FLAG = booleanPreferencesKey("notify_on_flag")
        val NOTIFY_ON_NIGHT_GUARD = booleanPreferencesKey("notify_on_night_guard")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val TRIAL_TRIGGERED = booleanPreferencesKey("trial_triggered")
        val TRAI_REPORTS_COUNT = intPreferencesKey("trai_reports_count")
        val DND_OPERATOR = stringPreferencesKey("dnd_operator")
        val THEME = stringPreferencesKey("theme_mode")
        // Advanced Blocking Policy
        val ABP_PRESET = stringPreferencesKey("abp_preset")
        val ABP_ALLOW_CONTACTS_ONLY = booleanPreferencesKey("abp_allow_contacts_only")
        val ABP_SILENCE_UNKNOWN = booleanPreferencesKey("abp_silence_unknown")
        val ABP_NIGHT_GUARD_ENABLED = booleanPreferencesKey("abp_night_guard_enabled")
        val ABP_NIGHT_GUARD_START = intPreferencesKey("abp_night_guard_start")
        val ABP_NIGHT_GUARD_END = intPreferencesKey("abp_night_guard_end")
        val ABP_NIGHT_GUARD_ACTION = stringPreferencesKey("abp_night_guard_action")
        val ABP_BLOCK_INTERNATIONAL = booleanPreferencesKey("abp_block_international")
        val ABP_COUNTRY_FILTER_MODE = stringPreferencesKey("abp_country_filter_mode")
        val ABP_COUNTRY_FILTER_LIST = stringPreferencesKey("abp_country_filter_list") // comma-separated ISO codes
        // VIP Contacts
        val ABP_VIP_CONTACTS_ONLY = booleanPreferencesKey("abp_vip_contacts_only")
        // Night Guard day schedule (comma-separated 0=Mon…6=Sun)
        val ABP_NIGHT_GUARD_DAYS = stringPreferencesKey("abp_night_guard_days")
        // Work Focus Window
        val ABP_WORK_FOCUS_ENABLED = booleanPreferencesKey("abp_work_focus_enabled")
        val ABP_WORK_FOCUS_START = intPreferencesKey("abp_work_focus_start")
        val ABP_WORK_FOCUS_END = intPreferencesKey("abp_work_focus_end")
        val ABP_WORK_FOCUS_ACTION = stringPreferencesKey("abp_work_focus_action")
        val ABP_WORK_FOCUS_DAYS = stringPreferencesKey("abp_work_focus_days")
        // Region
        val ABP_BLOCK_UNRECOGNIZED_ISD = booleanPreferencesKey("abp_block_unrecognized_isd")
        // Number Rules
        val ABP_BLOCKLIST_AGING_ENABLED = booleanPreferencesKey("abp_blocklist_aging_enabled")
        val ABP_BLOCKLIST_AGING_DAYS = intPreferencesKey("abp_blocklist_aging_days")
    }

    private fun String.toIntSet(): Set<Int> =
        split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    private fun Set<Int>.toPrefsString(): String = sorted().joinToString(",")

    suspend fun blockHiddenNumbers(): Boolean =
        context.dataStore.data.first()[Keys.BLOCK_HIDDEN] ?: false

    suspend fun notifyOnReject(): Boolean =
        context.dataStore.data.first()[Keys.NOTIFY_ON_REJECT] ?: true

    suspend fun notifyOnSilence(): Boolean =
        context.dataStore.data.first()[Keys.NOTIFY_ON_SILENCE] ?: true

    suspend fun notifyOnFlag(): Boolean =
        context.dataStore.data.first()[Keys.NOTIFY_ON_FLAG] ?: true

    suspend fun notifyOnNightGuard(): Boolean =
        context.dataStore.data.first()[Keys.NOTIFY_ON_NIGHT_GUARD] ?: false

    /** Reads all 6 screening flags in a single DataStore read — use this in the screening hot path. */
    suspend fun getScreeningFlags(): ScreeningFlags {
        val prefs = context.dataStore.data.first()
        return ScreeningFlags(
            blockHiddenNumbers      = prefs[Keys.BLOCK_HIDDEN] ?: false,
            notifyOnReject          = prefs[Keys.NOTIFY_ON_REJECT] ?: true,
            notifyOnSilence         = prefs[Keys.NOTIFY_ON_SILENCE] ?: true,
            notifyOnFlag            = prefs[Keys.NOTIFY_ON_FLAG] ?: true,
            notifyOnNightGuard      = prefs[Keys.NOTIFY_ON_NIGHT_GUARD] ?: false,
        )
    }

    data class ScreeningFlags(
        val blockHiddenNumbers: Boolean,
        val notifyOnReject: Boolean,
        val notifyOnSilence: Boolean,
        val notifyOnFlag: Boolean,
        val notifyOnNightGuard: Boolean,
    )

    suspend fun isOnboardingComplete(): Boolean =
        context.dataStore.data.first()[Keys.ONBOARDING_COMPLETE] ?: false

    fun observeOnboardingComplete(): Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ONBOARDING_COMPLETE] ?: false }

    /** Observes the hidden-number block toggle. */
    fun observeBlockHiddenNumbers(): Flow<Boolean> =
        context.dataStore.data.map { prefs -> prefs[Keys.BLOCK_HIDDEN] ?: false }

    /** Observes all screening + notification flags as a live Flow — reacts to backup restore. */
    fun observeAllSettingsFlags(): Flow<ScreeningFlags> =
        context.dataStore.data.map { prefs ->
            ScreeningFlags(
                blockHiddenNumbers      = prefs[Keys.BLOCK_HIDDEN] ?: false,
                notifyOnReject          = prefs[Keys.NOTIFY_ON_REJECT] ?: true,
                notifyOnSilence         = prefs[Keys.NOTIFY_ON_SILENCE] ?: true,
                notifyOnFlag            = prefs[Keys.NOTIFY_ON_FLAG] ?: true,
                notifyOnNightGuard      = prefs[Keys.NOTIFY_ON_NIGHT_GUARD] ?: false,
            )
        }

    suspend fun setBlockHiddenNumbers(value: Boolean) {
        context.dataStore.edit { it[Keys.BLOCK_HIDDEN] = value }
    }

    suspend fun setNotifyOnReject(value: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ON_REJECT] = value }
    }

    suspend fun setNotifyOnSilence(value: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ON_SILENCE] = value }
    }

    suspend fun setNotifyOnFlag(value: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ON_FLAG] = value }
    }

    suspend fun setNotifyOnNightGuard(value: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ON_NIGHT_GUARD] = value }
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = value }
    }

    suspend fun isTrialTriggered(): Boolean =
        context.dataStore.data.first()[Keys.TRIAL_TRIGGERED] ?: false

    suspend fun setTrialTriggered(value: Boolean) {
        context.dataStore.edit { it[Keys.TRIAL_TRIGGERED] = value }
    }

    suspend fun getTraiReportsCount(): Int =
        context.dataStore.data.first()[Keys.TRAI_REPORTS_COUNT] ?: 0

    suspend fun incrementTraiReportsCount() {
        context.dataStore.edit {
            it[Keys.TRAI_REPORTS_COUNT] = (it[Keys.TRAI_REPORTS_COUNT] ?: 0) + 1
        }
    }

    fun observeTheme(): Flow<ThemeMode> =
        context.dataStore.data.map {
            it[Keys.THEME]?.let { name -> runCatching { ThemeMode.valueOf(name) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME] = mode.name }
    }

    fun observeDndOperator(): Flow<DndOperator?> =
        context.dataStore.data.map { it[Keys.DND_OPERATOR]?.let { name -> DndOperator.fromName(name) } }

    suspend fun setDndOperator(operator: DndOperator) {
        context.dataStore.edit { it[Keys.DND_OPERATOR] = operator.name }
    }

    fun observeAdvancedBlockingPolicy(): Flow<AdvancedBlockingPolicy> =
        context.dataStore.data.map { prefs ->
            val preset = prefs[Keys.ABP_PRESET]
                ?.let { runCatching { BlockingPreset.valueOf(it) }.getOrNull() }
                ?: BlockingPreset.BALANCED
            AdvancedBlockingPolicy(
                preset = preset,
                allowContactsOnly = prefs[Keys.ABP_ALLOW_CONTACTS_ONLY] ?: false,
                silenceUnknownNumbers = prefs[Keys.ABP_SILENCE_UNKNOWN] ?: false,
                nightGuardEnabled = prefs[Keys.ABP_NIGHT_GUARD_ENABLED] ?: false,
                nightGuardStartHour = prefs[Keys.ABP_NIGHT_GUARD_START] ?: 22,
                nightGuardEndHour = prefs[Keys.ABP_NIGHT_GUARD_END] ?: 7,
                nightGuardAction = prefs[Keys.ABP_NIGHT_GUARD_ACTION]
                    ?.let { runCatching { UnknownCallAction.valueOf(it) }.getOrNull() }
                    ?: UnknownCallAction.SILENCE,
                blockInternational = prefs[Keys.ABP_BLOCK_INTERNATIONAL] ?: false,
                countryFilterMode = prefs[Keys.ABP_COUNTRY_FILTER_MODE]
                    ?.let { runCatching { CountryFilterMode.valueOf(it) }.getOrNull() }
                    ?: CountryFilterMode.OFF,
                countryFilterList = prefs[Keys.ABP_COUNTRY_FILTER_LIST]
                    ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
                vipContactsOnlyEnabled = prefs[Keys.ABP_VIP_CONTACTS_ONLY] ?: false,
                nightGuardDays = prefs[Keys.ABP_NIGHT_GUARD_DAYS]
                    ?.takeIf { it.isNotBlank() }?.toIntSet()
                    ?: setOf(0, 1, 2, 3, 4, 5, 6),
                workFocusEnabled = prefs[Keys.ABP_WORK_FOCUS_ENABLED] ?: false,
                workFocusStartHour = prefs[Keys.ABP_WORK_FOCUS_START] ?: 9,
                workFocusEndHour = prefs[Keys.ABP_WORK_FOCUS_END] ?: 18,
                workFocusAction = prefs[Keys.ABP_WORK_FOCUS_ACTION]
                    ?.let { runCatching { UnknownCallAction.valueOf(it) }.getOrNull() }
                    ?: UnknownCallAction.SILENCE,
                workFocusDays = prefs[Keys.ABP_WORK_FOCUS_DAYS]
                    ?.takeIf { it.isNotBlank() }?.toIntSet()
                    ?: setOf(0, 1, 2, 3, 4),
                blockUnrecognizedIsd = prefs[Keys.ABP_BLOCK_UNRECOGNIZED_ISD] ?: false,
                blocklistAgingEnabled = prefs[Keys.ABP_BLOCKLIST_AGING_ENABLED] ?: false,
                blocklistAgingDays = prefs[Keys.ABP_BLOCKLIST_AGING_DAYS] ?: 30,
            )
        }

    suspend fun getAdvancedBlockingPolicy(): AdvancedBlockingPolicy =
        observeAdvancedBlockingPolicy().first()

    suspend fun setAdvancedBlockingPolicy(policy: AdvancedBlockingPolicy) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ABP_PRESET] = policy.preset.name
            prefs[Keys.ABP_ALLOW_CONTACTS_ONLY] = policy.allowContactsOnly
            prefs[Keys.ABP_SILENCE_UNKNOWN] = policy.silenceUnknownNumbers
            prefs[Keys.ABP_NIGHT_GUARD_ENABLED] = policy.nightGuardEnabled
            prefs[Keys.ABP_NIGHT_GUARD_START] = policy.nightGuardStartHour
            prefs[Keys.ABP_NIGHT_GUARD_END] = policy.nightGuardEndHour
            prefs[Keys.ABP_NIGHT_GUARD_ACTION] = policy.nightGuardAction.name
            prefs[Keys.ABP_BLOCK_INTERNATIONAL] = policy.blockInternational
            prefs[Keys.ABP_COUNTRY_FILTER_MODE] = policy.countryFilterMode.name
            prefs[Keys.ABP_COUNTRY_FILTER_LIST] = policy.countryFilterList.joinToString(",")
            prefs[Keys.ABP_VIP_CONTACTS_ONLY] = policy.vipContactsOnlyEnabled
            prefs[Keys.ABP_NIGHT_GUARD_DAYS] = policy.nightGuardDays.toPrefsString()
            prefs[Keys.ABP_WORK_FOCUS_ENABLED] = policy.workFocusEnabled
            prefs[Keys.ABP_WORK_FOCUS_START] = policy.workFocusStartHour
            prefs[Keys.ABP_WORK_FOCUS_END] = policy.workFocusEndHour
            prefs[Keys.ABP_WORK_FOCUS_ACTION] = policy.workFocusAction.name
            prefs[Keys.ABP_WORK_FOCUS_DAYS] = policy.workFocusDays.toPrefsString()
            prefs[Keys.ABP_BLOCK_UNRECOGNIZED_ISD] = policy.blockUnrecognizedIsd
            prefs[Keys.ABP_BLOCKLIST_AGING_ENABLED] = policy.blocklistAgingEnabled
            prefs[Keys.ABP_BLOCKLIST_AGING_DAYS] = policy.blocklistAgingDays
        }
    }

    /** Reads all user-configurable settings in a single DataStore pass for backup. */
    suspend fun readAllForBackup(): BackupSettings {
        val prefs = context.dataStore.data.first()
        return BackupSettings(
            blockHiddenNumbers      = prefs[Keys.BLOCK_HIDDEN] ?: false,
            notifyOnReject          = prefs[Keys.NOTIFY_ON_REJECT] ?: true,
            notifyOnSilence         = prefs[Keys.NOTIFY_ON_SILENCE] ?: true,
            notifyOnFlag            = prefs[Keys.NOTIFY_ON_FLAG] ?: true,
            notifyOnNightGuard      = prefs[Keys.NOTIFY_ON_NIGHT_GUARD] ?: false,
            themeMode               = prefs[Keys.THEME] ?: "SYSTEM",
            abpPreset               = prefs[Keys.ABP_PRESET] ?: "BALANCED",
            abpAllowContactsOnly    = prefs[Keys.ABP_ALLOW_CONTACTS_ONLY] ?: false,
            abpSilenceUnknown       = prefs[Keys.ABP_SILENCE_UNKNOWN] ?: false,
            abpNightGuardEnabled    = prefs[Keys.ABP_NIGHT_GUARD_ENABLED] ?: false,
            abpNightGuardStart      = prefs[Keys.ABP_NIGHT_GUARD_START] ?: 22,
            abpNightGuardEnd        = prefs[Keys.ABP_NIGHT_GUARD_END] ?: 7,
            abpNightGuardAction     = prefs[Keys.ABP_NIGHT_GUARD_ACTION] ?: "SILENCE",
            abpBlockInternational   = prefs[Keys.ABP_BLOCK_INTERNATIONAL] ?: false,
            abpCountryFilterMode    = prefs[Keys.ABP_COUNTRY_FILTER_MODE] ?: "OFF",
            abpCountryFilterList    = prefs[Keys.ABP_COUNTRY_FILTER_LIST] ?: "",
            abpVipContactsOnly      = prefs[Keys.ABP_VIP_CONTACTS_ONLY] ?: false,
            abpNightGuardDays       = prefs[Keys.ABP_NIGHT_GUARD_DAYS] ?: "0,1,2,3,4,5,6",
            abpWorkFocusEnabled     = prefs[Keys.ABP_WORK_FOCUS_ENABLED] ?: false,
            abpWorkFocusStart       = prefs[Keys.ABP_WORK_FOCUS_START] ?: 9,
            abpWorkFocusEnd         = prefs[Keys.ABP_WORK_FOCUS_END] ?: 18,
            abpWorkFocusAction      = prefs[Keys.ABP_WORK_FOCUS_ACTION] ?: "SILENCE",
            abpWorkFocusDays        = prefs[Keys.ABP_WORK_FOCUS_DAYS] ?: "0,1,2,3,4",
            abpBlockUnrecognizedIsd = prefs[Keys.ABP_BLOCK_UNRECOGNIZED_ISD] ?: false,
            abpBlocklistAgingEnabled = prefs[Keys.ABP_BLOCKLIST_AGING_ENABLED] ?: false,
            abpBlocklistAgingDays   = prefs[Keys.ABP_BLOCKLIST_AGING_DAYS] ?: 30,
        )
    }

    /** Restores all backed-up settings in a single DataStore write. */
    suspend fun restoreFromBackup(s: BackupSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BLOCK_HIDDEN]          = s.blockHiddenNumbers
            prefs[Keys.NOTIFY_ON_REJECT]      = s.notifyOnReject
            prefs[Keys.NOTIFY_ON_SILENCE]     = s.notifyOnSilence
            prefs[Keys.NOTIFY_ON_FLAG]        = s.notifyOnFlag
            prefs[Keys.NOTIFY_ON_NIGHT_GUARD] = s.notifyOnNightGuard
            prefs[Keys.THEME]                 = s.themeMode
            prefs[Keys.ABP_PRESET]                 = s.abpPreset
            prefs[Keys.ABP_ALLOW_CONTACTS_ONLY]    = s.abpAllowContactsOnly
            prefs[Keys.ABP_SILENCE_UNKNOWN]        = s.abpSilenceUnknown
            prefs[Keys.ABP_NIGHT_GUARD_ENABLED]    = s.abpNightGuardEnabled
            prefs[Keys.ABP_NIGHT_GUARD_START]      = s.abpNightGuardStart
            prefs[Keys.ABP_NIGHT_GUARD_END]        = s.abpNightGuardEnd
            prefs[Keys.ABP_NIGHT_GUARD_ACTION]     = s.abpNightGuardAction
            prefs[Keys.ABP_BLOCK_INTERNATIONAL]    = s.abpBlockInternational
            prefs[Keys.ABP_COUNTRY_FILTER_MODE]    = s.abpCountryFilterMode
            prefs[Keys.ABP_COUNTRY_FILTER_LIST]    = s.abpCountryFilterList
            prefs[Keys.ABP_VIP_CONTACTS_ONLY]       = s.abpVipContactsOnly
            prefs[Keys.ABP_NIGHT_GUARD_DAYS]        = s.abpNightGuardDays
            prefs[Keys.ABP_WORK_FOCUS_ENABLED]      = s.abpWorkFocusEnabled
            prefs[Keys.ABP_WORK_FOCUS_START]        = s.abpWorkFocusStart
            prefs[Keys.ABP_WORK_FOCUS_END]          = s.abpWorkFocusEnd
            prefs[Keys.ABP_WORK_FOCUS_ACTION]       = s.abpWorkFocusAction
            prefs[Keys.ABP_WORK_FOCUS_DAYS]         = s.abpWorkFocusDays
            prefs[Keys.ABP_BLOCK_UNRECOGNIZED_ISD]  = s.abpBlockUnrecognizedIsd
            prefs[Keys.ABP_BLOCKLIST_AGING_ENABLED] = s.abpBlocklistAgingEnabled
            prefs[Keys.ABP_BLOCKLIST_AGING_DAYS]    = s.abpBlocklistAgingDays
        }
    }
}
