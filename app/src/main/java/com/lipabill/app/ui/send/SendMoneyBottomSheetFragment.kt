package com.lipabill.app.ui.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lipabill.app.R
import com.lipabill.app.ui.permissions.paymentAccessDirective
import com.lipabill.app.ui.sheet.EditableSheetField
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.MoneyFlowConfirmDialog
import com.lipabill.app.ui.sheet.MoneySheetScaffold
import com.lipabill.app.ui.sheet.expandedSheetDialog
import com.lipabill.app.ui.sheet.rememberMoneyFlowChrome
import com.lipabill.app.ui.sheet.rememberMoneySheetAccess
import com.lipabill.app.ui.sheet.rememberPhoneContactPicker
import com.lipabill.app.ui.sheet.rememberSheetEditor
import com.lipabill.app.ui.sheet.runMoneySheetPrimaryCta
import com.lipabill.app.ui.sheet.sheetName
import com.lipabill.app.ui.sheet.themedComposeView
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.formatMoneyInputLabel
import com.lipabill.app.ui.util.hideKeyboard
import com.lipabill.app.ui.util.opensOnDigitKeys
import com.lipabill.app.ui.util.sanitizeSheetLine
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.viewmodel.SendMoneyViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.WalletAccount
import com.lipabill.app.viewmodel.phoneKey

class SendMoneyBottomSheetFragment : BottomSheetDialogFragment() {

    interface Host {
        fun onSendSheetConfirm()
    }

    private val sendVm: SendMoneyViewModel by activityViewModels()
    private val listVm: TransactionListViewModel by activityViewModels()

    private val host: Host?
        get() = activity as? Host

    override fun getTheme(): Int = R.style.Theme_LipaBill_BottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?) = expandedSheetDialog()

    override fun onDismiss(dialog: android.content.DialogInterface) {
        sendVm.resetSession()
        super.onDismiss(dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = themedComposeView {
        SendMoneySheetContent(
            sendVm = sendVm,
            listVm = listVm,
            onConfirm = {
                // Keep sheet state until dial starts — dismiss resets the VM.
                host?.onSendSheetConfirm()
            }
        )
    }

    companion object {
        const val TAG = "send_money_sheet"
        fun newInstance() = SendMoneyBottomSheetFragment()
    }
}

@Composable
fun SendMoneySheetContent(
    sendVm: SendMoneyViewModel,
    listVm: TransactionListViewModel,
    onConfirm: () -> Unit
) {
    val state by sendVm.uiState.collectAsStateWithLifecycle()
    val shownBalance by listVm.latestBalance.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    var queryFocused by remember { mutableStateOf(false) }
    var editingRecipient by remember {
        mutableStateOf(sendVm.uiState.value.selected == null && !sendVm.uiState.value.detailsValid)
    }
    var amountFocused by remember {
        mutableStateOf(sendVm.consumeResumeOnAmountStep())
    }
    val sheetAccess = rememberMoneySheetAccess { sendVm.refreshGates() }
    val paymentBlock = sheetAccess.block
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val recipientEditor = rememberSheetEditor(state.query)

    fun dismissTextKeyboard() {
        queryFocused = false
        focusManager.clearFocus(force = true)
        hideKeyboard(focusManager, keyboard)
    }

    fun focusAmount() {
        dismissTextKeyboard()
        editingRecipient = false
        amountFocused = true
    }

    fun editRecipient() {
        amountFocused = false
        editingRecipient = true
        sendVm.setAmountInput("")
        queryFocused = true
    }

    val pickPhoneContact = rememberPhoneContactPicker(
        onPicked = { contact ->
            sendVm.select(contact)
            focusAmount()
        },
        onRejected = { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    )

    LaunchedEffect(Unit) {
        sendVm.refreshGates()
        if (state.detailsValid) {
            editingRecipient = false
            if (amountFocused || state.amountInput.isNotBlank()) {
                focusAmount()
            }
        } else {
            editingRecipient = true
        }
    }

    LaunchedEffect(state.detailsValid) {
        if (state.detailsValid && editingRecipient && !queryFocused) {
            editingRecipient = false
        }
    }

    val selectedName = state.selected?.let { it.name ?: it.normalizedPhone }
        ?: UssdMenuBuilder.normalizePhoneNumber(state.query)
        ?: state.query.trim().takeIf { it.isNotEmpty() }
    val phone = state.selected?.normalizedPhone
        ?: UssdMenuBuilder.normalizePhoneNumber(state.query)

    val chrome = rememberMoneyFlowChrome(
        amountInput = state.amountInput,
        amountValid = state.amountValid,
        detailsValid = state.detailsValid,
        canConfirm = state.canSend,
        dialStarted = state.dialStarted,
        statusMessage = state.statusMessage,
        detailsGuidance = state.guidanceMessage,
        balance = shownBalance,
        paymentBlock = paymentBlock,
        blockedDirective = paymentBlock?.let { paymentAccessDirective(sheetAccess.missing) },
        confirmLabel = "Send Now"
    )
    val pendingAmount = chrome.pendingAmount
    val showRecipientEditor = editingRecipient || !state.detailsValid
    val matchContacts = remember(state.query, state.contacts, showRecipientEditor) {
        if (showRecipientEditor && state.query.isNotBlank()) {
            state.contacts.filterNot { it.fromPhoneBook }.take(5)
        } else {
            emptyList()
        }
    }
    val searchMatches = remember(matchContacts, state.monthlyCounts) {
        matchContacts.map { contact ->
            SendSearchMatch(
                id = contact.transactionId,
                name = contact.sheetName(),
                phone = contact.normalizedPhone,
                sentThisMonth = phoneKey(contact.normalizedPhone)
                    ?.let { state.monthlyCounts[it] } ?: 0
            )
        }
    }
    val density = LocalDensity.current
    var searchFieldHeightPx by remember { mutableIntStateOf(0) }
    val searchFieldHeight = with(density) { searchFieldHeightPx.toDp() }

    MoneySheetScaffold(
        balance = shownBalance,
        balanceCaption = WalletAccount.MPESA.balanceCaption,
        balancePrefix = WalletAccount.MPESA.balancePrefix,
        pendingDeduction = pendingAmount,
        selectedName = selectedName,
        selectedSubtitle = phone?.let { "@$it" },
        amountDisplay = formatMoneyInputLabel(state.amountInput),
        amountInput = state.amountInput,
        selectedSelected = state.detailsValid,
        recipientsHeader = "Recipient",
        showKeypad = false,
        showRecipients = false,
        showAmountKeypad = amountFocused,
        scrollDetailsToEndOnKeyboard = false,
        detailsHeader = { SendSheetHeader(shownBalance) },
        recipientsContent = {},
        extraAboveKeypad = {
            InterceptSystemIme {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.section)) {
                        if (showRecipientEditor) {
                            EditableSheetField(
                                editor = recipientEditor,
                                onText = {
                                    amountFocused = false
                                    editingRecipient = true
                                    sendVm.setQuery(it)
                                },
                                placeholder = "Name or phone number",
                                sanitize = ::sanitizeSheetLine,
                                onFocused = { focused ->
                                    if (focused) {
                                        amountFocused = false
                                        editingRecipient = true
                                        queryFocused = true
                                    } else {
                                        queryFocused = false
                                    }
                                },
                                trailingIcon = {
                                    TextButton(
                                        onClick = {
                                            dismissTextKeyboard()
                                            amountFocused = false
                                            pickPhoneContact()
                                        }
                                    ) {
                                        Text("Contacts", style = HomeType.label, color = Accent)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { searchFieldHeightPx = it.size.height }
                            )
                        } else {
                            SendPartyCard(
                                name = selectedName,
                                phone = phone,
                                sentThisMonth = state.sentThisMonth,
                                onChange = { editRecipient() }
                            )
                        }
                        SendAmountEntry(
                            amountInput = state.amountInput,
                            amount = pendingAmount,
                            balance = shownBalance,
                            feeEstimate = state.feeEstimate,
                            onFocus = { focusAmount() }
                        )
                    }
                    if (showRecipientEditor && searchMatches.isNotEmpty()) {
                        SendSearchResultsOverlay(
                            matches = searchMatches,
                            onSelect = { match ->
                                matchContacts.firstOrNull { it.transactionId == match.id }?.let { contact ->
                                    sendVm.select(contact)
                                    focusAmount()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = searchFieldHeight)
                        )
                    }
                }
            }
        },
        inAppTextKeyboard = if (!amountFocused && queryFocused) {
            {
                InAppKeyboard(
                    startOnDigits = state.query.opensOnDigitKeys(),
                    onChar = { ch ->
                        sendVm.setQuery(recipientEditor.insert(ch))
                    },
                    onBackspace = {
                        sendVm.setQuery(recipientEditor.backspace())
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
                canConfirm = state.canSend,
                paymentBlock = paymentBlock,
                onConfirm = { showConfirm = true },
                onAllow = sheetAccess.allow,
                onContinue = { focusAmount() }
            )
        },
        onKey = sendVm::appendAmountKey,
        onBackspace = sendVm::deleteAmountKey,
        onApplyAddUpAmount = sendVm::setAmountInput,
        onClearAmount = { sendVm.setAmountInput("") },
        feedbackMessage = chrome.feedbackMessage,
        feedbackTone = chrome.feedbackTone,
        fulizaCoversShortfall = true,
        footnote = "You’ll enter your PIN on LipaBill’s secure keypad.",
        modifier = Modifier.fillMaxWidth()
    )

    sheetAccess.Dialog()

    MoneyFlowConfirmDialog(
        visible = showConfirm,
        canConfirm = state.canSend,
        confirmLabel = "Send Now",
        summary = sendVm.confirmSummary(),
        onDismiss = { showConfirm = false },
        onConfirm = {
            showConfirm = false
            onConfirm()
        }
    )
}
