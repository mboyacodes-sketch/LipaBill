package com.lipabill.app.ui.permissions

/**
 * Prominent Accessibility disclosure before the user enables the service.
 * Accurately describes USSD assist + optional Settings navigation on sideload builds.
 */
object AccessibilityDisclosure {

    const val TITLE = "LipaBill needs Accessibility access"

    val WHAT_IT_DOES: String = listOf(
        "Only while you start a payment you already confirmed in LipaBill.",
        "Reads M-Pesa payment screens for that active payment session.",
        "Types the next menu number, phone, or amount you already approved.",
        "Shows a secure LipaBill keypad for your M-Pesa PIN (hidden digits, not stored).",
        "On some sideload installs, briefly helps open the LipaBill Accessibility toggle " +
            "in system Settings during setup (Play builds open Settings without auto-tapping)."
    ).joinToString(separator = "\n") { "• $it" }

    val WHAT_IT_DOES_NOT: String = listOf(
        "Never stores your M-Pesa PIN.",
        "Never reads SMS through Accessibility.",
        "Never provides general device automation when no payment session is active.",
        "Never sends money without your Confirm tap and PIN.",
        "Never claims a payment succeeded without a new M-Pesa confirmation SMS."
    ).joinToString(separator = "\n") { "• $it" }

    val REQUIRED_PHRASES: List<String> = listOf(
        "payment you already confirmed",
        "M-Pesa payment screens",
        "PIN",
        "general device automation",
        "Settings"
    )
}
