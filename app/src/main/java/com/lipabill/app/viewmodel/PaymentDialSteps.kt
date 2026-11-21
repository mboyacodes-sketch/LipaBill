package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.ussd.PaymentDialResolver
import com.lipabill.app.ussd.PaymentWallet
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.SimLine

/** Steps shared by the Send, Pay and Repeat sheets once the user has filled in who and how much. */

/**
 * SIM and wallet readiness every payment sheet shows. A sheet's state holds one
 * [PaymentLineSnapshot]; these read-throughs keep `state.featureEnabled`-style access working.
 */
interface PaymentLineState {
    val lines: PaymentLineSnapshot
    val featureEnabled: Boolean get() = lines.featureEnabled
    val accessibilityEnabled: Boolean get() = lines.accessibilityEnabled
    val simLines: List<SimLine> get() = lines.simLines
    val selectedSubscriptionId: Int? get() = lines.selectedSubscriptionId
    val hasSavedSimPreference: Boolean get() = lines.hasSavedSimPreference
    val needsPhoneStatePermission: Boolean get() = lines.needsPhoneStatePermission
    val region: AppRegion get() = lines.region
    val eligiblePayLines: List<SimLine> get() = lines.eligiblePayLines
    val activeWallet: PaymentWallet? get() = lines.activeWallet
    val hasEligiblePaySim: Boolean get() = lines.hasEligiblePaySim
    val missingPaySimMessage: String get() = lines.missingPaySimMessage
    val requiresExplicitLinePick: Boolean get() = lines.requiresExplicitLinePick
    val explicitLinePickMessage: String? get() = lines.explicitLinePickMessage
}

/** What a sheet shows before the first line check finishes. */
val InitialPaymentLines = PaymentLineSnapshot(
    featureEnabled = true,
    accessibilityEnabled = false,
    simLines = emptyList(),
    selectedSubscriptionId = null,
    hasSavedSimPreference = false,
    needsPhoneStatePermission = false,
    region = AppRegion.DEFAULT
)

/** Lines after a dial started: the line and wallet that actually ran it. */
internal fun PaymentLineSnapshot.after(started: DialStarted) =
    copy(selectedSubscriptionId = started.subscriptionId, activeWallet = started.wallet)

/** The line a dial actually started on, and the status line to show. */
internal data class DialStarted(
    val subscriptionId: Int,
    val wallet: PaymentWallet?,
    val status: String
)

/** Turns the sheet's draft into a dial session; null when the draft or amount isn't usable. */
internal suspend fun RepeatTransactionCoordinator.preparePayment(
    tx: MpesaTransaction?,
    amountInput: String
): RepeatTransactionCoordinator.PreparedRepeat? {
    val draft = tx ?: return null
    val amount = RepeatTransactionViewModel.parseAmount(amountInput) ?: return null
    return prepareSession(draft, amount)
}

/** Resolves the dialing line (chosen, else saved preference) and starts the USSD dial. */
internal fun RepeatTransactionCoordinator.startPaymentDial(
    prepared: RepeatTransactionCoordinator.PreparedRepeat,
    preferences: SecurePreferences,
    region: AppRegion,
    simLines: List<SimLine>,
    selectedSubscriptionId: Int?,
    preferredSource: MoneySource? = null
): DialStarted? {
    val enabled = preferences.enabledLedgerSources(region)
    val target = PaymentDialResolver.resolve(
        region = region,
        lines = simLines,
        preferredSubscriptionId = selectedSubscriptionId ?: preferences.preferredSimSubscriptionId,
        preferredSource = preferredSource,
        enabled = enabled
    ) ?: return null
    val status = startDialWithStatus(prepared, preferences, target.subscriptionId, simLines) ?: return null
    return DialStarted(target.subscriptionId, target.wallet, status)
}

/** A typed-in payment as a ledger-shaped draft (code `MANUAL-<kind>-<time>`, no balance yet). */
internal fun manualPayment(
    kind: String,
    type: TransactionType,
    amount: Double,
    name: String?,
    phone: String?,
    rawBody: String = "",
    id: Long = 0,
    now: Long = System.currentTimeMillis(),
    source: MoneySource = MoneySource.MPESA
) = MpesaTransaction(
    id = id,
    code = "MANUAL-$kind-$now",
    type = type,
    amount = amount,
    counterpartyName = name,
    counterpartyPhone = phone,
    timestampMillis = now,
    balance = null,
    cost = null,
    rawBody = rawBody,
    source = source
)
