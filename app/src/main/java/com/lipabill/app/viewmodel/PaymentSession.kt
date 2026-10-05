package com.lipabill.app.viewmodel

import android.content.Context
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.SimLineHelper

/** SIM and accessibility gates shared by Send, Pay, and Repeat. */
data class PaymentLineSnapshot(
    val featureEnabled: Boolean,
    val accessibilityEnabled: Boolean,
    val simLines: List<SimLine>,
    val selectedSubscriptionId: Int?,
    val hasSavedSimPreference: Boolean,
    val needsPhoneStatePermission: Boolean
)

fun Context.paymentLineSnapshot(
    coordinator: RepeatTransactionCoordinator,
    preferences: SecurePreferences
): PaymentLineSnapshot {
    val needsPerm = !SimLineHelper.hasPhoneStatePermission(this)
    val lines = if (needsPerm) emptyList() else SimLineHelper.listActiveLines(this)
    val selected = if (needsPerm) {
        null
    } else {
        SimLineHelper.ensureSafaricomPreferred(preferences, lines)
    }
    val preferred = preferences.preferredSimSubscriptionId
    return PaymentLineSnapshot(
        featureEnabled = coordinator.isFeatureEnabled(),
        accessibilityEnabled = AccessibilityHelper.isLipaBillServiceEnabled(this),
        simLines = lines,
        selectedSubscriptionId = selected,
        hasSavedSimPreference = preferred >= 0 &&
            lines.any { line -> line.subscriptionId == preferred && line.isSafaricom },
        needsPhoneStatePermission = needsPerm
    )
}

/** Saves the chosen SIM, then the status line shown after dial starts. */
fun dialStartedStatus(
    preferences: SecurePreferences,
    subscriptionId: Int?,
    lines: List<SimLine>
): String {
    if (subscriptionId != null) {
        preferences.preferredSimSubscriptionId = subscriptionId
    }
    val line = lines.firstOrNull { it.subscriptionId == subscriptionId }
    return if (line != null) {
        "Payment on ${line.label} — enter PIN on the LipaBill keypad when prompted."
    } else {
        "Payment started — enter PIN on the LipaBill keypad when prompted."
    }
}
