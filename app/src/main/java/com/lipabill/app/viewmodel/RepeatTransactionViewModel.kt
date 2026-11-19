package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.ussd.PaymentDialResolver
import com.lipabill.app.ussd.PaymentManualDetails
import com.lipabill.app.ussd.PaymentWallet
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.ussd.WalletMenuPlans
import com.lipabill.app.ui.util.appendAmountKey as applyAmountKey
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.ui.util.sanitizeAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RepeatConfirmUiState(
    val transaction: MpesaTransaction? = null,
    val plan: UssdMenuBuilder.MenuPlan? = null,
    val amountInput: String = "",
    val amountValid: Boolean = false,
    val canPay: Boolean = false,
    val statusMessage: String? = null,
    val dialStarted: Boolean = false,
    override val lines: PaymentLineSnapshot = InitialPaymentLines
) : PaymentLineState

class RepeatTransactionViewModel(
    application: Application,
    private val transactionId: Long,
    private val initialAmount: String? = null
) : AndroidViewModel(application) {

    private val app = application as LipaBillApp
    private val coordinator = app.repeatCoordinator

    private val _ui = MutableStateFlow(RepeatConfirmUiState())
    val uiState: StateFlow<RepeatConfirmUiState> = _ui.asStateFlow()

    private var amountSeeded = false

    val transaction: StateFlow<MpesaTransaction?> =
        app.repository.observeById(transactionId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            transaction.collect { tx ->
                if (tx != null && !amountSeeded) {
                    amountSeeded = true
                    val seed = when {
                        !initialAmount.isNullOrBlank() -> sanitizeAmountInput(initialAmount)
                        else -> ""
                    }
                    refresh(tx, amountInput = seed)
                } else {
                    refresh(tx, amountInput = _ui.value.amountInput)
                }
            }
        }
    }

    fun refreshAccessibility() {
        refresh(_ui.value.transaction ?: transaction.value, _ui.value.amountInput)
    }

    fun setAmountInput(value: String) {
        refresh(_ui.value.transaction ?: transaction.value, sanitizeAmountInput(value))
    }

    fun appendAmountKey(key: String) {
        setAmountInput(applyAmountKey(_ui.value.amountInput, key))
    }

    fun deleteAmountKey() {
        setAmountInput(_ui.value.amountInput.dropLast(1))
    }

    private fun refresh(tx: MpesaTransaction?, amountInput: String) {
        val amount = parseAmount(amountInput)
        val lines = getApplication<Application>().paymentLineSnapshot(
            coordinator,
            app.securePreferences
        )
        val enabled = app.securePreferences.enabledLedgerSources(lines.region)
        val wallet = PaymentDialResolver.resolve(
            region = lines.region,
            lines = lines.simLines,
            preferredSubscriptionId = lines.selectedSubscriptionId
                ?: app.securePreferences.preferredSimSubscriptionId,
            preferredSource = tx?.source,
            enabled = enabled
        )?.wallet ?: PaymentWallet.forRegion(lines.region)
            .firstOrNull { it.ledgerSource in enabled }
        val plan = if (tx != null && amount != null && wallet != null) {
            WalletMenuPlans.build(wallet, tx, amountOverride = amount)
        } else {
            null
        }
        val canPay = tx != null && wallet != null && WalletMenuPlans.canRepeat(wallet, tx)
        _ui.value = RepeatConfirmUiState(
            transaction = tx,
            plan = plan,
            amountInput = amountInput,
            amountValid = amount != null,
            canPay = canPay,
            statusMessage = _ui.value.statusMessage,
            dialStarted = _ui.value.dialStarted,
            lines = lines.copy(activeWallet = lines.activeWallet ?: wallet)
        )
    }

    fun selectPayLine(subscriptionId: Int) {
        val state = _ui.value
        setPreferredPayLine(
            app.securePreferences,
            state.region,
            state.simLines,
            subscriptionId
        ) ?: return
        refresh(state.transaction ?: transaction.value, state.amountInput)
    }

    suspend fun prepare(): RepeatTransactionCoordinator.PreparedRepeat? =
        coordinator.preparePayment(transaction.value, _ui.value.amountInput)

    fun startDial(prepared: RepeatTransactionCoordinator.PreparedRepeat): Boolean {
        val state = _ui.value
        val started = coordinator.startPaymentDial(
            prepared,
            app.securePreferences,
            state.region,
            state.simLines,
            state.selectedSubscriptionId,
            preferredSource = state.transaction?.source
        ) ?: return false
        _ui.value = state.copy(
            dialStarted = true,
            statusMessage = started.status,
            lines = state.lines.after(started)
        )
        return true
    }

    suspend fun logManualCopy() {
        val tx = transaction.value ?: return
        coordinator.recordManualCopy(tx)
    }

    fun manualDetailsText(): String {
        val tx = transaction.value ?: return ""
        val state = _ui.value
        val amount = parseAmount(state.amountInput)
        val enabled = app.securePreferences.enabledLedgerSources(state.region)
        val wallet = state.plan?.wallet
            ?: PaymentDialResolver.resolve(
                region = state.region,
                lines = state.simLines,
                preferredSubscriptionId = state.selectedSubscriptionId
                    ?: app.securePreferences.preferredSimSubscriptionId,
                preferredSource = tx.source,
                enabled = enabled
            )?.wallet
        return PaymentManualDetails.format(
            typeLabel = tx.type.toString(),
            toLine = listOfNotNull(tx.counterpartyName, tx.counterpartyPhone)
                .joinToString(" ")
                .ifBlank { "—" },
            amountLabel = (amount ?: tx.amount)?.let { formatKesMoney(it) },
            planDescription = PaymentManualDetails.planDescription(tx, amount, wallet),
            simLabel = PaymentManualDetails.simLabel(state.simLines, state.selectedSubscriptionId),
            ussdCode = wallet?.ussdCode ?: UssdMenuBuilder.USSD_CODE,
            walletBadge = wallet?.badge ?: "mobile money"
        )
    }

    companion object {
        fun parseAmount(raw: String): Double? {
            val trimmed = raw.trim().replace(",", "").replace(" ", "")
            if (trimmed.isEmpty()) return null
            val value = trimmed.toDoubleOrNull() ?: return null
            return value.takeIf { it > 0 }
        }
    }
}
