package com.lipabill.app.ui.permissions

import android.Manifest
import android.content.Context
import com.lipabill.app.metrics.SetupBlocker
import com.lipabill.app.ussd.AccessibilityHelper

/**
 * Access Send and Pay cannot complete without.
 * A recipient number can be typed or chosen with Android's contact picker.
 */
enum class PaymentAccessNeed(val line: String, val actionLabel: String) {
    Phone(
        line = "Phone — place the payment call after you confirm",
        actionLabel = "Allow phone"
    ),
    PhoneState(
        line = "SIM access — see which line is Safaricom. Call history is not read",
        actionLabel = "Allow SIM access"
    ),
    Accessibility(
        line = "Accessibility Service — fill Safaricom USSD screens for Send, Pay, and Repeat " +
            "after you confirm the payment",
        actionLabel = AccessibilityServiceDisclosure.AGREE
    )
}

fun PaymentAccessNeed.toBlocker(): SetupBlocker? = when (this) {
    PaymentAccessNeed.Accessibility -> SetupBlocker.NoAccessibility
    PaymentAccessNeed.PhoneState -> SetupBlocker.NoSim
    PaymentAccessNeed.Phone -> null
}

fun Context.missingPaymentAccess(): List<PaymentAccessNeed> = buildList {
    if (!permissionGranted(Manifest.permission.CALL_PHONE)) add(PaymentAccessNeed.Phone)
    if (!permissionGranted(Manifest.permission.READ_PHONE_STATE)) add(PaymentAccessNeed.PhoneState)
    if (!AccessibilityHelper.isLipaBillServiceEnabled(this@missingPaymentAccess)) {
        add(PaymentAccessNeed.Accessibility)
    }
}

fun paymentAccessDirective(missing: List<PaymentAccessNeed>): String =
    missing.joinToString(separator = " ") { it.line }

fun consentPageFor(missing: List<PaymentAccessNeed>): FirstRunPage =
    if (missing.any { it == PaymentAccessNeed.Phone || it == PaymentAccessNeed.PhoneState }) {
        FirstRunPage.Phone
    } else {
        FirstRunPage.Accessibility
    }

fun FirstRunPage.toConsentRoute(): String = when (this) {
    FirstRunPage.Sms -> RouteConsent.SMS
    FirstRunPage.Phone -> RouteConsent.PHONE
    FirstRunPage.Accessibility -> RouteConsent.ACCESSIBILITY
    FirstRunPage.Camera -> RouteConsent.CAMERA
    FirstRunPage.Notifications -> RouteConsent.NOTIFICATIONS
    FirstRunPage.Hibernation -> RouteConsent.HIBERNATION
    FirstRunPage.Welcome,
    FirstRunPage.Region,
    FirstRunPage.Providers -> RouteConsent.SMS
}

/** String keys for [com.lipabill.app.ui.navigation.Route.Consent] without a Route import cycle. */
object RouteConsent {
    const val SMS = "sms"
    const val PHONE = "phone"
    const val ACCESSIBILITY = "accessibility"
    const val CAMERA = "camera"
    const val NOTIFICATIONS = "notifications"
    const val HIBERNATION = "hibernation"

    fun pageOf(raw: String?): FirstRunPage = when (raw) {
        PHONE -> FirstRunPage.Phone
        ACCESSIBILITY -> FirstRunPage.Accessibility
        CAMERA -> FirstRunPage.Camera
        NOTIFICATIONS -> FirstRunPage.Notifications
        HIBERNATION -> FirstRunPage.Hibernation
        else -> FirstRunPage.Sms
    }
}
