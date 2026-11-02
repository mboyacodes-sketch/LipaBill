package com.lipabill.app.ui.sheet

import com.lipabill.app.ui.permissions.PaymentAccessNeed

data class MoneySheetPrimaryCta(
    val label: String,
    val enabled: Boolean
)

fun moneySheetPrimaryCta(
    onAmountStep: Boolean,
    canConfirm: Boolean,
    dialStarted: Boolean,
    detailsValid: Boolean,
    paymentBlock: PaymentAccessNeed?,
    confirmLabel: String
): MoneySheetPrimaryCta {
    val label = when {
        paymentBlock != null -> paymentBlock.actionLabel
        onAmountStep -> confirmLabel
        else -> "Continue"
    }
    val enabled = when {
        paymentBlock != null -> true
        onAmountStep -> canConfirm && !dialStarted
        else -> detailsValid
    }
    return MoneySheetPrimaryCta(label, enabled)
}

fun runMoneySheetPrimaryCta(
    onAmountStep: Boolean,
    canConfirm: Boolean,
    paymentBlock: PaymentAccessNeed?,
    onConfirm: () -> Unit,
    onAllow: (PaymentAccessNeed) -> Unit,
    onContinue: () -> Unit
) {
    when {
        paymentBlock != null -> onAllow(paymentBlock)
        onAmountStep && canConfirm -> onConfirm()
        onAmountStep -> onConfirm()
        else -> onContinue()
    }
}
