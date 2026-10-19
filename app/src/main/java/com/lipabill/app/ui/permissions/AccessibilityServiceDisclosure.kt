package com.lipabill.app.ui.permissions

/**
 * Prominent AccessibilityService disclosure (shown in-app before the system toggle).
 * Single source for in-app copy, Play listing paragraph, and review tests.
 */
object AccessibilityServiceDisclosure {

    const val TITLE = "Accessibility Service for Send, Pay, and Repeat"
    const val AGREE = "Agree"
    const val NOT_NOW = "Not now"
    /** Must match `@string/accessibility_service_label`. */
    const val SERVICE_LABEL = "LipaBill Repeat Payment"

    /**
     * Primary disclosure shown immediately before opening Android Accessibility settings
     * to enable LipaBill’s AccessibilityService.
     */
    val STANDARD_BODY: String = listOf(
        "LipaBill uses Android’s AccessibilityService API to help complete M-Pesa USSD " +
            "payments that you start and confirm in the app.",
        "This is needed for Send money, Pay (Paybill, till, and Pochi), and Repeat a past " +
            "payment — so LipaBill can fill the Safaricom USSD screens after you tap Confirm.",
        "After you confirm and enter your M-Pesa PIN on LipaBill’s secure keypad, the service " +
            "may read on-screen USSD and dialer prompts and enter the next menu digit, phone " +
            "number, amount, or PIN field for that payment only.",
        "That on-screen text is used only to finish the payment you confirmed. It is not " +
            "stored as a transcript, not sent off this phone, and not shared with anyone.",
        "The service runs only while a payment session you started is active. It does not " +
            "read SMS, does not change system settings, does not stop you from turning the " +
            "service off or uninstalling LipaBill, and is not used to control unrelated apps " +
            "in the background.",
        "LipaBill is not an accessibility tool for people with disabilities.",
        "You can continue without enabling Accessibility Service. Send, Pay, and automatic " +
            "Repeat fill stay unavailable until you turn on “$SERVICE_LABEL” in Android " +
            "Accessibility settings."
    ).joinToString(separator = " ")

    /** Phrases that must appear in the disclosure (for unit tests / Play review). */
    val REQUIRED_PHRASES: List<String> = listOf(
        "AccessibilityService API",
        "Send money",
        "Pay",
        "Repeat",
        "USSD",
        "payment session",
        "does not read SMS",
        "not shared",
        "not an accessibility tool",
        "continue without"
    )

    /**
     * Paragraph for the Play Store long description (Console listing).
     * Keep `docs/play-store-listing.md` identical — tested.
     */
    val PLAY_STORE_LISTING_PARAGRAPH: String = listOf(
        "AccessibilityService API: LipaBill uses Android’s AccessibilityService API only to " +
            "assist M-Pesa USSD payment flows that you start and confirm in the app " +
            "(Send money, Pay bill/till/Pochi, and Repeat a past payment).",
        "After you confirm a payment and enter your M-Pesa PIN on LipaBill’s keypad, the " +
            "service can read the on-screen USSD prompts and fill the next step for that " +
            "session only.",
        "On-screen USSD text is not stored as a transcript, not sent off this phone, and not " +
            "shared with anyone.",
        "It does not run for unrelated apps when you are not paying, does not read SMS, and " +
            "is not an accessibility tool for disabilities.",
        "You can turn “$SERVICE_LABEL” off anytime in Android Settings → Accessibility."
    ).joinToString(separator = " ")
}
