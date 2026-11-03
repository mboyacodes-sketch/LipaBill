package com.lipabill.app.ui.pay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lipabill.app.R
import com.lipabill.app.ui.LipaChoiceChip
import com.lipabill.app.ui.permissions.paymentAccessDirective
import com.lipabill.app.ui.sheet.EditableSheetField
import com.lipabill.app.ui.sheet.HorizontalRecipient
import com.lipabill.app.ui.sheet.HorizontalRecipientRow
import com.lipabill.app.ui.sheet.rememberPhoneContactPicker
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.SheetTextEditor
import com.lipabill.app.ui.sheet.rememberSheetEditor
import com.lipabill.app.ui.sheet.MoneyConfirmDialog
import com.lipabill.app.ui.sheet.MoneySheetScaffold
import com.lipabill.app.ui.sheet.moneySheetPrimaryCta
import com.lipabill.app.ui.sheet.rememberMoneySheetAccess
import com.lipabill.app.ui.sheet.runMoneySheetPrimaryCta
import com.lipabill.app.ui.sheet.sameParty
import com.lipabill.app.ui.sheet.sheetName
import com.lipabill.app.ui.sheet.sheetSubtitle
import com.lipabill.app.ui.sheet.SheetFeedbackTone
import com.lipabill.app.ui.sheet.amountExceedsBalance
import com.lipabill.app.ui.sheet.amountStepGuidance
import com.lipabill.app.ui.util.formatMoneyInputLabel
import com.lipabill.app.ui.sheet.moneySheetFeedback
import com.lipabill.app.ui.sheet.expandedSheetDialog
import com.lipabill.app.ui.sheet.themedComposeView
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.data.parser.sanitizeAccountNumber
import com.lipabill.app.data.repository.MerchantHit
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.opensOnDigitKeys
import com.lipabill.app.ui.util.hideKeyboard
import com.lipabill.app.ui.util.sanitizeSheetLine
import com.lipabill.app.viewmodel.PayMethod
import com.lipabill.app.viewmodel.WalletAccount
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

    override fun onCreateDialog(savedInstanceState: Bundle?) = expandedSheetDialog()

    override fun onDismiss(dialog: android.content.DialogInterface) {
        payVm.resetSession()
        super.onDismiss(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = themedComposeView {
        PayMoneySheetContent(
            payVm = payVm,
            listVm = listVm,
            onConfirm = {
                // Keep sheet state until dial starts — dismiss resets the VM.
                host?.onPaySheetConfirm()
            }
        )
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
    val sheetAccess = rememberMoneySheetAccess { payVm.refreshGates() }
    val paymentBlock = sheetAccess.block
    val pendingAmount = remember(state.amountInput) {
        RepeatTransactionViewModel.parseAmount(state.amountInput)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val merchantEditor = rememberSheetEditor(state.merchantQuery)
    val accountEditor = rememberSheetEditor(state.accountNumber)
    val pochiEditor = rememberSheetEditor(state.pochiQuery)

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

    val pickPhoneContact = rememberPhoneContactPicker(
        onPicked = { contact ->
            payVm.selectPochiContact(contact)
            dismissTextKeyboard()
            step = PaySheetStep.Amount
        },
        onRejected = { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    )

    /**
     * A copied payment message fills Paybill, account, and amount.
     * A normal paste stays in the field and can be edited.
     */
    fun claimPaybillPaste(raw: String): Boolean {
        if (!payVm.applyPaybillPaste(raw)) return false
        if (payVm.consumeResumeOnAmountStep()) {
            step = PaySheetStep.Amount
        }
        dismissTextKeyboard()
        Toast.makeText(context, "Paybill details filled", Toast.LENGTH_SHORT).show()
        return true
    }

    fun pushField(field: PayFocusedField, text: String) {
        when (field) {
            PayFocusedField.Merchant -> payVm.setMerchantQuery(text)
            PayFocusedField.Account -> payVm.setAccountNumber(text)
            PayFocusedField.Pochi -> payVm.setPochiQuery(text)
        }
    }

    fun editorFor(field: PayFocusedField?): SheetTextEditor? = when (field) {
        PayFocusedField.Merchant -> merchantEditor
        PayFocusedField.Account -> accountEditor
        PayFocusedField.Pochi -> pochiEditor
        null -> null
    }

    fun sanitizeFor(field: PayFocusedField?): (String) -> String =
        if (field == PayFocusedField.Account) ::sanitizeAccountNumber else ::sanitizeSheetLine

    fun onFieldFocus(field: PayFocusedField): (Boolean) -> Unit = { focused ->
        focusedField = if (focused) {
            field
        } else if (focusedField == field) {
            null
        } else {
            focusedField
        }
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

    val paybillNumber = state.businessNumber.takeIf { it.isNotBlank() }
    val paybillAccount = state.accountNumber.takeIf { it.isNotBlank() }
    val paybillName = paybillNumber?.let { number ->
        state.merchantHits.firstOrNull { it.identifier == number }
            ?.displayName
            ?.takeUnless { it.isPaybillPlaceholder(number) }
    }
    val selectedName = when (state.method) {
        PayMethod.PAYBILL -> paybillName ?: paybillNumber ?: state.merchantQuery.ifBlank { null }
        PayMethod.TILL -> state.merchantQuery.ifBlank { state.tillNumber }.ifBlank { null }
        PayMethod.POCHI -> state.selectedPochi?.let { it.name ?: it.normalizedPhone }
            ?: state.pochiQuery.ifBlank { null }
        null -> null
    }
    val selectedSubtitle = when (state.method) {
        PayMethod.PAYBILL -> when {
            paybillNumber == null -> null
            paybillName != null && paybillAccount != null -> "$paybillNumber · $paybillAccount"
            paybillAccount != null -> paybillAccount
            else -> ""
        }
        PayMethod.TILL -> state.tillNumber.takeIf { it.isNotBlank() }?.let { "Till $it" }
        PayMethod.POCHI -> state.selectedPochi?.normalizedPhone
            ?: state.pochiQuery.takeIf { it.any { c -> c.isDigit() } }
        null -> null
    }

    val onAmountStep = step == PaySheetStep.Amount
    val shownBalance = listState.latestBalance
    val exceedsBalance = amountExceedsBalance(shownBalance, pendingAmount)
    val feedback = moneySheetFeedback(
        blockedDirective = paymentBlock?.let { paymentAccessDirective(sheetAccess.missing) },
        status = state.statusMessage,
        guidance = amountStepGuidance(
            onAmountStep = onAmountStep,
            exceedsBalance = exceedsBalance,
            amountInput = state.amountInput,
            amountValid = state.amountValid,
            detailsGuidance = state.guidanceMessage,
            fulizaCoversShortfall = true
        )
    )
    val primaryCta = moneySheetPrimaryCta(
        onAmountStep = onAmountStep,
        canConfirm = state.canPay,
        dialStarted = state.dialStarted,
        detailsValid = state.detailsValid,
        paymentBlock = paymentBlock,
        confirmLabel = "Pay Now"
    )

    MoneySheetScaffold(
        balance = shownBalance,
        balanceCaption = WalletAccount.MPESA.balanceCaption,
        balancePrefix = WalletAccount.MPESA.balancePrefix,
        pendingDeduction = pendingAmount,
        selectedName = selectedName ?: "Choose merchant",
        selectedSubtitle = selectedSubtitle ?: state.method.labelOrPick(),
        amountDisplay = formatMoneyInputLabel(state.amountInput),
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
                                        name = if (state.method == PayMethod.PAYBILL) {
                                            hit.paybillChipName()
                                        } else {
                                            hit.displayName
                                        },
                                        subtitle = if (state.method == PayMethod.PAYBILL) {
                                            hit.paybillChipSubtitle()
                                        } else {
                                            hit.identifier
                                        },
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
                        HorizontalRecipientRow {
                            HorizontalRecipient(
                                name = "Contacts",
                                subtitle = "Phone",
                                selected = state.selectedPochi?.fromPhoneBook == true,
                                onClick = {
                                    dismissTextKeyboard()
                                    pickPhoneContact()
                                }
                            )
                            state.pochiContacts.take(6).forEach { contact ->
                                HorizontalRecipient(
                                    name = contact.sheetName(),
                                    subtitle = contact.sheetSubtitle(),
                                    selected = contact.sameParty(state.selectedPochi),
                                    onClick = {
                                        payVm.selectPochiContact(contact)
                                        dismissTextKeyboard()
                                        step = PaySheetStep.Amount
                                    }
                                )
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
                            EditableSheetField(
                                editor = merchantEditor,
                                onText = payVm::setMerchantQuery,
                                placeholder = "Business name or number",
                                onBulkText = ::claimPaybillPaste,
                                onFocused = onFieldFocus(PayFocusedField.Merchant)
                            )
                            Spacer(modifier = Modifier.height(Space.gap))
                            EditableSheetField(
                                editor = accountEditor,
                                onText = payVm::setAccountNumber,
                                placeholder = "Account / shop code",
                                enabled = state.businessNumber.length >= 5,
                                sanitize = ::sanitizeAccountNumber,
                                onBulkText = ::claimPaybillPaste,
                                onFocused = onFieldFocus(PayFocusedField.Account)
                            )
                        }
                        PayMethod.TILL -> {
                            EditableSheetField(
                                editor = merchantEditor,
                                onText = payVm::setMerchantQuery,
                                placeholder = "Business name or till number",
                                onFocused = onFieldFocus(PayFocusedField.Merchant)
                            )
                        }
                        PayMethod.POCHI -> {
                            EditableSheetField(
                                editor = pochiEditor,
                                onText = payVm::setPochiQuery,
                                placeholder = "Name or phone number",
                                onFocused = onFieldFocus(PayFocusedField.Pochi)
                            )
                        }
                        null -> Unit
                    }
                }
            }
        },
        inAppTextKeyboard = if (!onAmountStep && focusedField != null) {
            {
                val field = focusedField
                val editor = editorFor(field)
                val sanitize = sanitizeFor(field)
                InAppKeyboard(
                    startOnDigits = (editor?.text ?: "").opensOnDigitKeys(),
                    onChar = { ch ->
                        if (editor != null && field != null) {
                            pushField(field, editor.insert(ch, sanitize))
                        }
                    },
                    onBackspace = {
                        if (editor != null && field != null) {
                            pushField(field, editor.backspace(sanitize))
                        }
                    },
                    onDone = { dismissTextKeyboard() }
                )
            }
        } else {
            null
        },
        ctaLabel = primaryCta.label,
        ctaEnabled = primaryCta.enabled,
        onCta = {
            runMoneySheetPrimaryCta(
                onAmountStep = onAmountStep,
                canConfirm = state.canPay,
                paymentBlock = paymentBlock,
                onConfirm = { showConfirm = true },
                onAllow = sheetAccess.allow,
                onContinue = { goToAmount() }
            )
        },
        onKey = payVm::appendAmountKey,
        onBackspace = payVm::deleteAmountKey,
        onApplyAddUpAmount = payVm::setAmountInput,
        onClearAmount = { payVm.setAmountInput("") },
        feedbackMessage = feedback?.first,
        feedbackTone = feedback?.second ?: SheetFeedbackTone.Hint,
        fulizaCoversShortfall = true,
        modifier = Modifier.fillMaxWidth()
    )

    sheetAccess.Dialog()

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
            LipaChoiceChip(
                label = method.shortLabel(),
                selected = method == selected,
                onClick = { onSelect(method) },
                modifier = Modifier.weight(1f),
                unselectedContainer = CardWhite,
                unselectedBorder = Hairline
            )
        }
    }
}

private fun PayMethod.shortLabel(): String = when (this) {
    PayMethod.PAYBILL -> "Paybill"
    PayMethod.TILL -> "Till"
    PayMethod.POCHI -> "Pochi"
}

private fun MerchantHit.paybillChipName(): String =
    if (displayName.isPaybillPlaceholder(identifier)) identifier else displayName

private fun MerchantHit.paybillChipSubtitle(): String {
    val account = accountHint?.trim().orEmpty()
    return if (displayName.isPaybillPlaceholder(identifier)) {
        account
    } else if (account.isNotEmpty()) {
        "$identifier · $account"
    } else {
        identifier
    }
}

/** Saved rows named only “Paybill 123456” should show the number, not the word twice. */
private fun String.isPaybillPlaceholder(identifier: String): Boolean {
    val normalized = trim().lowercase()
    val number = identifier.trim()
    return normalized == number ||
        normalized == "paybill" ||
        normalized == "paybill $number" ||
        (normalized.startsWith("paybill ") && filter { it.isDigit() } == number)
}

private fun PayMethod?.labelOrPick(): String = when (this) {
    PayMethod.PAYBILL -> "Paybill"
    PayMethod.TILL -> "Buy Goods"
    PayMethod.POCHI -> "Pochi La Biashara"
    null -> "Pick a payment type"
}
