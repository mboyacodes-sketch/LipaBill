package com.lipabill.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.lipabill.app.BuildConfig
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.defaultLedgerSources
import com.lipabill.app.data.model.parseStoredSources
import com.lipabill.app.data.model.resolvedLedgerSources
import com.lipabill.app.data.model.serializeLedgerSources
import com.lipabill.app.pay.PayLink
import com.lipabill.app.social.SocialLink
import com.lipabill.app.ui.privacy.RecordingCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
     * user intentionally turns the service off from Settings. When the OS toggle
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
        set(value) {
            prefs.edit().putInt(KEY_PREFERRED_SIM_SUB_ID, value).apply()
            _preferredSim.value = value
        }

    private val _preferredSim by lazy { MutableStateFlow(preferredSimSubscriptionId) }

    /** Live [preferredSimSubscriptionId], so every screen follows a wallet switch made anywhere. */
    val preferredSim: StateFlow<Int> get() = _preferredSim.asStateFlow()

    /** UI base font size in sp (Montserrat geometric sans). Default 12. */
    var uiFontSizeSp: Int
        get() = prefs.getInt(KEY_UI_FONT_SIZE_SP, DEFAULT_FONT_SIZE_SP).coerceIn(8, 22)
        set(value) = prefs.edit().putInt(KEY_UI_FONT_SIZE_SP, value.coerceIn(8, 22)).apply()


    /**
     * Debug / internal builds only. When true, FLAG_SECURE is cleared and names,
     * numbers, references, and ticket codes are blurred in the framebuffer.
     * Keyboards stay sharp. Play release ignores this flag. Defaults on for
     * debug/sideload so scrcpy and screen recording work without a Settings trip.
     */
    var recordingPrivacy: Boolean
        get() = RecordingCapture.available &&
            prefs.getBoolean(KEY_RECORDING_PRIVACY, true)
        set(value) {
            if (!RecordingCapture.available) return
            prefs.edit().putBoolean(KEY_RECORDING_PRIVACY, value).apply()
        }

    /**
     * True after the one-time explanation of Android's unused-app hibernation.
     * SMS and phone access can be reset if the app sits unused for months.
     */
    var hibernationPromptShown: Boolean
        get() = prefs.getBoolean(KEY_HIBERNATION_PROMPT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_HIBERNATION_PROMPT_SHOWN, value).apply()

    /** Country the ledger reads confirmation SMS for. Existing installs stay Kenya. */
    var appRegion: AppRegion
        get() = AppRegion.fromIso(prefs.getString(KEY_APP_REGION, null))
        set(value) = prefs.edit().putString(KEY_APP_REGION, value.iso).apply()

    /**
     * After a region change, the next scan reads the whole inbox again so the
     * new country's senders are not skipped by the previous high-water mark.
     */
    fun resetInboxScanForRegionChange() {
        prefs.edit()
            .putBoolean(KEY_INBOX_HISTORY_IMPORTED, false)
            .putLong(KEY_INBOX_HIGH_WATER, 0L)
            .putBoolean(KEY_BACKFILL_DONE, false)
            .putBoolean(KEY_WALLET_RAILS_INBOX_SCAN, false)
            .remove(KEY_INBOX_FULLY_SCANNED_SOURCES)
            .apply()
    }

    /**
     * Wallets and banks on this ledger. Null storage means every rail in [region]
     * so existing installs keep current behaviour until the user edits the list.
     */
    fun enabledLedgerSources(region: AppRegion = appRegion): Set<MoneySource> =
        resolvedLedgerSources(region, storedLedgerSources())

    fun storedLedgerSources(): Set<MoneySource>? =
        if (prefs.contains(KEY_ENABLED_SOURCES)) {
            parseStoredSources(prefs.getString(KEY_ENABLED_SOURCES, null)) ?: emptySet()
        } else {
            null
        }

    fun setEnabledLedgerSources(sources: Set<MoneySource>, region: AppRegion = appRegion) {
        val next = resolvedLedgerSources(region, sources)
        prefs.edit().putString(KEY_ENABLED_SOURCES, serializeLedgerSources(next)).apply()
    }

    /**
     * Old first-run saved only the primary wallet. Once, expand that to the multi-rail
     * default so Airtel/bank SMS can import without a Settings visit. Skips null storage
     * (already every rail) and any set the user already widened or trimmed.
     */
    fun widenPrimaryOnlyLedgerSourcesIfNeeded(region: AppRegion = appRegion): Boolean {
        if (prefs.getBoolean(KEY_LEDGER_RAILS_WIDENED, false)) return false
        val stored = storedLedgerSources()
        if (stored == null || stored != setOf(region.primaryWallet)) {
            prefs.edit().putBoolean(KEY_LEDGER_RAILS_WIDENED, true).apply()
            return false
        }
        setEnabledLedgerSources(defaultLedgerSources(region), region)
        prefs.edit().putBoolean(KEY_LEDGER_RAILS_WIDENED, true).apply()
        return true
    }

    /**
     * One full inbox re-read after multi-rail SMS import stopped being SIM-gated.
     * Earlier installs marked the inbox "caught up" on M-Pesa alone, so incremental
     * scans never revisited older Airtel/bank confirmations. False until first-run
     * finishes; true once when a full rescan should run; then stays consumed.
     */
    fun consumePendingFullInboxRescan(): Boolean {
        if (prefs.getBoolean(KEY_MULTI_RAIL_INBOX_RESCAN, false)) return false
        if (!firstRunSetupDone) return false
        prefs.edit().putBoolean(KEY_MULTI_RAIL_INBOX_RESCAN, true).apply()
        return true
    }

    /** SIM-bound wallets that had an active line at the last check; null before the first check. */
    var lineWalletsSeen: Set<MoneySource>?
        get() = if (prefs.contains(KEY_LINE_WALLETS_SEEN)) {
            parseStoredSources(prefs.getString(KEY_LINE_WALLETS_SEEN, null)) ?: emptySet()
        } else {
            null
        }
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_LINE_WALLETS_SEEN)
            else putString(KEY_LINE_WALLETS_SEEN, serializeLedgerSources(value))
        }.apply()

    /** When true, first-run permissions + SIM setup has been completed. */
    var firstRunSetupDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN_SETUP_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN_SETUP_DONE, value).apply()

    /**
     * Debug / sideload only. When true, first-run stays incomplete across process
     * death so Settings → Replay setup wizard is not skipped by the existing-install
     * migration. Cleared when the wizard finishes.
     */
    var replayFirstRunPending: Boolean
        get() = prefs.getBoolean(KEY_REPLAY_FIRST_RUN_PENDING, false)
        set(value) = prefs.edit().putBoolean(KEY_REPLAY_FIRST_RUN_PENDING, value).apply()

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

    /** One-shot: re-read bank/wallet SMS direction after send/receive classify fix. */
    var bankDirectionRepairDone: Boolean
        get() = prefs.getBoolean(KEY_BANK_DIRECTION_REPAIR_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_BANK_DIRECTION_REPAIR_DONE, value).apply()

    /** One-shot: re-extract bank merchant / till / account holder from SMS bodies. */
    var bankDetailRepairDone: Boolean
        get() = prefs.getBoolean(KEY_BANK_DETAIL_REPAIR_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_BANK_DETAIL_REPAIR_DONE, value).apply()

    /** Regular payees the user marked essential (rent, school fees): kept out of cost nudges. */
    var essentialPayees: Set<String>
        get() = prefs.getStringSet(KEY_ESSENTIAL_PAYEES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_ESSENTIAL_PAYEES, HashSet(value)).apply()

    /** Regular payees the user is keeping an eye on from Insights. */
    var watchedPayees: Set<String>
        get() = prefs.getStringSet(KEY_WATCHED_PAYEES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_WATCHED_PAYEES, HashSet(value)).apply()

    /** Insights badges already earned; once earned they stay. */
    var earnedBadges: Set<String>
        get() = prefs.getStringSet(KEY_EARNED_BADGES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EARNED_BADGES, HashSet(value)).apply()

    /** Habits badges already earned; once earned they stay. */
    var earnedHabitBadges: Set<String>
        get() = prefs.getStringSet(KEY_EARNED_HABIT_BADGES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EARNED_HABIT_BADGES, HashSet(value)).apply()

    /** Habit patterns the user answered "Not me" to. */
    var dismissedHabitPatterns: Set<String>
        get() = prefs.getStringSet(KEY_DISMISSED_HABIT_PATTERNS, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_DISMISSED_HABIT_PATTERNS, HashSet(value)).apply()

    /** Last time Habits was opened, for the catch-up card. 0 before the first visit. */
    var habitsLastVisit: Long
        get() = prefs.getLong(KEY_HABITS_LAST_VISIT, 0L)
        set(value) = prefs.edit().putLong(KEY_HABITS_LAST_VISIT, value).apply()

    /**
     * Payees the user taught Habits a category for. Encoded as `payeeKey=categoryId`.
     */
    var taughtPayeeCategories: Set<String>
        get() = prefs.getStringSet(KEY_TAUGHT_PAYEE_CATEGORIES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_TAUGHT_PAYEE_CATEGORIES, HashSet(value)).apply()

    /**
     * Spend-category overrides from the receipt editor. Encoded as `payeeKey=spendCategoryId`
     * (same payee key as Insights regulars). Applies to every linked payment.
     */
    var spendCategoryOverrides: Set<String>
        get() = prefs.getStringSet(KEY_SPEND_CATEGORY_OVERRIDES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_SPEND_CATEGORY_OVERRIDES, HashSet(value)).apply()

    /** Monthly category budgets, `categoryId=limit`. */
    var categoryBudgets: Set<String>
        get() = prefs.getStringSet(KEY_CATEGORY_BUDGETS, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_CATEGORY_BUDGETS, HashSet(value)).apply()

    /** Savings goals as a JSON array. */
    var savingsGoals: String?
        get() = prefs.getString(KEY_SAVINGS_GOALS, null)
        set(value) = prefs.edit().putString(KEY_SAVINGS_GOALS, value).apply()

    /** Money moments already sent (one key per moment and period), so each nudges once. */
    var moneyMomentsSent: Set<String>
        get() = prefs.getStringSet(KEY_MONEY_MOMENTS_SENT, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_MONEY_MOMENTS_SENT, HashSet(value)).apply()

    /** True while a duo / accountability invite is waiting. */
    var pendingDuoInvite: Boolean
        get() = prefs.getBoolean(KEY_PENDING_DUO_INVITE, false)
        set(value) = prefs.edit().putBoolean(KEY_PENDING_DUO_INVITE, value).apply()

    /** DIY challenge template ids the user built (`diy:{categoryId}:{days}:{capCents}`). */
    var customDiyChallenges: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOM_DIY_CHALLENGES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_CUSTOM_DIY_CHALLENGES, HashSet(value)).apply()

    /** When true, screens use the navy night palette. Passes stay paper white. */
    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()


    /**
     * One-shot: copy the old global Request destinations onto the region's
     * primary wallet so multi-MNO phones do not share one QR across every rail.
     */
    fun ensureRequestPayDestPerWallet(region: AppRegion = appRegion) {
        if (prefs.getBoolean(KEY_PAY_DEST_PER_WALLET, false)) return
        val into = region.primaryWallet
        val edit = prefs.edit()
        fun copyString(legacy: String, scoped: String) {
            if (prefs.contains(scoped)) return
            val value = prefs.getString(legacy, null) ?: return
            edit.putString(scoped, value)
        }
        fun copyBool(legacy: String, scoped: String) {
            if (prefs.contains(scoped)) return
            if (!prefs.contains(legacy)) return
            edit.putBoolean(scoped, prefs.getBoolean(legacy, false))
        }
        copyString(KEY_PAY_COLLECT_KIND, payDestKey(KEY_PAY_COLLECT_KIND, into))
        copyString(KEY_PAY_PHONE, payDestKey(KEY_PAY_PHONE, into))
        copyBool(KEY_PAY_PHONE_HIDDEN, payDestKey(KEY_PAY_PHONE_HIDDEN, into))
        copyString(KEY_PAY_TILL, payDestKey(KEY_PAY_TILL, into))
        copyString(KEY_PAY_BILL_BUSINESS, payDestKey(KEY_PAY_BILL_BUSINESS, into))
        copyString(KEY_PAY_BILL_ACCOUNT, payDestKey(KEY_PAY_BILL_ACCOUNT, into))
        edit.putBoolean(KEY_PAY_DEST_PER_WALLET, true).apply()
    }

    /** How this wallet's Request QR collects money: `phone`, `till`, or `paybill`. */
    fun payCollectKind(source: MoneySource): String {
        ensureRequestPayDestPerWallet()
        return prefs.getString(payDestKey(KEY_PAY_COLLECT_KIND, source), null) ?: "phone"
    }

    fun setPayCollectKind(source: MoneySource, value: String) {
        ensureRequestPayDestPerWallet()
        prefs.edit().putString(payDestKey(KEY_PAY_COLLECT_KIND, source), value).apply()
    }

    /** Phone number this wallet's pay code collects. Empty until the user sets it. */
    fun payPhone(source: MoneySource): String? {
        ensureRequestPayDestPerWallet()
        return prefs.getString(payDestKey(KEY_PAY_PHONE, source), null)?.takeIf { it.isNotBlank() }
    }

    fun setPayPhone(source: MoneySource, value: String?) {
        ensureRequestPayDestPerWallet()
        prefs.edit().putString(payDestKey(KEY_PAY_PHONE, source), value).apply()
    }

    /**
     * When true, Request → Payments hides Send Money for [source] even if a SIM
     * number is present. Cleared when they add/save a phone again.
     */
    fun payPhoneHidden(source: MoneySource): Boolean {
        ensureRequestPayDestPerWallet()
        return prefs.getBoolean(payDestKey(KEY_PAY_PHONE_HIDDEN, source), false)
    }

    fun setPayPhoneHidden(source: MoneySource, value: Boolean) {
        ensureRequestPayDestPerWallet()
        prefs.edit().putBoolean(payDestKey(KEY_PAY_PHONE_HIDDEN, source), value).apply()
    }

    fun payTill(source: MoneySource): String? {
        ensureRequestPayDestPerWallet()
        return prefs.getString(payDestKey(KEY_PAY_TILL, source), null)?.takeIf { it.isNotBlank() }
    }

    fun setPayTill(source: MoneySource, value: String?) {
        ensureRequestPayDestPerWallet()
        prefs.edit().putString(payDestKey(KEY_PAY_TILL, source), value).apply()
    }

    fun payBillBusiness(source: MoneySource): String? {
        ensureRequestPayDestPerWallet()
        return prefs.getString(payDestKey(KEY_PAY_BILL_BUSINESS, source), null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setPayBillBusiness(source: MoneySource, value: String?) {
        ensureRequestPayDestPerWallet()
        prefs.edit().putString(payDestKey(KEY_PAY_BILL_BUSINESS, source), value).apply()
    }

    fun payBillAccount(source: MoneySource): String? {
        ensureRequestPayDestPerWallet()
        return prefs.getString(payDestKey(KEY_PAY_BILL_ACCOUNT, source), null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setPayBillAccount(source: MoneySource, value: String?) {
        ensureRequestPayDestPerWallet()
        prefs.edit().putString(payDestKey(KEY_PAY_BILL_ACCOUNT, source), value).apply()
    }

    /** Demo / seed helper — writes the primary-wallet phone Request destination. */
    var payPhone: String?
        get() = payPhone(appRegion.primaryWallet)
        set(value) = setPayPhone(appRegion.primaryWallet, value)

    /** Clears a Request → Payments destination for [source]. */
    fun clearPayDestination(source: MoneySource, kind: PayLink.Kind) {
        ensureRequestPayDestPerWallet()
        when (kind) {
            PayLink.Kind.PHONE -> {
                setPayPhone(source, null)
                setPayPhoneHidden(source, true)
            }
            PayLink.Kind.TILL -> setPayTill(source, null)
            PayLink.Kind.PAYBILL -> {
                setPayBillBusiness(source, null)
                setPayBillAccount(source, null)
            }
        }
    }

    /**
     * When false (default), the Request QR changes every UTC day.
     * When true, the same code is shown until the destination or amount changes.
     */
    var payQrFixed: Boolean
        get() = prefs.getBoolean(KEY_PAY_QR_FIXED, false)
        set(value) = prefs.edit().putBoolean(KEY_PAY_QR_FIXED, value).apply()

    /**
     * Request screen top mode: `payments` (pay QR) or `socials` (profile QR).
     */
    var requestQrMode: String
        get() = prefs.getString(KEY_REQUEST_QR_MODE, "payments") ?: "payments"
        set(value) = prefs.edit().putString(KEY_REQUEST_QR_MODE, value).apply()

    /** Last social network chosen on the Request → Socials tab. */
    var socialPlatformKey: String
        get() = prefs.getString(KEY_SOCIAL_PLATFORM, "instagram") ?: "instagram"
        set(value) = prefs.edit().putString(KEY_SOCIAL_PLATFORM, value).apply()

    fun socialHandle(platformKey: String): String? =
        prefs.getString(socialHandlePrefKey(platformKey), null)?.takeIf { it.isNotBlank() }

    fun setSocialHandle(platformKey: String, handle: String?) {
        prefs.edit().putString(socialHandlePrefKey(platformKey), handle?.takeIf { it.isNotBlank() }).apply()
    }

    /**
     * Debug / internal only: fill every Socials platform with a demo handle so
     * Request → Socials can flip through QR badges without typing.
     * Overwrites existing social handles once per install (keyed flag).
     */
    fun seedDemoSocialHandles() {
        if (!BuildConfig.DEBUG && !BuildConfig.SIDELOAD_DISTRIBUTION) return
        if (prefs.getBoolean(KEY_DEMO_SOCIAL_SEEDED, false)) return
        // Only a few so Request → Socials still has room to exercise Add.
        val demo = mapOf(
            SocialLink.Platform.INSTAGRAM to "natgeo",
            SocialLink.Platform.WHATSAPP to "254712345678",
            SocialLink.Platform.X to "NASA"
        )
        val edit = prefs.edit()
        SocialLink.Platform.entries.forEach { platform ->
            edit.remove(socialHandlePrefKey(platform.storageKey))
        }
        demo.forEach { (platform, handle) ->
            val clean = SocialLink.canonicalHandle(platform, handle) ?: handle
            edit.putString(socialHandlePrefKey(platform.storageKey), clean)
        }
        edit.putString(KEY_SOCIAL_PLATFORM, SocialLink.Platform.WHATSAPP.storageKey)
        edit.putString(KEY_REQUEST_QR_MODE, "socials")
        edit.putBoolean(KEY_DEMO_SOCIAL_SEEDED, true)
        edit.apply()
    }

    /** When true, the inbox has been read once without a date bound. Later scans stay incremental. */
    val inboxHistoryImported: Boolean
        get() = prefs.getBoolean(KEY_INBOX_HISTORY_IMPORTED, false)

    /** Newest SMS date already considered. Later scans read strictly after this, minus a short overlap. */
    val inboxHighWaterMillis: Long
        get() = prefs.getLong(KEY_INBOX_HIGH_WATER, 0L)

    /**
     * When false, the next inbox read ignores the high-water mark so bank and
     * wallet rails added after the first import are not skipped.
     */
    var walletRailsInboxScanDone: Boolean
        get() = prefs.getBoolean(KEY_WALLET_RAILS_INBOX_SCAN, false)
        set(value) = prefs.edit().putBoolean(KEY_WALLET_RAILS_INBOX_SCAN, value).apply()

    /**
     * Rails covered by a completed full inbox pass. Enabling a rail that is not
     * in this set (bank-only → MNO) must re-read older SMS, not only the tip.
     */
    fun inboxFullyScannedSources(): Set<MoneySource> =
        parseStoredSources(prefs.getString(KEY_INBOX_FULLY_SCANNED_SOURCES, null)).orEmpty()

    fun markInboxFullyScanned(sources: Set<MoneySource>) {
        val next = inboxFullyScannedSources() + sources
        prefs.edit()
            .putString(KEY_INBOX_FULLY_SCANNED_SOURCES, serializeLedgerSources(next))
            .apply()
    }

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
        /** v2: also drop failure SMS that were stored as deposits after the gate widen. */
        private const val KEY_CONFIRMATION_PURGE_DONE = "confirmation_filter_purge_v2"
        private const val KEY_AMOUNT_PARSE_REPAIR_DONE = "amount_parse_repair_v2"
        private const val KEY_SAVINGS_CLASSIFY_REPAIR_DONE = "savings_classify_repair_v2"
        private const val KEY_UNKNOWN_PARTY_REPAIR_DONE = "unknown_party_repair_v1"
        private const val KEY_BANK_DIRECTION_REPAIR_DONE = "bank_direction_repair_v2"
        private const val KEY_BANK_DETAIL_REPAIR_DONE = "bank_detail_repair_v1"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_ESSENTIAL_PAYEES = "essential_payees"
        private const val KEY_WATCHED_PAYEES = "watched_payees"
        private const val KEY_EARNED_BADGES = "earned_badges"
        private const val KEY_EARNED_HABIT_BADGES = "earned_habit_badges"
        private const val KEY_DISMISSED_HABIT_PATTERNS = "dismissed_habit_patterns"
        private const val KEY_HABITS_LAST_VISIT = "habits_last_visit"
        private const val KEY_TAUGHT_PAYEE_CATEGORIES = "taught_payee_categories"
        private const val KEY_SPEND_CATEGORY_OVERRIDES = "spend_category_overrides_v1"
        private const val KEY_CATEGORY_BUDGETS = "category_budgets_v1"
        private const val KEY_SAVINGS_GOALS = "savings_goals_v1"
        private const val KEY_MONEY_MOMENTS_SENT = "money_moments_sent_v1"
        private const val KEY_PENDING_DUO_INVITE = "pending_duo_invite"
        private const val KEY_CUSTOM_DIY_CHALLENGES = "custom_diy_challenges"
        private const val KEY_INBOX_HISTORY_IMPORTED = "inbox_history_imported_v1"
        private const val KEY_INBOX_HIGH_WATER = "inbox_high_water_millis"
        // v2: bank formats (NCBA, Equity, StanChart, Absa) and Lipa-na-KCB order widened; re-read once.
        private const val KEY_WALLET_RAILS_INBOX_SCAN = "wallet_rails_inbox_scan_v2"
        private const val KEY_INBOX_FULLY_SCANNED_SOURCES = "inbox_fully_scanned_sources_v1"
        private const val KEY_REPEAT_ENABLED = "repeat_feature_enabled"
        private const val KEY_A11Y_ONBOARDING_SEEN = "a11y_onboarding_seen"
        private const val KEY_A11Y_PREFERRED_ON = "a11y_preferred_on"
        private const val KEY_PREFERRED_SIM_SUB_ID = "preferred_sim_subscription_id"
        private const val KEY_UI_FONT_SIZE_SP = "ui_font_size_sp_v2"
        private const val KEY_RECORDING_PRIVACY = "recording_privacy"
        private const val KEY_HIBERNATION_PROMPT_SHOWN = "hibernation_prompt_shown"
        private const val KEY_FIRST_RUN_SETUP_DONE = "first_run_setup_done"
        private const val KEY_REPLAY_FIRST_RUN_PENDING = "replay_first_run_pending"
        private const val KEY_APP_REGION = "app_region"
        private const val KEY_ENABLED_SOURCES = "enabled_ledger_sources_v1"
        private const val KEY_LEDGER_RAILS_WIDENED = "ledger_rails_widened_v1"
        private const val KEY_MULTI_RAIL_INBOX_RESCAN = "multi_rail_inbox_rescan_v2"
        private const val KEY_LINE_WALLETS_SEEN = "line_wallets_seen_v1"
        private const val KEY_PAY_COLLECT_KIND = "pay_collect_kind"
        private const val KEY_PAY_PHONE = "pay_phone"
        private const val KEY_PAY_PHONE_HIDDEN = "pay_phone_hidden"
        private const val KEY_PAY_TILL = "pay_till"
        private const val KEY_PAY_BILL_BUSINESS = "pay_bill_business"
        private const val KEY_PAY_BILL_ACCOUNT = "pay_bill_account"
        private const val KEY_PAY_DEST_PER_WALLET = "pay_dest_per_wallet_v1"
        private const val KEY_PAY_QR_FIXED = "pay_qr_fixed"
        private const val KEY_REQUEST_QR_MODE = "request_qr_mode"
        private const val KEY_SOCIAL_PLATFORM = "social_platform_key"
        private const val KEY_DEMO_SOCIAL_SEEDED = "demo_social_handles_seeded_v2"
        private fun socialHandlePrefKey(platformKey: String) = "social_handle_$platformKey"
        private fun payDestKey(base: String, source: MoneySource) = "${base}_${source.name}"
        const val DEFAULT_TIMEOUT_MS = 2 * 60 * 1000L
        const val DEFAULT_FONT_SIZE_SP = 12
    }
}
