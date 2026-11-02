package com.lipabill.app.ui.permissions

/**
 * Prominent AccessibilityService disclosure (shown in-app before the system toggle).
 * Single source for in-app copy, Play listing paragraph, and review tests.
 */
object AccessibilityServiceDisclosure {

    const val TITLE = "Accessibility Service for Send, Pay, and Repeat"
    const val AGREE = "Agree"
    const val NOT_NOW = "Not now"
    const val CONTINUE_WITHOUT = "Continue without"
    /** Must match `@string/accessibility_service_label`. */
    const val SERVICE_LABEL = "LipaBill M-Pesa payments"

    /** Short in-app points. Join to [STANDARD_BODY] for tests and listing checks. */
    val STANDARD_POINTS: List<String> = listOf(
        "LipaBill uses Android’s AccessibilityService API to fill M-Pesa USSD screens for " +
            "Send money, Pay, and Repeat after you confirm a payment.",
        "It may read on-screen USSD prompts for that payment session only. That text is not " +
            "stored, not sent off this phone, and not shared with anyone.",
        "It does not read SMS and is not an accessibility tool for disabilities.",
        "You can continue without enabling it. Send, Pay, and Repeat stay off until you " +
            "turn on “$SERVICE_LABEL”. History, Insights, and Passes stay available."
    )

    /**
     * Primary disclosure shown immediately before opening Android Accessibility settings
     * to enable LipaBill’s AccessibilityService.
     */
    val STANDARD_BODY: String = STANDARD_POINTS.joinToString(separator = " ")

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
