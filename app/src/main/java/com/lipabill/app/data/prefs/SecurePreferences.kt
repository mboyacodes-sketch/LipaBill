package com.lipabill.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lipabill.app.BuildConfig

/**
 * App settings and auth flags stored in EncryptedSharedPreferences.
 */
class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var authRequired: Boolean
        get() = prefs.getBoolean(KEY_AUTH_REQUIRED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTH_REQUIRED, value).apply()

    /** Idle timeout in milliseconds before re-auth on resume. Default 2 minutes. */
    var authTimeoutMillis: Long
        get() = prefs.getLong(KEY_AUTH_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
        set(value) = prefs.edit().putLong(KEY_AUTH_TIMEOUT_MS, value).apply()

    var lastBackgroundedAt: Long
        get() = prefs.getLong(KEY_LAST_BACKGROUNDED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKGROUNDED, value).apply()

    var smsBackfillDone: Boolean
        get() = prefs.getBoolean(KEY_BACKFILL_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKFILL_DONE, value).apply()

    /** When false, repeat uses copy-details fallback (no Accessibility / USSD dial). */
    var repeatFeatureEnabled: Boolean
        get() = prefs.getBoolean(KEY_REPEAT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_REPEAT_ENABLED, value).apply()

    var accessibilityOnboardingSeen: Boolean
        get() = prefs.getBoolean(KEY_A11Y_ONBOARDING_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_A11Y_ONBOARDING_SEEN, value).apply()

    /**
     * True after the user has successfully enabled LipaBill Accessibility.
     * Survives APK updates (`adb install -r` / Play update). Cleared when the
     * user intentionally turns the service off from Profile. When the OS toggle
     * is off after an update, release builds use this flag for the re-enable coach.
     */
    var accessibilityPreferredOn: Boolean
        get() = prefs.getBoolean(KEY_A11Y_PREFERRED_ON, false)
        set(value) = prefs.edit().putBoolean(KEY_A11Y_PREFERRED_ON, value).apply()

    /**
     * Preferred SIM subscriptionId for dialing *334#.
     * -1 means not set — auto-pick Safaricom if present, else first SIM.
     */
    var preferredSimSubscriptionId: Int
        get() = prefs.getInt(KEY_PREFERRED_SIM_SUB_ID, -1)
        set(value) = prefs.edit().putInt(KEY_PREFERRED_SIM_SUB_ID, value).apply()

    /** UI base font size in sp (Montserrat geometric sans). Default 12. */
    var uiFontSizeSp: Int
        get() = prefs.getInt(KEY_UI_FONT_SIZE_SP, DEFAULT_FONT_SIZE_SP).coerceIn(8, 22)
        set(value) = prefs.edit().putInt(KEY_UI_FONT_SIZE_SP, value.coerceIn(8, 22)).apply()

    /** When true, available balance is always visible (no tap-to-reveal / auto-hide). */
    var alwaysShowBalance: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_SHOW_BALANCE, false)
        set(value) = prefs.edit().putBoolean(KEY_ALWAYS_SHOW_BALANCE, value).apply()

    /** When true, the home Favourites (Frequent) section is shown. */
    var favouritesSectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAVOURITES_SECTION, true)
        set(value) = prefs.edit().putBoolean(KEY_FAVOURITES_SECTION, value).apply()

    /**
     * Debug builds only. When true, names, phone numbers, references, and ticket
     * codes are blurred so a screen recording can show the app without them.
     * Keyboards stay sharp. The M-Pesa PIN pad stays in phone order.
     * Release builds ignore this flag.
     */
    var recordingPrivacy: Boolean
        get() = BuildConfig.DEBUG && prefs.getBoolean(KEY_RECORDING_PRIVACY, false)
        set(value) {
            if (!BuildConfig.DEBUG) return
            prefs.edit().putBoolean(KEY_RECORDING_PRIVACY, value).apply()
        }

    /**
     * True after the one-time explanation of Android's unused-app hibernation.
     * SMS and phone access can be reset if the app sits unused for months.
     */
    var hibernationPromptShown: Boolean
        get() = prefs.getBoolean(KEY_HIBERNATION_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_HIBERNATION_PROMPT_SHOWN, value).apply()

    /** When true, first-run permissions + SIM setup has been completed. */
    var firstRunSetupDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN_SETUP_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN_SETUP_DONE, value).apply()

    /** When true, non-confirmation SMS rows have been purged from Room. */
    var confirmationFilterPurgeDone: Boolean
        get() = prefs.getBoolean(KEY_CONFIRMATION_PURGE_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRMATION_PURGE_DONE, value).apply()

    /** When true, stored amounts have been re-parsed with the hardened thousand-separator logic. */
    var amountParseRepairDone: Boolean
        get() = prefs.getBoolean(KEY_AMOUNT_PARSE_REPAIR_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOUNT_PARSE_REPAIR_DONE, value).apply()

    /** When true, M-Shwari transfers already in the ledger have been marked as savings. */
    var savingsClassifyRepairDone: Boolean
        get() = prefs.getBoolean(KEY_SAVINGS_CLASSIFY_REPAIR_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_SAVINGS_CLASSIFY_REPAIR_DONE, value).apply()

    /** When true, unknown confirmations have been re-read for a name, phone, and direction. */
    var unknownPartyRepairDone: Boolean
        get() = prefs.getBoolean(KEY_UNKNOWN_PARTY_REPAIR_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_UNKNOWN_PARTY_REPAIR_DONE, value).apply()

    /** When true, screens use the navy night palette. Passes stay paper white. */
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    /** When true, the home screen can start a no-spend challenge. Default off. */
    var challengesEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHALLENGES, false)
        set(value) = prefs.edit().putBoolean(KEY_CHALLENGES, value).apply()

    /**
     * How this phone's Request QR collects money: `phone`, `till`, or `paybill`.
     * Only one is active.
     */
    var payCollectKind: String
        get() = prefs.getString(KEY_PAY_COLLECT_KIND, "phone") ?: "phone"
        set(value) = prefs.edit().putString(KEY_PAY_COLLECT_KIND, value).apply()

    /** M-Pesa number this phone's pay code collects. Empty until the user sets it. */
    var payPhone: String?
        get() = prefs.getString(KEY_PAY_PHONE, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_PAY_PHONE, value).apply()

    var payTill: String?
        get() = prefs.getString(KEY_PAY_TILL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_PAY_TILL, value).apply()

    var payBillBusiness: String?
        get() = prefs.getString(KEY_PAY_BILL_BUSINESS, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_PAY_BILL_BUSINESS, value).apply()

    var payBillAccount: String?
        get() = prefs.getString(KEY_PAY_BILL_ACCOUNT, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString(KEY_PAY_BILL_ACCOUNT, value).apply()

    /**
     * When false (default), the Request QR changes every UTC day.
     * When true, the same code is shown until the destination or amount changes.
     */
    var payQrFixed: Boolean
        get() = prefs.getBoolean(KEY_PAY_QR_FIXED, false)
        set(value) = prefs.edit().putBoolean(KEY_PAY_QR_FIXED, value).apply()

    /** When true, the weekly spending check-in is offered. Default off. */
    var weeklyCheckInEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEEKLY_CHECKIN, false)
        set(value) = prefs.edit().putBoolean(KEY_WEEKLY_CHECKIN, value).apply()

    /** When true, the inbox has been read once without a date bound. Later scans stay incremental. */
    val inboxHistoryImported: Boolean
        get() = prefs.getBoolean(KEY_INBOX_HISTORY_IMPORTED, false)

    /** Newest SMS date already considered. Later scans read strictly after this, minus a short overlap. */
    val inboxHighWaterMillis: Long
        get() = prefs.getLong(KEY_INBOX_HIGH_WATER, 0L)

    fun markInboxCaughtUp(highWaterMillis: Long) {
        val next = maxOf(inboxHighWaterMillis, highWaterMillis)
        prefs.edit()
            .putBoolean(KEY_INBOX_HISTORY_IMPORTED, true)
            .putLong(KEY_INBOX_HIGH_WATER, next)
            .apply()
    }

    companion object {
        private const val PREFS_FILE = "lipabill_secure_prefs"
        private const val KEY_AUTH_REQUIRED = "auth_required"
        private const val KEY_AUTH_TIMEOUT_MS = "auth_timeout_ms"
        private const val KEY_LAST_BACKGROUNDED = "last_backgrounded_at"
        private const val KEY_BACKFILL_DONE = "sms_backfill_done"
        private const val KEY_CONFIRMATION_PURGE_DONE = "confirmation_filter_purge_v1"
        private const val KEY_AMOUNT_PARSE_REPAIR_DONE = "amount_parse_repair_v2"
        private const val KEY_SAVINGS_CLASSIFY_REPAIR_DONE = "savings_classify_repair_v2"
        private const val KEY_UNKNOWN_PARTY_REPAIR_DONE = "unknown_party_repair_v1"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_CHALLENGES = "challenges_enabled"
        private const val KEY_WEEKLY_CHECKIN = "weekly_checkin_enabled"
        private const val KEY_INBOX_HISTORY_IMPORTED = "inbox_history_imported_v1"
        private const val KEY_INBOX_HIGH_WATER = "inbox_high_water_millis"
        private const val KEY_REPEAT_ENABLED = "repeat_feature_enabled"
        private const val KEY_A11Y_ONBOARDING_SEEN = "a11y_onboarding_seen"
        private const val KEY_A11Y_PREFERRED_ON = "a11y_preferred_on"
        private const val KEY_PREFERRED_SIM_SUB_ID = "preferred_sim_subscription_id"
        private const val KEY_UI_FONT_SIZE_SP = "ui_font_size_sp_v2"
        private const val KEY_ALWAYS_SHOW_BALANCE = "always_show_balance"
        private const val KEY_FAVOURITES_SECTION = "favourites_section_enabled"
        private const val KEY_RECORDING_PRIVACY = "recording_privacy"
        private const val KEY_HIBERNATION_PROMPT_SHOWN = "hibernation_prompt_shown"
        private const val KEY_FIRST_RUN_SETUP_DONE = "first_run_setup_done"
        private const val KEY_PAY_COLLECT_KIND = "pay_collect_kind"
        private const val KEY_PAY_PHONE = "pay_phone"
        private const val KEY_PAY_TILL = "pay_till"
        private const val KEY_PAY_BILL_BUSINESS = "pay_bill_business"
        private const val KEY_PAY_BILL_ACCOUNT = "pay_bill_account"
        private const val KEY_PAY_QR_FIXED = "pay_qr_fixed"
        const val DEFAULT_TIMEOUT_MS = 2 * 60 * 1000L
        const val DEFAULT_FONT_SIZE_SP = 12
    }
}
