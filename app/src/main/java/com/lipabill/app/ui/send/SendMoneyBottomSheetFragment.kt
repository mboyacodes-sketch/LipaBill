package com.lipabill.app.ui.send

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.lipabill.app.ui.sheet.SheetFeedbackTone
import com.lipabill.app.ui.sheet.SheetInputStyle
import com.lipabill.app.ui.sheet.expandForComposeContent
import com.lipabill.app.ui.sheet.formatSheetAmount
import com.lipabill.app.ui.sheet.resolveSheetFeedback
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.LipaBillTheme
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.bringIntoViewOnFocus
import com.lipabill.app.ui.util.hideKeyboard
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.viewmodel.RepeatTransactionViewModel
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        AppSecurity.lockScreenCapture(dialog)
        dialog.expandForComposeContent()
        return dialog
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        sendVm.resetSession()
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
                    SendMoneySheetContent(
                        sendVm = sendVm,
                        listVm = listVm,
                        onConfirm = {
                            // Keep sheet state until dial starts — dismiss resets the VM.
                            host?.onSendSheetConfirm()
                        }
                    )
                }
            }
        }
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
    val app = LocalContext.current.applicationContext as LipaBillApp
    val alwaysShowBalance by app.alwaysShowBalance.collectAsStateWithLifecycle()
    val pendingAmount = remember(state.amountInput) {
        RepeatTransactionViewModel.parseAmount(state.amountInput)
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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
        selectedName = selectedName,
        selectedSubtitle = selectedSubtitle,
        amountDisplay = formatSheetAmount(state.amountInput),
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
                        val name = contact.name ?: contact.normalizedPhone
                        val subtitle = if (contact.fromPhoneBook) {
                            "Phone · ${contact.normalizedPhone}"
                        } else {
                            contact.normalizedPhone
                        }
                        HorizontalRecipient(
                            name = name,
                            subtitle = subtitle,
                            selected = contact.normalizedPhone == state.selected?.normalizedPhone &&
                                contact.fromPhoneBook == state.selected?.fromPhoneBook,
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
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = sendVm::setQuery,
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewOnFocus(delayMs = 80L)
                            .onFocusChanged { queryFocused = it.isFocused },
                        singleLine = true,
                        readOnly = true,
                        textStyle = SheetInputStyle,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = {
                            Text("Name or phone number", style = HomeType.body, color = Mute)
                        }
                    )
                }
            }
        },
        inAppTextKeyboard = if (!onAmountStep && queryFocused) {
            {
                InAppKeyboard(
                    startOnDigits = state.query.any { it.isDigit() } &&
                        state.query.none { it.isLetter() },
                    onChar = { ch ->
                        sendVm.setQuery(state.query + ch)
                    },
                    onBackspace = {
                        sendVm.setQuery(state.query.dropLast(1))
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
        ctaLabel = if (onAmountStep) "Send Now" else "Continue",
        ctaEnabled = if (onAmountStep) {
            state.canSend && !state.dialStarted
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
        onKey = sendVm::appendAmountKey,
        onBackspace = sendVm::deleteAmountKey,
        onApplyAddUpAmount = sendVm::setAmountInput,
        onClearAmount = { sendVm.setAmountInput("") },
        feedbackMessage = feedback?.first,
        feedbackTone = feedback?.second ?: SheetFeedbackTone.Hint,
        modifier = Modifier.fillMaxWidth()
    )

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
