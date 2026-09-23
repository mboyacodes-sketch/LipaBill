package com.lipabill.app.ui.permissions

/**
 * Prominent SMS-permission disclosure copy (shown before the system dialog).
 * Keep wording factual — do not claim Google mandates SMS or that granting is mandatory.
 */
object SmsPermissionDisclosure {

    const val TITLE = "SMS access for M-Pesa history"

    /** Primary disclosure shown immediately before requesting READ_SMS / RECEIVE_SMS. */
    val STANDARD_BODY: String = listOf(
        "LipaBill reads Safaricom M-Pesa confirmation SMS on this device.",
        "SMS access builds your local transaction history, balance, and spending overview.",
        "When new M-Pesa confirmations arrive, LipaBill can update the ledger automatically.",
        "SMS content is processed and stored only on this phone (encrypted).",
        "LipaBill does not send SMS and is not your default messaging app.",
        "You can continue without SMS access; automatic history, backfill, and live " +
            "payment confirmation will be unavailable or limited."
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
        "M-Pesa confirmation",
        "local transaction history",
        "update the ledger automatically",
        "on this phone",
        "does not send SMS",
        "continue without SMS"
    )
}
