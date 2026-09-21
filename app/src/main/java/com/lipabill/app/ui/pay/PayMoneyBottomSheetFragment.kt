package com.lipabill.app.ui.pay

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.KeyboardType
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
import com.lipabill.app.ui.sheet.MoneyConfirmDialog
import com.lipabill.app.ui.sheet.MoneySheetScaffold
import com.lipabill.app.ui.sheet.SheetInputStyle
import com.lipabill.app.ui.sheet.expandForComposeContent
import com.lipabill.app.ui.sheet.formatSheetAmount
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.LipaBillTheme
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.viewmodel.PayMethod
import com.lipabill.app.viewmodel.PayMoneyViewModel
import com.lipabill.app.viewmodel.RepeatTransactionViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel

private enum class PaySheetStep {
    Details,
    Amount
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
    var step by remember { mutableStateOf(PaySheetStep.Details) }
    var showConfirm by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as LipaBillApp
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
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }

    fun goToAmount() {
        if (!state.detailsValid) return
        focusManager.clearFocus(force = true)
        keyboard?.hide()
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

    MoneySheetScaffold(
        title = "Pay",
        balance = listState.latestBalance,
        alwaysShowBalance = alwaysShowBalance,
        pendingDeduction = pendingAmount,
        selectedName = selectedName ?: "Choose merchant",
        selectedSubtitle = selectedSubtitle ?: state.method.labelOrPick(),
        amountDisplay = formatSheetAmount(state.amountInput),
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
                        payVm.selectMethod(it)
                        step = PaySheetStep.Details
                    }
                )
                Spacer(modifier = Modifier.height(Space.block))
                when (state.method) {
                    PayMethod.PAYBILL, PayMethod.TILL -> {
                        if (state.merchantHits.isEmpty()) {
                            Text(
                                text = "Search a saved name below, or type a number.",
                                style = HomeType.caption,
                                color = Mute
                            )
                        } else {
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
                                                focusManager.clearFocus(force = true)
                                                keyboard?.hide()
                                                step = PaySheetStep.Amount
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    PayMethod.POCHI -> {
                        if (state.pochiContacts.isEmpty()) {
                            Text(
                                text = "No past contacts — enter a phone number below.",
                                style = HomeType.caption,
                                color = Mute
                            )
                        } else {
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
                                            focusManager.clearFocus(force = true)
                                            keyboard?.hide()
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
                when (state.method) {
                    PayMethod.PAYBILL -> {
                        OutlinedTextField(
                            value = state.merchantQuery,
                            onValueChange = payVm::setMerchantQuery,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = SheetInputStyle,
                            shape = RoundedCornerShape(14.dp),
                            placeholder = {
                                Text("Business name or number", style = HomeType.body, color = Mute)
                            }
                        )
                        Spacer(modifier = Modifier.height(Space.gap))
                        OutlinedTextField(
                            value = state.accountNumber,
                            onValueChange = payVm::setAccountNumber,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = SheetInputStyle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            shape = RoundedCornerShape(14.dp),
                            placeholder = {
                                Text("Account / shop code", style = HomeType.body, color = Mute)
                            },
                            enabled = state.businessNumber.length >= 5
                        )
                    }
                    PayMethod.TILL -> {
                        OutlinedTextField(
                            value = state.merchantQuery,
                            onValueChange = payVm::setMerchantQuery,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
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
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = SheetInputStyle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            shape = RoundedCornerShape(14.dp),
                            placeholder = {
                                Text("Name or phone number", style = HomeType.body, color = Mute)
                            }
                        )
                    }
                    null -> Unit
                }
            }
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
