package com.lipabill.app.ui.permissions

/**
 * Prominent SMS-permission disclosure copy (shown before the system dialog).
 * Keep wording factual — do not claim Google mandates SMS or that granting is mandatory.
 * Play exception: SMS-based money management (budget / track). Not OTP, not 5-digit UPI.
 */
object SmsPermissionDisclosure {

    const val TITLE = "SMS for money management"

    /** Primary disclosure shown immediately before requesting READ_SMS / RECEIVE_SMS. */
    val STANDARD_BODY: String = listOf(
        "LipaBill’s core feature is on-device money management.",
        "It reads confirmation SMS from wallets and banks you enable on this phone " +
            "to build your encrypted transaction history, available balance, and spending overview, " +
            "and updates that ledger automatically.",
        "Confirmations stay on this phone, encrypted, and are not uploaded or shared.",
        "LipaBill does not send SMS, does not read OTPs, and is not your default messaging app.",
        "You can continue without SMS; history stays empty until you allow it."
    ).joinToString(separator = " ")

    /**
     * Same facts plus sideload restricted-settings guidance (Android 15–24 APK installs).
     */
    val RESTRICTED_SETTINGS_BODY: String =
        STANDARD_BODY +
            " On Android 15–24, sideloaded installs may need Allow restricted settings " +
            "on App info before SMS can be granted."

    fun body(showRestrictedSettingsHelp: Boolean): String =
        if (showRestrictedSettingsHelp) RESTRICTED_SETTINGS_BODY else STANDARD_BODY

    /** Phrases that must appear in the disclosure (for unit tests). */
    val REQUIRED_PHRASES: List<String> = listOf(
        "money management",
        "confirmation SMS",
        "wallets and banks",
        "encrypted transaction history",
        "updates that ledger automatically",
        "on this phone",
        "encrypted",
        "not uploaded or shared",
        "does not send SMS",
        "does not read OTPs",
        "continue without SMS"
    )

    /**
     * Store-listing SMS paragraph. Keep `docs/play-store-listing.md` identical — tested.
     */
    val PLAY_STORE_LISTING_PARAGRAPH: String = listOf(
        "SMS-based money management: LipaBill’s core feature is reading Kenyan wallet and " +
            "bank confirmation SMS (M-Pesa, Airtel Money, T-Kash, KCB, Equity, Co-op, NCBA, " +
            "Absa, and similar) on this device " +
            "to build a local encrypted ledger, available balance, and spending overview " +
            "(send, receive, paybill, till, Pochi, withdraw, deposit, reversals, Fuliza, " +
            "savings, airtime). Those confirmations stay on this device, encrypted, and are " +
            "not uploaded or shared.",
        "LipaBill does not send SMS, does not read OTPs or other verification codes, " +
            "and is not your default messaging app.",
        "You can deny SMS; history, balance, and insights stay empty until you allow it."
    ).joinToString(separator = " ")

    /**
     * Play Console “instructions for reviewers” when the field is capped at 500 characters.
     */
    const val CONSOLE_REVIEWER_INSTRUCTIONS_500: String =
        "Core: READ_SMS/RECEIVE_SMS for on-device money management. Reads confirmation " +
            "SMS from wallets and banks you enable. Confirmations stay on this device, encrypted, " +
            "not uploaded or shared. Does not send SMS, read OTPs/5-digit codes, or act as " +
            "default SMS app. No Call Log. Verify (Kenya, real confirmation SMS): play AAB → " +
            "Welcome → Next → SMS disclosure → Allow SMS → home ledger. Deny SMS = empty " +
            "history. Settings → Rescan inbox. Promo/OTP ignored. Non-Kenya: use video. " +
            "Not geo-blocked."
}
