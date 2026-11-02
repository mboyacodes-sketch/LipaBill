package com.lipabill.app.ui.permissions

import com.lipabill.app.data.model.AppRegion

/**
 * Consent pages. Each sensitive ask is its own page so AccessibilityService
 * is never bundled with SMS or phone (Play User Data policy).
 */
enum class FirstRunPage {
    Welcome,
    Region,
    Providers,
    Sms,
    Phone,
    Accessibility,
    Camera,
    Notifications,
    Hibernation
}

object FirstRunWizard {
    /** First run is ledger (region + providers + SMS) then Send/Pay access when the country supports it. */
    fun firstRunPages(region: AppRegion = AppRegion.DEFAULT): List<FirstRunPage> = buildList {
        add(FirstRunPage.Welcome)
        add(FirstRunPage.Region)
        add(FirstRunPage.Providers)
        add(FirstRunPage.Sms)
        if (region.canSendPay) {
            add(FirstRunPage.Phone)
            add(FirstRunPage.Accessibility)
        }
    }

    val PAGES: List<FirstRunPage>
        get() = firstRunPages()

    /** Later visits skip Welcome and Region. Payment access stays a cluster. */
    val LATER_PAGES: List<FirstRunPage> =
        FirstRunPage.entries.filter {
            it != FirstRunPage.Welcome &&
                it != FirstRunPage.Region &&
                it != FirstRunPage.Providers
        }

    private val PAYMENT_PAGES: List<FirstRunPage> = listOf(
        FirstRunPage.Sms,
        FirstRunPage.Phone,
        FirstRunPage.Accessibility
    )

    fun laterPages(start: FirstRunPage): List<FirstRunPage> = when (start) {
        FirstRunPage.Welcome,
        FirstRunPage.Region,
        FirstRunPage.Providers,
        FirstRunPage.Sms,
        FirstRunPage.Phone,
        FirstRunPage.Accessibility -> PAYMENT_PAGES
        else -> listOf(start)
    }

    const val WELCOME_TITLE = "Your money has a story"
    const val WELCOME_BODY =
        "See it clearly, then send, pay, or show a pass. Encrypted on this phone, not in a LipaBill cloud."

    const val REGION_TITLE = "Where is this phone?"
    const val REGION_BODY =
        "The picture is built from wallets and banks in that country. " +
            "We'll use this SIM when we can. You can change this in Settings."

    const val PROVIDERS_TITLE = "Who should be in the picture?"
    const val PROVIDERS_BODY =
        "Wallets first, then banks. LipaBill keeps those confirmation SMS on this phone. " +
            "You can change this in Settings."

    const val PHONE_TITLE = "Phone for Send and Pay"
    const val PHONE_BODY =
        "Send and Pay place a Safaricom M-Pesa call on the right SIM after you confirm. " +
            "Call history is not read, and your PIN is not stored."

    val SMS_TITLE: String = SmsPermissionDisclosure.TITLE
    val ACCESSIBILITY_TITLE: String = AccessibilityServiceDisclosure.TITLE
    const val CAMERA_TITLE = "Camera for codes"
    const val CAMERA_BODY =
        "The camera reads a ticket or pay QR. LipaBill does not save photos or video."
    val NOTIFICATIONS_TITLE: String = PermissionLesson.Notifications.title
    val NOTIFICATIONS_BODY: String = PermissionLesson.Notifications.body
    val HIBERNATION_TITLE: String = PermissionLesson.Hibernation.title
    val HIBERNATION_BODY: String = PermissionLesson.Hibernation.body

    fun smsBody(showRestrictedSettingsHelp: Boolean): String =
        SmsPermissionDisclosure.body(showRestrictedSettingsHelp)

    val ACCESSIBILITY_BODY: String = AccessibilityServiceDisclosure.STANDARD_BODY
}
