package com.lipabill.app.ui.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.lipabill.app.ui.permissions.PaymentAccessNeed
import com.lipabill.app.viewmodel.RepeatTransactionViewModel

/** Shared amount / feedback / CTA wiring for Pay and Send sheets. */
data class MoneyFlowChrome(
    val pendingAmount: Double?,
    val feedbackMessage: String?,
    val feedbackTone: SheetFeedbackTone,
    val primaryCta: MoneySheetPrimaryCta
)

@Composable
fun rememberMoneyFlowChrome(
    amountInput: String,
    amountValid: Boolean,
    detailsValid: Boolean,
    canConfirm: Boolean,
    dialStarted: Boolean,
    statusMessage: String?,
    detailsGuidance: String?,
    balance: Double?,
    paymentBlock: PaymentAccessNeed?,
    blockedDirective: String?,
    confirmLabel: String,
    fulizaCoversShortfall: Boolean = true
): MoneyFlowChrome {
    val pendingAmount = remember(amountInput) {
        RepeatTransactionViewModel.parseAmount(amountInput)
    }
    return remember(
        amountInput,
        amountValid,
        detailsValid,
        canConfirm,
        dialStarted,
        statusMessage,
        detailsGuidance,
        balance,
        paymentBlock,
        blockedDirective,
        confirmLabel,
        fulizaCoversShortfall,
        pendingAmount
    ) {
        val exceedsBalance = amountExceedsBalance(balance, pendingAmount)
        val amountGuidance = amountStepGuidance(
            onAmountStep = true,
            exceedsBalance = exceedsBalance,
            amountInput = amountInput,
            amountValid = amountValid,
            detailsGuidance = null,
            fulizaCoversShortfall = fulizaCoversShortfall
        )
        val feedback = moneySheetFeedback(
            blockedDirective = blockedDirective,
            status = statusMessage,
            guidance = if (!detailsValid) detailsGuidance else amountGuidance
        )
        MoneyFlowChrome(
            pendingAmount = pendingAmount,
            feedbackMessage = feedback?.first,
            feedbackTone = feedback?.second ?: SheetFeedbackTone.Hint,
            primaryCta = moneySheetPrimaryCta(
                onAmountStep = true,
                canConfirm = canConfirm,
                dialStarted = dialStarted,
                detailsValid = detailsValid,
                paymentBlock = paymentBlock,
                confirmLabel = confirmLabel
            )
        )
    }
}

/**
 * Confirm dialog shared by Pay and Send.
 * [summary] is `(title, detail, amountLabel)` from the flow ViewModel.
 */
@Composable
fun MoneyFlowConfirmDialog(
    visible: Boolean,
    canConfirm: Boolean,
    confirmLabel: String,
    summary: Triple<String, String, String>?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!visible || !canConfirm || summary == null) return
    val (_, detail, amountLabel) = summary
    MoneyConfirmDialog(
        amountLabel = amountLabel,
        detail = detail,
        confirmLabel = confirmLabel,
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
