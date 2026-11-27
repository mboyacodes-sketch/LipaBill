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
    Hibernation,
    /** OEM battery / Never-sleeping — keep Accessibility bound after Device Care sleep. */
    OemSurvival
}

data class IntroSlide(val title: String, val body: String)

object FirstRunWizard {
    /**
     * First run: region, then SIM access (when Send/Pay exists) so Providers can match
     * wallets to the MNOs in the phone, then SMS and Accessibility.
     */
    fun firstRunPages(region: AppRegion = AppRegion.DEFAULT): List<FirstRunPage> = buildList {
        add(FirstRunPage.Welcome)
        add(FirstRunPage.Region)
        if (region.canSendPay) {
            add(FirstRunPage.Phone)
        }
        add(FirstRunPage.Providers)
        add(FirstRunPage.Sms)
        if (region.canSendPay) {
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
                it != FirstRunPage.Providers &&
                it != FirstRunPage.OemSurvival
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
        FirstRunPage.OemSurvival,
        FirstRunPage.Hibernation,
        FirstRunPage.Camera,
        FirstRunPage.Notifications -> listOf(start)
    }

    const val WELCOME_TITLE = "Your money.\nOne clear story."
    const val WELCOME_BODY =
        "Wallets and banks in one place, from the texts you already get. Then send, pay, or show a pass."

    /** "Kenya and Uganda" — every region where Send/Pay can dial. */
    private val SEND_PAY_COUNTRIES: String = AppRegion.entries.filter { it.canSendPay }
        .map { it.label }
        .let { names ->
            if (names.size <= 1) names.joinToString() else
                names.dropLast(1).joinToString(", ") + " and " + names.last()
        }

    /** Welcome is a three-slide intro; slide one carries [WELCOME_TITLE]. */
    val INTRO_SLIDES: List<IntroSlide> = listOf(
        IntroSlide(WELCOME_TITLE, WELCOME_BODY),
        IntroSlide(
            "Send money.\nSkip the stress.",
            "Send and pay in $SEND_PAY_COUNTRIES in a few taps, on the right SIM. " +
                "Your PIN is never stored."
        ),
        IntroSlide(
            "Small steps.\nBigger plans.",
            "Plan your money, keep your tickets handy, and see where it all goes."
        )
    )

    /** Sits where a login link would — LipaBill has no account to sign in to. */
    const val INTRO_FOOTER = "Encrypted and kept on this phone. Nothing is stored on LipaBill servers."

    /** Small caps label above each setup title. Permission asks can all be skipped. */
    fun eyebrow(page: FirstRunPage): String = when (page) {
        FirstRunPage.Welcome -> ""
        FirstRunPage.Region -> "Your starting point"
        FirstRunPage.Providers -> "Your accounts"
        FirstRunPage.OemSurvival -> "Stay ready"
        else -> "Optional"
    }

    /** "02 / 05" for setup pages; Welcome is not counted. */
    fun stepLabel(pages: List<FirstRunPage>, page: FirstRunPage): String {
        val steps = pages.filter { it != FirstRunPage.Welcome }
        val n = steps.indexOf(page) + 1
        return String.format(java.util.Locale.ROOT, "%02d / %02d", n, steps.size)
    }

    fun stepProgress(pages: List<FirstRunPage>, page: FirstRunPage): Float {
        val steps = pages.filter { it != FirstRunPage.Welcome }
        if (steps.isEmpty()) return 0f
        return (steps.indexOf(page) + 1).toFloat() / steps.size
    }

    const val REGION_TITLE = "Where is this phone?"
    const val REGION_BODY =
        "LipaBill tracks wallets and banks from that country. " +
            "We'll use this SIM when we can. You can change this in Settings."

    const val PROVIDERS_TITLE = "Which wallets and banks do you use?"
    const val PROVIDERS_BODY =
        "Wallets are matched to the SIMs in this phone. Add any banks you use. Pick up to 5; you can change this in Settings."

    const val PHONE_TITLE = "Phone for Send and Pay"
    const val PHONE_BODY =
        "LipaBill checks which SIMs are in this phone and which numbers they carry, " +
            "then dials the payments you confirm. Call history is not read, and your PIN is not stored."

    val SMS_TITLE: String = SmsPermissionDisclosure.TITLE
    val ACCESSIBILITY_TITLE: String = AccessibilityServiceDisclosure.TITLE
    const val CAMERA_TITLE = "Camera for codes"
    const val CAMERA_BODY =
        "The camera reads a ticket or pay QR. LipaBill does not save photos or video."
    val NOTIFICATIONS_TITLE: String = PermissionLesson.Notifications.title
    val NOTIFICATIONS_BODY: String = PermissionLesson.Notifications.body
    val HIBERNATION_TITLE: String = PermissionLesson.Hibernation.title
    val HIBERNATION_BODY: String = PermissionLesson.Hibernation.body
    val OEM_SURVIVAL_TITLE: String = PermissionLesson.OemSurvival.title
    val OEM_SURVIVAL_BODY: String get() = OemSurvivalHelper.coachBody()

    fun smsBody(showRestrictedSettingsHelp: Boolean): String =
        SmsPermissionDisclosure.body(showRestrictedSettingsHelp)

    val ACCESSIBILITY_BODY: String = AccessibilityServiceDisclosure.STANDARD_BODY
}
