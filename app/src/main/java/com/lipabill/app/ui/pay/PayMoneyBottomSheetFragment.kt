package com.lipabill.app.ui.pay

import android.app.Dialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lipabill.app.LipaBillApp
import com.lipabill.app.R
import com.lipabill.app.security.AppSecurity
import com.lipabill.app.ui.sheet.HorizontalRecipient
import com.lipabill.app.ui.sheet.HorizontalRecipientRow
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.MoneyConfirmDialog
import com.lipabill.app.ui.sheet.MoneySheetScaffold
import com.lipabill.app.ui.sheet.SheetInputStyle
import com.lipabill.app.ui.sheet.expandForComposeContent
import com.lipabill.app.ui.sheet.formatSheetAmount
import com.lipabill.app.ui.sheet.resolveSheetFeedback
import com.lipabill.app.ui.sheet.SheetFeedbackTone
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.LipaBillTheme
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.bringIntoViewOnFocus
import com.lipabill.app.ui.util.hideKeyboard
import com.lipabill.app.viewmodel.PayMethod
import com.lipabill.app.viewmodel.PayMoneyViewModel
import com.lipabill.app.viewmodel.RepeatTransactionViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel

private enum class PaySheetStep {
    Details,
    Amount
}

private enum class PayFocusedField {
    Merchant,
    Account,
    Pochi
}

class PayMoneyBottomSheetFragment : BottomSheetDialogFragment() {

    interface Host {
        fun onPaySheetConfirm()
    }

    private val payVm: PayMoneyViewModel by activityViewModels()
    private val listVm: TransactionListViewModel by activityViewModels()

    private val host: Host?
        get() = activity as? Host

    override fun getTheme(): Int = R.style.Theme_LipaBill_BottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        AppSecurity.lockScreenCapture(dialog)
        dialog.expandForComposeContent()
        return dialog
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        payVm.resetSession()
        super.onDismiss(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val app = requireActivity().application as LipaBillApp
        return ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val fontSize by app.fontSizeSp.collectAsStateWithLifecycle(initialValue = 12)
                LipaBillTheme(fontSizeSp = fontSize) {
                    PayMoneySheetContent(
                        payVm = payVm,
                        listVm = listVm,
                        onConfirm = {
                            // Keep sheet state until dial starts — dismiss resets the VM.
                            host?.onPaySheetConfirm()
                        }
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "pay_money_sheet"
        fun newInstance() = PayMoneyBottomSheetFragment()
    }
}

@Composable
fun PayMoneySheetContent(
    payVm: PayMoneyViewModel,
    listVm: TransactionListViewModel,
    onConfirm: () -> Unit
) {
    val state by payVm.uiState.collectAsStateWithLifecycle()
    val listState by listVm.uiState.collectAsStateWithLifecycle()
    var step by remember {
        mutableStateOf(
            if (payVm.consumeResumeOnAmountStep()) PaySheetStep.Amount else PaySheetStep.Details
        )
    }
    var showConfirm by remember { mutableStateOf(false) }
    var focusedField by remember { mutableStateOf<PayFocusedField?>(null) }
    val context = LocalContext.current
    val app = context.applicationContext as LipaBillApp
    val alwaysShowBalance by app.alwaysShowBalance.collectAsStateWithLifecycle()
    val pendingAmount = remember(state.amountInput) {
        RepeatTransactionViewModel.parseAmount(state.amountInput)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        payVm.refreshGates()
    }

    LaunchedEffect(step) {
        if (step == PaySheetStep.Amount) {
            focusedField = null
            hideKeyboard(focusManager, keyboard)
        }
    }

    fun dismissTextKeyboard() {
        focusedField = null
        focusManager.clearFocus(force = true)
        hideKeyboard(focusManager, keyboard)
    }

    fun goToAmount() {
        if (!state.detailsValid) return
        dismissTextKeyboard()
        step = PaySheetStep.Amount
    }

    fun goToDetails() {
        showConfirm = false
        payVm.setAmountInput("")
        step = PaySheetStep.Details
    }

    val selectedName = when (state.method) {
        PayMethod.PAYBILL -> state.merchantQuery.ifBlank { state.businessNumber }.ifBlank { null }
        PayMethod.TILL -> state.merchantQuery.ifBlank { state.tillNumber }.ifBlank { null }
        PayMethod.POCHI -> state.selectedPochi?.let { it.name ?: it.normalizedPhone }
            ?: state.pochiQuery.ifBlank { null }
        null -> null
    }
    val selectedSubtitle = when (state.method) {
        PayMethod.PAYBILL -> state.businessNumber.takeIf { it.isNotBlank() }?.let { "Paybill $it" }
        PayMethod.TILL -> state.tillNumber.takeIf { it.isNotBlank() }?.let { "Till $it" }
        PayMethod.POCHI -> state.selectedPochi?.normalizedPhone
            ?: state.pochiQuery.takeIf { it.any { c -> c.isDigit() } }
        null -> null
    }

    val onAmountStep = step == PaySheetStep.Amount
    val availableBalance = listState.latestBalance
    val exceedsBalance = availableBalance != null &&
        pendingAmount != null &&
        pendingAmount > 0.0 &&
        pendingAmount > availableBalance
    val feedback = resolveSheetFeedback(
        status = state.statusMessage,
        guidance = when {
            onAmountStep && exceedsBalance ->
                "That amount is more than your available balance."
            onAmountStep && state.amountInput.isBlank() ->
                "Enter an amount to continue."
            onAmountStep && !state.amountValid ->
                "Enter a valid amount to continue."
            onAmountStep -> null
            else -> state.guidanceMessage
        }
    )

    MoneySheetScaffold(
        balance = listState.latestBalance,
        alwaysShowBalance = alwaysShowBalance,
        pendingDeduction = pendingAmount,
        selectedName = selectedName ?: "Choose merchant",
        selectedSubtitle = selectedSubtitle ?: state.method.labelOrPick(),
        amountDisplay = formatSheetAmount(state.amountInput),
        amountInput = state.amountInput,
        selectedSelected = state.detailsValid,
        recipientsHeader = if (onAmountStep) "Change payment" else "Payment",
        onRecipientsHeaderClick = if (onAmountStep) {
            { goToDetails() }
        } else {
            null
        },
        showKeypad = onAmountStep,
        showRecipients = !onAmountStep,
        recipientsContent = {
            Column {
                MethodChipRow(
                    selected = state.method,
                    onSelect = {
                        dismissTextKeyboard()
                        payVm.selectMethod(it)
                        step = PaySheetStep.Details
                    }
                )
                Spacer(modifier = Modifier.height(Space.block))
                when (state.method) {
                    PayMethod.PAYBILL, PayMethod.TILL -> {
                        if (state.merchantHits.isNotEmpty()) {
                            HorizontalRecipientRow {
                                state.merchantHits.take(6).forEach { hit ->
                                    HorizontalRecipient(
                                        name = hit.displayName,
                                        subtitle = hit.identifier,
                                        selected = when (state.method) {
                                            PayMethod.PAYBILL -> hit.identifier == state.businessNumber
                                            PayMethod.TILL -> hit.identifier == state.tillNumber
                                            else -> false
                                        },
                                        onClick = {
                                            payVm.selectMerchant(hit)
                                            if (state.method == PayMethod.TILL) {
                                                dismissTextKeyboard()
                                                step = PaySheetStep.Amount
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    PayMethod.POCHI -> {
                        if (state.pochiContacts.isNotEmpty()) {
                            HorizontalRecipientRow {
                                state.pochiContacts.take(6).forEach { contact ->
                                    val name = contact.name ?: contact.normalizedPhone
                                    val subtitle = if (contact.fromPhoneBook) {
                                        "Phone · ${contact.normalizedPhone}"
                                    } else {
                                        contact.normalizedPhone
                                    }
                                    HorizontalRecipient(
                                        name = name,
                                        subtitle = subtitle,
                                        selected = contact.normalizedPhone ==
                                            state.selectedPochi?.normalizedPhone &&
                                            contact.fromPhoneBook == state.selectedPochi?.fromPhoneBook,
                                        onClick = {
                                            payVm.selectPochiContact(contact)
                                            dismissTextKeyboard()
                                            step = PaySheetStep.Amount
                                        }
                                    )
                                }
                            }
                        }
                    }
                    null -> Unit
                }
            }
        },
        extraAboveKeypad = if (onAmountStep) {
            null
        } else {
            {
                InterceptSystemIme {
                    when (state.method) {
                        PayMethod.PAYBILL -> {
                            val pastePaybillDetails: () -> Unit = {
                                val pasted = readClipboardText(context)
                                when {
                                    pasted.isNullOrBlank() ->
                                        Toast.makeText(
                                            context,
                                            "Copy a message with Paybill and Acc first",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    payVm.applyPaybillPaste(pasted) -> {
                                        dismissTextKeyboard()
                                        if (payVm.consumeResumeOnAmountStep()) {
                                            step = PaySheetStep.Amount
                                        }
                                        Toast.makeText(
                                            context,
                                            "Paybill details filled",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    else ->
                                        Toast.makeText(
                                            context,
                                            "Couldn’t find Paybill and Acc in the copied text",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                }
                            }
                            val merchantFocus = remember { FocusRequester() }
                            val accountFocus = remember { FocusRequester() }
                            Box {
                                OutlinedTextField(
                                    value = state.merchantQuery,
                                    onValueChange = payVm::setMerchantQuery,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(merchantFocus)
                                        .bringIntoViewOnFocus(delayMs = 80L)
                                        .onFocusChanged {
                                            focusedField = if (it.isFocused) {
                                                PayFocusedField.Merchant
                                            } else if (focusedField == PayFocusedField.Merchant) {
                                                null
                                            } else {
                                                focusedField
                                            }
                                        },
                                    singleLine = true,
                                    readOnly = true,
                                    textStyle = SheetInputStyle,
                                    shape = RoundedCornerShape(14.dp),
                                    placeholder = {
                                        Text(
                                            "Business name or number · long-press to paste",
                                            style = HomeType.body,
                                            color = Mute
                                        )
                                    }
                                )
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = { merchantFocus.requestFocus() },
                                                onLongPress = { pastePaybillDetails() }
                                            )
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.height(Space.gap))
                            Box {
                                OutlinedTextField(
                                    value = state.accountNumber,
                                    onValueChange = payVm::setAccountNumber,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(accountFocus)
                                        .bringIntoViewOnFocus(delayMs = 80L)
                                        .onFocusChanged {
                                            focusedField = if (it.isFocused) {
                                                PayFocusedField.Account
                                            } else if (focusedField == PayFocusedField.Account) {
                                                null
                                            } else {
                                                focusedField
                                            }
                                        },
                                    singleLine = true,
                                    readOnly = true,
                                    textStyle = SheetInputStyle,
                                    shape = RoundedCornerShape(14.dp),
                                    placeholder = {
                                        Text(
                                            "Account / shop code · long-press to paste",
                                            style = HomeType.body,
                                            color = Mute
                                        )
                                    },
                                    enabled = state.businessNumber.length >= 5
                                )
                                if (state.businessNumber.length >= 5) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = { accountFocus.requestFocus() },
                                                    onLongPress = { pastePaybillDetails() }
                                                )
                                            }
                                    )
                                }
                            }
                        }
                        PayMethod.TILL -> {
                            OutlinedTextField(
                                value = state.merchantQuery,
                                onValueChange = payVm::setMerchantQuery,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewOnFocus(delayMs = 80L)
                                    .onFocusChanged {
                                        focusedField = if (it.isFocused) {
                                            PayFocusedField.Merchant
                                        } else if (focusedField == PayFocusedField.Merchant) {
                                            null
                                        } else {
                                            focusedField
                                        }
                                    },
                                singleLine = true,
                                readOnly = true,
                                textStyle = SheetInputStyle,
                                shape = RoundedCornerShape(14.dp),
                                placeholder = {
                                    Text("Business name or till number", style = HomeType.body, color = Mute)
                                }
                            )
                        }
                        PayMethod.POCHI -> {
                            OutlinedTextField(
                                value = state.pochiQuery,
                                onValueChange = payVm::setPochiQuery,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewOnFocus(delayMs = 80L)
                                    .onFocusChanged {
                                        focusedField = if (it.isFocused) {
                                            PayFocusedField.Pochi
                                        } else if (focusedField == PayFocusedField.Pochi) {
                                            null
                                        } else {
                                            focusedField
                                        }
                                    },
                                singleLine = true,
                                readOnly = true,
                                textStyle = SheetInputStyle,
                                shape = RoundedCornerShape(14.dp),
                                placeholder = {
                                    Text("Name or phone number", style = HomeType.body, color = Mute)
                                }
                            )
                        }
                        null -> Unit
                    }
                }
            }
        },
        inAppTextKeyboard = if (!onAmountStep && focusedField != null) {
            {
                val activeValue = when (focusedField) {
                    PayFocusedField.Merchant -> state.merchantQuery
                    PayFocusedField.Account -> state.accountNumber
                    PayFocusedField.Pochi -> state.pochiQuery
                    null -> ""
                }
                InAppKeyboard(
                    startOnDigits = activeValue.any { it.isDigit() } &&
                        activeValue.none { it.isLetter() },
                    onChar = { ch ->
                        when (focusedField) {
                            PayFocusedField.Merchant ->
                                payVm.setMerchantQuery(state.merchantQuery + ch)
                            PayFocusedField.Account ->
                                payVm.setAccountNumber(state.accountNumber + ch)
                            PayFocusedField.Pochi ->
                                payVm.setPochiQuery(state.pochiQuery + ch)
                            null -> Unit
                        }
                    },
                    onBackspace = {
                        when (focusedField) {
                            PayFocusedField.Merchant ->
                                payVm.setMerchantQuery(state.merchantQuery.dropLast(1))
                            PayFocusedField.Account ->
                                payVm.setAccountNumber(state.accountNumber.dropLast(1))
                            PayFocusedField.Pochi ->
                                payVm.setPochiQuery(state.pochiQuery.dropLast(1))
                            null -> Unit
                        }
                    },
                    onDone = { dismissTextKeyboard() }
                )
            }
        } else {
            null
        },
        ctaLabel = if (onAmountStep) "Pay Now" else "Continue",
        ctaEnabled = if (onAmountStep) {
            state.canPay && !state.dialStarted
        } else {
            state.detailsValid
        },
        onCta = {
            if (onAmountStep) {
                showConfirm = true
            } else {
                goToAmount()
            }
        },
        onKey = payVm::appendAmountKey,
        onBackspace = payVm::deleteAmountKey,
        onApplyAddUpAmount = payVm::setAmountInput,
        onClearAmount = { payVm.setAmountInput("") },
        feedbackMessage = feedback?.first,
        feedbackTone = feedback?.second ?: SheetFeedbackTone.Hint,
        modifier = Modifier.fillMaxWidth()
    )

    if (showConfirm && state.canPay) {
        val summary = payVm.confirmSummary()
        if (summary != null) {
            val (_, detail, amountLabel) = summary
            MoneyConfirmDialog(
                amountLabel = amountLabel,
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
}

@Composable
private fun MethodChipRow(
    selected: PayMethod?,
    onSelect: (PayMethod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PayMethod.entries.forEach { method ->
            val isSelected = method == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) Accent else CardWhite)
                    .border(1.dp, if (isSelected) Accent else Hairline, RoundedCornerShape(20.dp))
                    .clickable { onSelect(method) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = method.shortLabel(),
                    style = HomeType.label,
                    color = if (isSelected) CardWhite else Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun PayMethod.shortLabel(): String = when (this) {
    PayMethod.PAYBILL -> "Paybill"
    PayMethod.TILL -> "Till"
    PayMethod.POCHI -> "Pochi"
}

private fun PayMethod?.labelOrPick(): String = when (this) {
    PayMethod.PAYBILL -> "Paybill"
    PayMethod.TILL -> "Buy Goods"
    PayMethod.POCHI -> "Pochi La Biashara"
    null -> "Pick a payment type"
}

private fun readClipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
