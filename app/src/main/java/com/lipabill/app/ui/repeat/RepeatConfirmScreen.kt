package com.lipabill.app.ui.repeat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as activityViewModel
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.parser.extractAccount
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.SetupBlocker
import com.lipabill.app.ui.payment.PayWalletPicker
import com.lipabill.app.ui.permissions.AccessibilityServiceDisclosure
import com.lipabill.app.ui.sheet.MoneyConfirmDialog
import com.lipabill.app.ui.sheet.MoneySheetScaffold
import com.lipabill.app.ui.sheet.SheetFeedbackTone
import com.lipabill.app.ui.sheet.amountExceedsBalance
import com.lipabill.app.ui.sheet.amountStepGuidance
import com.lipabill.app.ui.sheet.moneySheetFeedback
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.util.findComponentActivity
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.ui.util.formatMoneyInputLabel
import com.lipabill.app.viewmodel.RepeatTransactionViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.sheetBalance
import com.lipabill.app.viewmodel.WalletAccount

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
        ?: error("Repeat amount screen is not hosted by an activity")
    val listVm: TransactionListViewModel = activityViewModel(viewModelStoreOwner = activity)
    val homeBalance by listVm.latestBalance.collectAsStateWithLifecycle()
    val walletBalances by listVm.walletBalances.collectAsStateWithLifecycle()
    // The balance of the wallet this sheet will dial (e.g. Airtel on a dual-SIM phone).
    val shownBalance = sheetBalance(state.activeWallet, walletBalances, homeBalance)
    val app = activity.application as LipaBillApp
    var showConfirm by remember { mutableStateOf(false) }

    val tx = state.transaction
    val needsSimSetup = state.simLines.count { it.isPayEligible(state.region) } > 1 &&
        !state.hasSavedSimPreference
    val missingPaySim = !state.needsPhoneStatePermission &&
        state.simLines.isNotEmpty() &&
        !state.hasEligiblePaySim
    val pendingAmount = remember(state.amountInput) {
        RepeatTransactionViewModel.parseAmount(state.amountInput)
    }
    val exceedsBalance = amountExceedsBalance(shownBalance, pendingAmount)
    val setupAction = when {
        state.needsPhoneStatePermission -> "Allow"
        missingPaySim -> null
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
        missingPaySim ->
            state.missingPaySimMessage.ifBlank {
                "A matching SIM for ${state.region.label} is required to send."
            }
        needsSimSetup ->
            "Set your default payment SIM in Settings first."
        !state.featureEnabled ->
            "Automatic repeat is off in Settings. Copy the details instead."
        !state.accessibilityEnabled ->
            "Turn on “${AccessibilityServiceDisclosure.SERVICE_LABEL}” to Repeat. " +
                "History stays available."
        else -> amountStepGuidance(
            onAmountStep = true,
            exceedsBalance = exceedsBalance,
            amountInput = state.amountInput,
            amountValid = state.amountValid,
            detailsGuidance = null,
            fulizaCoversShortfall = true
        )
    }
    val feedback = moneySheetFeedback(
        blockedDirective = null,
        status = state.statusMessage,
        guidance = guidance
    )
    LaunchedEffect(missingPaySim) {
        if (missingPaySim) AppMetrics.setupBlocked(SetupBlocker.NoSafaricom)
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
            balanceCaption = WalletAccount.MPESA.balanceCaption,
            balancePrefix = WalletAccount.MPESA.balancePrefix,
            pendingDeduction = pendingAmount,
            selectedName = recipient,
            selectedSubtitle = subtitle,
            amountDisplay = formatMoneyInputLabel(state.amountInput),
            amountInput = state.amountInput,
            selectedSelected = tx != null,
            recipientsHeader = "Back",
            onRecipientsHeaderClick = onBack,
            showKeypad = true,
            showRecipients = false,
            recipientsContent = {},
            detailsHeader = {
                if (!state.needsPhoneStatePermission) {
                    PayWalletPicker(
                        region = state.region,
                        eligibleLines = state.eligiblePayLines,
                        selectedSubscriptionId = state.selectedSubscriptionId,
                        activeWallet = state.activeWallet,
                        missingMessage = state.missingPaySimMessage,
                        onSelect = viewModel::selectPayLine,
                        balances = walletBalances
                    )
                }
            },
            ctaLabel = setupAction ?: "Pay Now",
            ctaEnabled = !state.dialStarted && !missingPaySim && tx != null && (
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
            feedbackTone = feedback?.second ?: SheetFeedbackTone.Hint,
            fulizaCoversShortfall = true
        )
    }

    if (showConfirm && tx != null && pendingAmount != null) {
        val account = extractAccount(tx.rawBody)
        val detail = listOfNotNull(
            tx.counterpartyName?.takeIf { it.isNotBlank() },
            tx.counterpartyPhone?.takeIf { it.isNotBlank() },
            account?.let { "Acc $it" }
        ).joinToString(" · ").ifBlank { "Recipient" }
        MoneyConfirmDialog(
            amountLabel = formatKesMoney(pendingAmount),
            detail = detail,
            confirmLabel = "Pay Now",
            from = state.activeWallet,
            onDismiss = { showConfirm = false },
            onConfirm = {
                showConfirm = false
                onConfirm()
            }
        )
    }
}
