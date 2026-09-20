package com.lipabill.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    /** When true, available balance is always visible (no eye / auto-hide). */
    var alwaysShowBalance: Boolean
        get() = prefs.getBoolean(KEY_ALWAYS_SHOW_BALANCE, false)
        set(value) = prefs.edit().putBoolean(KEY_ALWAYS_SHOW_BALANCE, value).apply()

    /** When true, non-confirmation SMS rows have been purged from Room. */
    var confirmationFilterPurgeDone: Boolean
        get() = prefs.getBoolean(KEY_CONFIRMATION_PURGE_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRMATION_PURGE_DONE, value).apply()

    companion object {
        private const val PREFS_FILE = "lipabill_secure_prefs"
        private const val KEY_AUTH_REQUIRED = "auth_required"
        private const val KEY_AUTH_TIMEOUT_MS = "auth_timeout_ms"
        private const val KEY_LAST_BACKGROUNDED = "last_backgrounded_at"
        private const val KEY_BACKFILL_DONE = "sms_backfill_done"
        private const val KEY_CONFIRMATION_PURGE_DONE = "confirmation_filter_purge_v1"
        private const val KEY_REPEAT_ENABLED = "repeat_feature_enabled"
        private const val KEY_A11Y_ONBOARDING_SEEN = "a11y_onboarding_seen"
        private const val KEY_PREFERRED_SIM_SUB_ID = "preferred_sim_subscription_id"
        private const val KEY_UI_FONT_SIZE_SP = "ui_font_size_sp_v2"
        private const val KEY_ALWAYS_SHOW_BALANCE = "always_show_balance"
        const val DEFAULT_TIMEOUT_MS = 2 * 60 * 1000L
        const val DEFAULT_FONT_SIZE_SP = 12
    }
}
