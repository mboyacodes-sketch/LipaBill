package com.lipabill.app.ui.repeat

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as activityViewModel
import com.lipabill.app.LipaBillApp
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.SetupBlocker
import com.lipabill.app.ui.sheet.MoneyConfirmDialog
import com.lipabill.app.ui.sheet.MoneySheetScaffold
import com.lipabill.app.ui.sheet.SheetFeedbackTone
import com.lipabill.app.ui.sheet.amountExceedsBalance
import com.lipabill.app.ui.sheet.amountStepGuidance
import com.lipabill.app.ui.sheet.formatSheetAmount
import com.lipabill.app.ui.sheet.moneySheetFeedback
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.viewmodel.RepeatTransactionViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel

private fun android.content.Context.findComponentActivity(): ComponentActivity {
    var current = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    error("Repeat amount screen is not hosted by an activity")
}

@Composable
fun RepeatConfirmScreen(
    viewModel: RepeatTransactionViewModel,
    onBack: () -> Unit,
    onNeedAccessibility: () -> Unit,
    onManualFallback: () -> Unit,
    onOpenSimSettings: () -> Unit,
    onRequestPhoneStatePermission: () -> Unit,
    onConfirm: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findComponentActivity()
    val listVm: TransactionListViewModel = activityViewModel(viewModelStoreOwner = activity)
    val listState by listVm.uiState.collectAsStateWithLifecycle()
    val app = activity.application as LipaBillApp
    val alwaysShowBalance by app.alwaysShowBalance.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }

    val tx = state.transaction
    val needsSimSetup = state.simLines.size > 1 && !state.hasSavedSimPreference
    val missingSafaricom = !state.needsPhoneStatePermission &&
        state.simLines.isNotEmpty() &&
        state.simLines.none { it.isSafaricom }
    val pendingAmount = remember(state.amountInput) {
        RepeatTransactionViewModel.parseAmount(state.amountInput)
    }
    val shownBalance = listState.shownBalance()
    val exceedsBalance = amountExceedsBalance(shownBalance, pendingAmount)
    val setupAction = when {
        state.needsPhoneStatePermission -> "Allow"
        missingSafaricom -> null
        needsSimSetup -> "Set SIM"
        !state.featureEnabled -> "Copy details"
        !state.accessibilityEnabled -> "Enable"
        else -> null
    }
    val guidance = when {
        tx == null -> "Transaction not found."
        !state.canPay ->
            "This payment cannot be started automatically."
        state.needsPhoneStatePermission ->
            "Allow phone access to use your default SIM."
        missingSafaricom ->
            "A Safaricom SIM is required to dial M-Pesa."
        needsSimSetup ->
            "Set your default M-Pesa SIM in Settings first."
        !state.featureEnabled ->
            "Payment automation is off. Copy the details instead."
        !state.accessibilityEnabled ->
            "Enable Accessibility for LipaBill to fill the M-Pesa screens."
        else -> amountStepGuidance(
            onAmountStep = true,
            exceedsBalance = exceedsBalance,
            amountInput = state.amountInput,
            amountValid = state.amountValid,
            detailsGuidance = null
        )
    }
    val feedback = moneySheetFeedback(
        blockedDirective = null,
        status = state.statusMessage,
        guidance = guidance
    )
    LaunchedEffect(missingSafaricom) {
        if (missingSafaricom) AppMetrics.setupBlocked(SetupBlocker.NoSafaricom)
    }
    val recipient = tx?.counterpartyName?.takeIf { it.isNotBlank() } ?: "Recipient"
    val subtitle = tx?.counterpartyPhone?.takeIf { it.isNotBlank() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        MoneySheetScaffold(
            balance = shownBalance,
            balanceCaption = listState.account.balanceCaption,
            balancePrefix = listState.account.balancePrefix,
            alwaysShowBalance = alwaysShowBalance,
            pendingDeduction = pendingAmount,
            selectedName = recipient,
            selectedSubtitle = subtitle,
            amountDisplay = formatSheetAmount(state.amountInput),
            amountInput = state.amountInput,
            selectedSelected = tx != null,
            recipientsHeader = "Back",
            onRecipientsHeaderClick = onBack,
            showKeypad = true,
            showRecipients = false,
            recipientsContent = {},
            ctaLabel = setupAction ?: "Pay Now",
            ctaEnabled = !state.dialStarted && !missingSafaricom && tx != null && (
                setupAction != null || (state.amountValid && state.plan != null && state.canPay)
                ),
            onCta = {
                when {
                    state.needsPhoneStatePermission -> {
                        AppMetrics.setupBlocked(SetupBlocker.NoSim)
                        onRequestPhoneStatePermission()
                    }
                    needsSimSetup -> {
                        AppMetrics.setupBlocked(SetupBlocker.NoSim)
                        onOpenSimSettings()
                    }
                    !state.featureEnabled -> onManualFallback()
                    !state.accessibilityEnabled -> {
                        AppMetrics.setupBlocked(SetupBlocker.NoAccessibility)
                        onNeedAccessibility()
                    }
                    else -> showConfirm = true
                }
            },
            onKey = viewModel::appendAmountKey,
            onBackspace = viewModel::deleteAmountKey,
            onApplyAddUpAmount = viewModel::setAmountInput,
            onClearAmount = { viewModel.setAmountInput("") },
            feedbackMessage = feedback?.first,
            feedbackTone = feedback?.second ?: SheetFeedbackTone.Hint
        )
    }

    if (showConfirm && tx != null && pendingAmount != null) {
        val detail = listOfNotNull(tx.counterpartyName, tx.counterpartyPhone)
            .joinToString(" · ")
            .ifBlank { "Recipient" }
        MoneyConfirmDialog(
            amountLabel = formatKesMoney(pendingAmount),
            detail = detail,
            confirmLabel = "Pay Now",
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                onConfirm()
            }
        )
    }
}
