package com.lipabill.app.viewmodel

import android.content.Context
import com.lipabill.app.data.model.ActiveRegion
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.PaymentDialResolver
import com.lipabill.app.ussd.PaymentDialTarget
import com.lipabill.app.ussd.PaymentWallet
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.SimLineHelper

/**
 * Starts the USSD dial and returns the status line to show, or null if dialing failed.
 */
fun RepeatTransactionCoordinator.startDialWithStatus(
    prepared: RepeatTransactionCoordinator.PreparedRepeat,
    preferences: SecurePreferences,
    subscriptionId: Int?,
    lines: List<SimLine>
): String? {
    val status = dialStartedStatus(preferences, subscriptionId, lines)
    if (!startDialing(prepared, subscriptionId)) return null
    return status
}

/** SIM and accessibility gates shared by Send, Pay, and Repeat. */
data class PaymentLineSnapshot(
    val featureEnabled: Boolean,
    val accessibilityEnabled: Boolean,
    val simLines: List<SimLine>,
    val selectedSubscriptionId: Int?,
    val hasSavedSimPreference: Boolean,
    val needsPhoneStatePermission: Boolean,
    val region: AppRegion = ActiveRegion.current,
    val hasEligiblePaySim: Boolean = false,
    val missingPaySimMessage: String = "",
    /** On-device MNO SIMs that can dial Send/Pay for [region]. Banks never appear. */
    val eligiblePayLines: List<SimLine> = emptyList(),
    val activeWallet: PaymentWallet? = null,
    /** Short home / sheet caption, e.g. "Pay with M-Pesa". */
    val payWalletCaption: String? = null,
    /**
     * Same-MNO dual-SIM: preference alone is unsafe — Confirm waits until the
     * sheet session picks a line via [setPreferredPayLine] / selectPayLine.
     */
    val requiresExplicitLinePick: Boolean = false,
    val explicitLinePickMessage: String? = null
)

fun Context.paymentLineSnapshot(
    coordinator: RepeatTransactionCoordinator,
    preferences: SecurePreferences
): PaymentLineSnapshot {
    val region = preferences.appRegion
    val enabled = preferences.enabledLedgerSources(region)
    val needsPerm = !SimLineHelper.hasPhoneStatePermission(this)
    val lines = if (needsPerm) emptyList() else SimLineHelper.listActiveLines(this, region)
    val selected = if (needsPerm) {
        null
    } else {
        SimLineHelper.ensurePayPreferred(region, preferences, lines, enabled)
    }
    val preferred = preferences.preferredSimSubscriptionId
    val eligible = PaymentDialResolver.eligibleLines(region, lines, enabled)
    val requiresPick = !needsPerm &&
        PaymentDialResolver.requiresExplicitLinePick(region, lines, enabled)
    val target = if (needsPerm || eligible.isEmpty()) {
        null
    } else {
        PaymentDialResolver.resolve(
            region = region,
            lines = lines,
            preferredSubscriptionId = selected ?: preferred,
            enabled = enabled
        )
    }
    return PaymentLineSnapshot(
        featureEnabled = coordinator.isFeatureEnabled(),
        accessibilityEnabled = AccessibilityHelper.isLipaBillServiceEnabled(this),
        simLines = lines,
        selectedSubscriptionId = target?.subscriptionId ?: selected,
        hasSavedSimPreference = preferred >= 0 &&
            eligible.any { line -> line.subscriptionId == preferred },
        needsPhoneStatePermission = needsPerm,
        region = region,
        hasEligiblePaySim = eligible.isNotEmpty(),
        missingPaySimMessage = PaymentDialResolver.missingPaySimMessage(region),
        eligiblePayLines = eligible,
        activeWallet = target?.wallet,
        payWalletCaption = target?.let { "Send & Pay with ${it.wallet.badge}" },
        requiresExplicitLinePick = requiresPick,
        explicitLinePickMessage = if (requiresPick) {
            PaymentDialResolver.explicitLinePickMessage(target?.wallet)
        } else {
            null
        }
    )
}

/**
 * Persist an on-device eligible MNO SIM as the dialer primary.
 * Ignores subscription ids that are not pay-eligible for [region].
 */
fun setPreferredPayLine(
    preferences: SecurePreferences,
    region: AppRegion,
    lines: List<SimLine>,
    subscriptionId: Int
): PaymentDialTarget? {
    val enabled = preferences.enabledLedgerSources(region)
    val eligible = PaymentDialResolver.eligibleLines(region, lines, enabled)
    if (eligible.none { it.subscriptionId == subscriptionId }) return null
    preferences.preferredSimSubscriptionId = subscriptionId
    return PaymentDialResolver.resolve(region, lines, subscriptionId, enabled = enabled)
}

/** Home / sheets: resolve current dial target without a coordinator. */
fun Context.activePayDialTarget(preferences: SecurePreferences): PaymentDialTarget? {
    if (!SimLineHelper.hasPhoneStatePermission(this)) return null
    val region = preferences.appRegion
    val enabled = preferences.enabledLedgerSources(region)
    val lines = SimLineHelper.listActiveLines(this, region)
    val preferred = SimLineHelper.ensurePayPreferred(region, preferences, lines, enabled)
        ?: return null
    return PaymentDialResolver.resolve(region, lines, preferred, enabled = enabled)
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

/** One-shot flag so Send/Pay reopen on the amount step after PIN cancel. */
class AmountStepResume {
    private var pending: Boolean = false

    fun mark() {
        pending = true
    }

    fun clear() {
        pending = false
    }

    fun consume(): Boolean {
        val value = pending
        pending = false
        return value
    }
}
