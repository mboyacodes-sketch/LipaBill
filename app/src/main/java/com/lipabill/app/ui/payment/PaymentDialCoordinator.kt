package com.lipabill.app.ui.payment

import android.content.Context
import android.widget.Toast
import androidx.navigation.NavHostController
import com.lipabill.app.BuildConfig
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.ui.navigation.Route
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.ussd.PendingPaymentReceipt
import com.lipabill.app.ussd.RepeatOutcome
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.UssdAutomationMask
import com.lipabill.app.viewmodel.PayMoneyViewModel
import com.lipabill.app.viewmodel.RepeatTransactionViewModel
import com.lipabill.app.viewmodel.SendMoneyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Prepares a payment, dials (or runs the debug walkthrough), then navigates to Processing.
 * Phone-permission coaching stays with the host via [requestPhoneConsent].
 */
class PaymentDialCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val navController: NavHostController,
    private val hasCallPermission: () -> Boolean,
    private val hasPhoneStatePermission: () -> Boolean,
    private val requestPhoneConsent: (onReady: () -> Unit) -> Unit,
    private val onPendingDialTxId: (Long?) -> Unit,
    private val dismissMoneySheets: () -> Unit,
    private val navigateHome: () -> Unit,
    private val finishAudit: suspend (auditId: Long, outcome: RepeatOutcome, detail: String) -> Unit
) {

    fun run(
        flow: String,
        prepare: suspend () -> RepeatTransactionCoordinator.PreparedRepeat?,
        startDial: (RepeatTransactionCoordinator.PreparedRepeat) -> Boolean,
        pendingId: Long? = null
    ) {
        val needCall = !hasCallPermission()
        val needState = !hasPhoneStatePermission()
        if (needCall || needState) {
            onPendingDialTxId(pendingId)
            requestPhoneConsent {
                if (hasCallPermission() && hasPhoneStatePermission()) {
                    run(flow, prepare, startDial, pendingId)
                }
            }
            return
        }
        scope.launch {
            val prepared = prepare()
            if (prepared == null) {
                AppMetrics.paymentAborted(flow, "prepare_failed")
                Toast.makeText(
                    context,
                    "Couldn’t start payment — check amount and recipient",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            AppMetrics.paymentStarted(flow)
            if (!startDial(prepared)) {
                Toast.makeText(
                    context,
                    "Payment didn’t start. Turn off other accessibility services, then try again.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            PendingPaymentReceipt.capture(prepared)
            dismissMoneySheets()
            navController.navigate(Route.Processing(prepared.auditId).path) {
                popUpTo(Route.List.path) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    fun runRepeat(repeatVm: RepeatTransactionViewModel) {
        run(
            flow = "repeat",
            prepare = { repeatVm.prepare() },
            startDial = { repeatVm.startDial(it) },
            pendingId = repeatVm.uiState.value.transaction?.id
        )
    }

    fun runSend(sendVm: SendMoneyViewModel) {
        if (BuildConfig.DEBUG) {
            runDemoWalkthrough(
                flow = "send",
                prepare = { sendVm.prepare() },
                summary = { sendVm.confirmSummary() }
            )
            return
        }
        run(
            flow = "send",
            prepare = { sendVm.prepare() },
            startDial = { sendVm.startDial(it) },
            pendingId = null
        )
    }

    fun runPay(payVm: PayMoneyViewModel) {
        if (BuildConfig.DEBUG) {
            runDemoWalkthrough(
                flow = "pay",
                prepare = { payVm.prepare() },
                summary = { payVm.confirmSummary() }
            )
            return
        }
        run(
            flow = "pay",
            prepare = { payVm.prepare() },
            startDial = { payVm.startDial(it) },
            pendingId = null
        )
    }

    /**
     * Debug / Maestro only. Prefers the real USSD mask when Accessibility is on;
     * otherwise the Compose fallback. Never dials *334# and never stores PIN digits.
     */
    fun runDemoWalkthrough(
        flow: String,
        prepare: suspend () -> RepeatTransactionCoordinator.PreparedRepeat?,
        summary: () -> Triple<String, String, String>?
    ) {
        scope.launch {
            val prepared = prepare()
            if (prepared == null) {
                AppMetrics.paymentAborted(flow, "prepare_failed")
                Toast.makeText(
                    context,
                    "Couldn’t start payment — check amount and recipient",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            AppMetrics.paymentStarted(flow)
            PendingPaymentReceipt.capture(prepared)
            dismissMoneySheets()
            val labels = summary()
            val amountLabel = labels?.third ?: formatKesMoney(prepared.amount)
            val detail = labels?.let { "${it.first} · ${it.second}" }
                ?: listOfNotNull(
                    prepared.transaction.counterpartyName,
                    prepared.transaction.counterpartyPhone
                ).joinToString(" · ").ifBlank { "Demo payment" }
            val auditId = prepared.auditId
            val maskSummary = "$amountLabel\n$detail\nDemo walkthrough — no USSD dial"
            val usedMask = UssdAutomationMask.startDebugWalkthrough(
                totalSteps = prepared.plan.actions.size.coerceAtLeast(1),
                summary = maskSummary,
                onComplete = {
                    scope.launch { finishDemoWalkthrough(auditId, cancelled = false) }
                },
                onCancel = {
                    scope.launch { finishDemoWalkthrough(auditId, cancelled = true) }
                }
            )
            if (!usedMask) {
                navController.navigate(
                    Route.DemoPayment(
                        auditId = auditId,
                        amountLabel = amountLabel,
                        detail = detail
                    ).path
                ) {
                    popUpTo(Route.List.path) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    suspend fun finishDemoWalkthrough(auditId: Long, cancelled: Boolean) {
        if (cancelled) {
            finishAudit(auditId, RepeatOutcome.USER_CANCELLED, "demo_cancelled")
            PendingPaymentReceipt.clear()
            navigateHome()
            return
        }
        val snap = PendingPaymentReceipt.peek(auditId)
        if (snap != null) {
            PendingPaymentReceipt.queueDemoHomeReveal(snap)
        }
        finishAudit(auditId, RepeatOutcome.COMPLETED_TO_PIN, "demo_walkthrough")
        navController.navigate(Route.Processing(auditId).path) {
            popUpTo(Route.List.path) { inclusive = false }
            launchSingleTop = true
        }
    }
}
