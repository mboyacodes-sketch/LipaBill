package com.lipabill.app.ui.send

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lipabill.app.R
import com.lipabill.app.ui.permissions.rememberContactsAccessOffer
import com.lipabill.app.ui.permissions.paymentAccessDirective
import com.lipabill.app.ui.sheet.EditableSheetField
import com.lipabill.app.ui.sheet.HorizontalRecipient
import com.lipabill.app.ui.sheet.HorizontalRecipientRow
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.rememberSheetEditor
import com.lipabill.app.ui.sheet.MoneyConfirmDialog
import com.lipabill.app.ui.sheet.MoneySheetScaffold
import com.lipabill.app.ui.sheet.rememberMoneySheetAccess
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
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.opensOnDigitKeys
import com.lipabill.app.ui.util.hideKeyboard
import com.lipabill.app.ui.util.sanitizeSheetLine
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.viewmodel.RepeatTransactionViewModel
import com.lipabill.app.viewmodel.WalletAccount
import com.lipabill.app.viewmodel.SendMoneyViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel

private enum class SendSheetStep {
    Recipient,
    Amount
}

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
    val listState by listVm.uiState.collectAsStateWithLifecycle()
    var step by remember {
        mutableStateOf(
            when {
                sendVm.consumeResumeOnAmountStep() -> SendSheetStep.Amount
                sendVm.uiState.value.selected != null -> SendSheetStep.Amount
                else -> SendSheetStep.Recipient
            }
        )
    }
    var showConfirm by remember { mutableStateOf(false) }
    var queryFocused by remember { mutableStateOf(false) }
    val sheetAccess = rememberMoneySheetAccess { sendVm.refreshGates() }
    val paymentBlock = sheetAccess.block
    val pendingAmount = remember(state.amountInput) {
        RepeatTransactionViewModel.parseAmount(state.amountInput)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val offerContacts = rememberContactsAccessOffer {
        sendVm.refreshContactsAccess()
    }
    val recipientEditor = rememberSheetEditor(state.query)

    LaunchedEffect(Unit) {
        sendVm.refreshGates()
        if (state.selected != null) {
            step = SendSheetStep.Amount
            hideKeyboard(focusManager, keyboard)
        }
    }

    LaunchedEffect(step) {
        if (step == SendSheetStep.Amount) {
            queryFocused = false
            hideKeyboard(focusManager, keyboard)
        }
    }

    fun goToAmount() {
        if (!state.detailsValid) return
        queryFocused = false
        hideKeyboard(focusManager, keyboard)
        step = SendSheetStep.Amount
    }

    fun goToRecipient() {
        showConfirm = false
        sendVm.setAmountInput("")
        step = SendSheetStep.Recipient
    }

    val selectedName = state.selected?.let { it.name ?: it.normalizedPhone }
        ?: UssdMenuBuilder.normalizePhoneNumber(state.query)
        ?: state.query.trim().takeIf { it.isNotEmpty() }
    val selectedSubtitle = state.selected?.normalizedPhone?.let { "@$it" }
        ?: UssdMenuBuilder.normalizePhoneNumber(state.query)?.let { "@$it" }

    val onAmountStep = step == SendSheetStep.Amount
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

    MoneySheetScaffold(
        balance = shownBalance,
        balanceCaption = WalletAccount.MPESA.balanceCaption,
        balancePrefix = WalletAccount.MPESA.balancePrefix,
        alwaysShowBalance = sheetAccess.alwaysShowBalance,
        pendingDeduction = pendingAmount,
        selectedName = selectedName,
        selectedSubtitle = selectedSubtitle,
        amountDisplay = formatMoneyInputLabel(state.amountInput),
        amountInput = state.amountInput,
        selectedSelected = state.detailsValid,
        recipientsHeader = if (onAmountStep) "Change recipient" else "Frequent",
        onRecipientsHeaderClick = if (onAmountStep) {
            { goToRecipient() }
        } else {
            null
        },
        showKeypad = onAmountStep,
        showRecipients = !onAmountStep,
        recipientsContent = {
            if (state.contacts.isNotEmpty()) {
                HorizontalRecipientRow {
                    state.contacts.take(12).forEach { contact ->
                        HorizontalRecipient(
                            name = contact.sheetName(),
                            subtitle = contact.sheetSubtitle(),
                            selected = contact.sameParty(state.selected),
                            onClick = {
                                sendVm.select(contact)
                                queryFocused = false
                                hideKeyboard(focusManager, keyboard)
                                step = SendSheetStep.Amount
                            }
                        )
                    }
                }
            }
        },
        extraAboveKeypad = if (onAmountStep) {
            null
        } else {
            {
                InterceptSystemIme {
                    EditableSheetField(
                        editor = recipientEditor,
                        onText = sendVm::setQuery,
                        placeholder = "Name or phone number",
                        sanitize = ::sanitizeSheetLine,
                        onFocused = { focused ->
                            queryFocused = focused
                            offerContacts(focused)
                        }
                    )
                }
            }
        },
        inAppTextKeyboard = if (!onAmountStep && queryFocused) {
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
                        queryFocused = false
                        focusManager.clearFocus(force = true)
                    }
                )
            }
        } else {
            null
        },
        ctaLabel = when {
            paymentBlock != null -> paymentBlock.actionLabel
            onAmountStep -> "Send Now"
            else -> "Continue"
        },
        ctaEnabled = if (paymentBlock != null) {
            true
        } else if (onAmountStep) {
            state.canSend && !state.dialStarted
        } else {
            state.detailsValid
        },
        onCta = {
            val block = paymentBlock
            if (block != null) {
                sheetAccess.allow(block)
            } else if (onAmountStep) {
                showConfirm = true
            } else {
                goToAmount()
            }
        },
        onKey = sendVm::appendAmountKey,
        onBackspace = sendVm::deleteAmountKey,
        onApplyAddUpAmount = sendVm::setAmountInput,
        onClearAmount = { sendVm.setAmountInput("") },
        feedbackMessage = feedback?.first,
        feedbackTone = feedback?.second ?: SheetFeedbackTone.Hint,
        fulizaCoversShortfall = true,
        modifier = Modifier.fillMaxWidth()
    )

    sheetAccess.Dialog()

    if (showConfirm && state.canSend) {
        val summary = sendVm.confirmSummary()
        if (summary != null) {
            val (_, detail, amountLabel) = summary
            MoneyConfirmDialog(
                amountLabel = amountLabel,
                detail = detail,
                confirmLabel = "Send Now",
                onDismiss = { showConfirm = false },
                onConfirm = {
                    showConfirm = false
                    onConfirm()
                }
            )
        }
    }
}
