package com.lipabill.app.ui.pay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lipabill.app.R
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.parser.sanitizeAccountNumber
import com.lipabill.app.data.repository.MerchantHit
import com.lipabill.app.ui.permissions.paymentAccessDirective
import com.lipabill.app.ui.sheet.EditableSheetField
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.MoneyFlowConfirmDialog
import com.lipabill.app.ui.sheet.MoneyFlowSheetHeader
import com.lipabill.app.ui.sheet.MoneySheetScaffold
import com.lipabill.app.ui.sheet.SheetTextEditor
import com.lipabill.app.ui.sheet.expandedSheetDialog
import com.lipabill.app.ui.sheet.rememberMoneyFlowChrome
import com.lipabill.app.ui.sheet.rememberMoneySheetAccess
import com.lipabill.app.ui.sheet.rememberPhoneContactPicker
import com.lipabill.app.ui.sheet.rememberSheetEditor
import com.lipabill.app.ui.sheet.runMoneySheetPrimaryCta
import com.lipabill.app.ui.sheet.sameParty
import com.lipabill.app.ui.sheet.sheetName
import com.lipabill.app.ui.sheet.themedComposeView
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.theme.asPillLabel
import com.lipabill.app.ui.LipaChipHeight
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.formatMoneyInputLabel
import com.lipabill.app.ui.util.hideKeyboard
import com.lipabill.app.ui.util.opensOnDigitKeys
import com.lipabill.app.ui.util.sanitizeSheetLine
import com.lipabill.app.viewmodel.PayMethod
import com.lipabill.app.viewmodel.PayMoneyViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.WalletAccount

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
    val shownBalance by listVm.latestBalance.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    var focusedField by remember { mutableStateOf<PayFocusedField?>(null) }
    var amountFocused by remember { mutableStateOf(payVm.consumeResumeOnAmountStep()) }
    var fillNote by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val sheetAccess = rememberMoneySheetAccess { payVm.refreshGates() }
    val paymentBlock = sheetAccess.block
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val merchantEditor = rememberSheetEditor(state.merchantQuery)
    val accountEditor = rememberSheetEditor(state.accountNumber)
    val pochiEditor = rememberSheetEditor(state.pochiQuery)

    LaunchedEffect(Unit) {
        payVm.refreshGates()
        if (payVm.uiState.value.method == null) {
            payVm.selectMethod(PayMethod.PAYBILL)
        }
    }

    fun dismissTextKeyboard() {
        focusedField = null
        focusManager.clearFocus(force = true)
        hideKeyboard(focusManager, keyboard)
    }

    fun focusAmount() {
        dismissTextKeyboard()
        amountFocused = true
    }

    val pickPhoneContact = rememberPhoneContactPicker(
        onPicked = { contact ->
            payVm.selectPochiContact(contact)
            focusAmount()
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
        fillNote = "Paybill details filled from your paste"
        if (payVm.consumeResumeOnAmountStep()) {
            focusAmount()
        } else {
            dismissTextKeyboard()
        }
        return true
    }

    fun noteFromMerchant(hit: MerchantHit): String {
        val name = hit.paybillChipName()
        return when (hit.type) {
            TransactionType.PAYBILL ->
                "Paybill details filled from your last $name payment"
            else ->
                "Till details filled from your last $name payment"
        }
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
        if (focused) {
            amountFocused = false
            focusedField = field
        } else if (focusedField == field) {
            focusedField = null
        }
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

    val chrome = rememberMoneyFlowChrome(
        amountInput = state.amountInput,
        amountValid = state.amountValid,
        detailsValid = state.detailsValid,
        canConfirm = state.canPay,
        dialStarted = state.dialStarted,
        statusMessage = state.statusMessage,
        detailsGuidance = state.guidanceMessage,
        balance = shownBalance,
        paymentBlock = paymentBlock,
        blockedDirective = paymentBlock?.let { paymentAccessDirective(sheetAccess.missing) },
        confirmLabel = "Pay Now"
    )
    val pendingAmount = chrome.pendingAmount

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
        recipientsHeader = "Payment",
        showKeypad = false,
        showRecipients = true,
        showRecipientsHeader = false,
        showAmountKeypad = amountFocused,
        detailsHeader = { MoneyFlowSheetHeader(title = "Pay", balance = shownBalance) },
        recipientsContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.section)) {
                MethodSegmentedRow(
                    selected = state.method,
                    onSelect = {
                        dismissTextKeyboard()
                        amountFocused = false
                        fillNote = null
                        payVm.selectMethod(it)
                    }
                )
                when (state.method) {
                    PayMethod.PAYBILL, PayMethod.TILL -> {
                        if (state.merchantHits.isNotEmpty()) {
                            RecentChipSection(label = "Recent businesses") {
                                state.merchantHits.take(6).forEach { hit ->
                                    val selected = when (state.method) {
                                        PayMethod.PAYBILL -> hit.identifier == state.businessNumber
                                        PayMethod.TILL -> hit.identifier == state.tillNumber
                                        else -> false
                                    }
                                    PayRecentChip(
                                        label = if (state.method == PayMethod.PAYBILL) {
                                            hit.paybillChipName()
                                        } else {
                                            hit.displayName
                                        },
                                        selected = selected,
                                        onClick = {
                                            payVm.selectMerchant(hit)
                                            fillNote = noteFromMerchant(hit)
                                            if (state.method == PayMethod.TILL) {
                                                focusAmount()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    PayMethod.POCHI -> {
                        RecentChipSection(label = "Recent") {
                            PayRecentChip(
                                label = "Contacts",
                                selected = state.selectedPochi?.fromPhoneBook == true,
                                onClick = {
                                    fillNote = null
                                    pickPhoneContact()
                                }
                            )
                            state.pochiContacts.take(6).forEach { contact ->
                                PayRecentChip(
                                    label = contact.sheetName(),
                                    selected = contact.sameParty(state.selectedPochi),
                                    onClick = {
                                        payVm.selectPochiContact(contact)
                                        fillNote = null
                                        focusAmount()
                                    }
                                )
                            }
                        }
                    }
                    null -> Unit
                }
            }
        },
        extraAboveKeypad = {
            InterceptSystemIme {
                Column(verticalArrangement = Arrangement.spacedBy(Space.section)) {
                    when (state.method) {
                        PayMethod.PAYBILL -> {
                            PayLabeledField(label = "Business name or number") {
                                EditableSheetField(
                                    editor = merchantEditor,
                                    onText = {
                                        fillNote = null
                                        payVm.setMerchantQuery(it)
                                    },
                                    placeholder = "Business name or number",
                                    onBulkText = ::claimPaybillPaste,
                                    onFocused = onFieldFocus(PayFocusedField.Merchant)
                                )
                            }
                            PayLabeledField(label = "Account number") {
                                EditableSheetField(
                                    editor = accountEditor,
                                    onText = {
                                        fillNote = null
                                        payVm.setAccountNumber(it)
                                    },
                                    placeholder = "Account number",
                                    enabled = state.businessNumber.length >= 5,
                                    sanitize = ::sanitizeAccountNumber,
                                    onBulkText = ::claimPaybillPaste,
                                    onFocused = onFieldFocus(PayFocusedField.Account)
                                )
                            }
                        }
                        PayMethod.TILL -> {
                            PayLabeledField(label = "Business name or till number") {
                                EditableSheetField(
                                    editor = merchantEditor,
                                    onText = {
                                        fillNote = null
                                        payVm.setMerchantQuery(it)
                                    },
                                    placeholder = "Business name or till number",
                                    onFocused = onFieldFocus(PayFocusedField.Merchant)
                                )
                            }
                        }
                        PayMethod.POCHI -> {
                            PayLabeledField(label = "Name or phone number") {
                                EditableSheetField(
                                    editor = pochiEditor,
                                    onText = {
                                        fillNote = null
                                        payVm.setPochiQuery(it)
                                    },
                                    placeholder = "Name or phone number",
                                    onFocused = onFieldFocus(PayFocusedField.Pochi)
                                )
                            }
                        }
                        null -> Unit
                    }
                    fillNote?.let { note ->
                        PayFillNote(text = note)
                    }
                    PayAmountEntry(
                        amountInput = state.amountInput,
                        amount = pendingAmount,
                        balance = shownBalance,
                        focused = amountFocused,
                        onFocus = { focusAmount() }
                    )
                }
            }
        },
        inAppTextKeyboard = if (!amountFocused && focusedField != null) {
            {
                val field = focusedField
                val editor = editorFor(field)
                val sanitize = sanitizeFor(field)
                InAppKeyboard(
                    startOnDigits = (editor?.text ?: "").opensOnDigitKeys(),
                    onChar = { ch ->
                        if (editor != null && field != null) {
                            fillNote = null
                            pushField(field, editor.insert(ch, sanitize))
                        }
                    },
                    onBackspace = {
                        if (editor != null && field != null) {
                            fillNote = null
                            pushField(field, editor.backspace(sanitize))
                        }
                    },
                    onDone = {
                        dismissTextKeyboard()
                        if (state.detailsValid) focusAmount()
                    }
                )
            }
        } else {
            null
        },
        ctaLabel = chrome.primaryCta.label,
        ctaEnabled = chrome.primaryCta.enabled,
        onCta = {
            runMoneySheetPrimaryCta(
                onAmountStep = true,
                canConfirm = state.canPay,
                paymentBlock = paymentBlock,
                onConfirm = { showConfirm = true },
                onAllow = sheetAccess.allow,
                onContinue = { focusAmount() }
            )
        },
        onKey = payVm::appendAmountKey,
        onBackspace = payVm::deleteAmountKey,
        onApplyAddUpAmount = payVm::setAmountInput,
        onClearAmount = { payVm.setAmountInput("") },
        feedbackMessage = chrome.feedbackMessage,
        feedbackTone = chrome.feedbackTone,
        fulizaCoversShortfall = true,
        footnote = "You’ll enter your PIN on LipaBill’s secure keypad.",
        modifier = Modifier.fillMaxWidth()
    )

    sheetAccess.Dialog()

    MoneyFlowConfirmDialog(
        visible = showConfirm,
        canConfirm = state.canPay,
        confirmLabel = "Pay Now",
        summary = payVm.confirmSummary(),
        onDismiss = { showConfirm = false },
        onConfirm = {
            showConfirm = false
            onConfirm()
        }
    )
}

private val MethodTabs = listOf(PayMethod.TILL, PayMethod.PAYBILL, PayMethod.POCHI)

private val FillNoteFill = Color(0xFFE7F6EC)
private val FillNoteInk = Color(0xFF14532D)

@Composable
private fun MethodSegmentedRow(
    selected: PayMethod?,
    onSelect: (PayMethod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SoftBlue)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MethodTabs.forEach { method ->
            val on = method == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) Accent else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(method) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = method.shortLabel(),
                    style = HomeType.label,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) OnAccent else Accent
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentChipSection(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            style = HomeType.caption,
            fontWeight = FontWeight.SemiBold,
            color = Mute
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun PayRecentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .height(LipaChipHeight)
            .clip(shape)
            .background(if (selected) SoftBlue else CardWhite)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Accent else Hairline,
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = HomeType.label.asPillLabel(),
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Accent else Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PayLabeledField(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = HomeType.caption,
            fontWeight = FontWeight.SemiBold,
            color = Mute
        )
        content()
    }
}

@Composable
private fun PayFillNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FillNoteFill)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = null,
            tint = Income,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = HomeType.caption,
            color = FillNoteInk,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun PayMethod.shortLabel(): String = when (this) {
    PayMethod.PAYBILL -> "Paybill"
    PayMethod.TILL -> "Till"
    PayMethod.POCHI -> "Pochi"
}

private fun MerchantHit.paybillChipName(): String =
    if (displayName.isPaybillPlaceholder(identifier)) identifier else displayName

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
