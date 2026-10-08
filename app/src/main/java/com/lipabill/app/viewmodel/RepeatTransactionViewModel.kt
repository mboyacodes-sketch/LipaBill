package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.UssdMenuBuilder
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
    val featureEnabled: Boolean = true,
    val accessibilityEnabled: Boolean = false,
    val canPay: Boolean = false,
    val statusMessage: String? = null,
    val dialStarted: Boolean = false,
    val simLines: List<SimLine> = emptyList(),
    val selectedSubscriptionId: Int? = null,
    val hasSavedSimPreference: Boolean = false,
    val needsPhoneStatePermission: Boolean = false
)

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
        val plan = if (tx != null && amount != null) {
            UssdMenuBuilder.build(tx, amountOverride = amount)
        } else {
            null
        }
        val lines = getApplication<Application>().paymentLineSnapshot(
            coordinator,
            app.securePreferences
        )
        _ui.value = RepeatConfirmUiState(
            transaction = tx,
            plan = plan,
            amountInput = amountInput,
            amountValid = amount != null,
            featureEnabled = lines.featureEnabled,
            accessibilityEnabled = lines.accessibilityEnabled,
            canPay = tx != null && UssdMenuBuilder.canRepeat(tx),
            statusMessage = _ui.value.statusMessage,
            dialStarted = _ui.value.dialStarted,
            simLines = lines.simLines,
            selectedSubscriptionId = lines.selectedSubscriptionId,
            hasSavedSimPreference = lines.hasSavedSimPreference,
            needsPhoneStatePermission = lines.needsPhoneStatePermission
        )
    }

    suspend fun prepare(): RepeatTransactionCoordinator.PreparedRepeat? {
        val tx = transaction.value ?: return null
        val amount = parseAmount(_ui.value.amountInput) ?: return null
        return coordinator.prepareSession(tx, amount)
    }

    fun startDial(prepared: RepeatTransactionCoordinator.PreparedRepeat) {
        val state = _ui.value
        val status = dialStartedStatus(
            app.securePreferences,
            state.selectedSubscriptionId,
            state.simLines
        )
        coordinator.startDialing(prepared, state.selectedSubscriptionId)
        _ui.value = state.copy(dialStarted = true, statusMessage = status)
    }

    suspend fun logManualCopy() {
        val tx = transaction.value ?: return
        coordinator.recordManualCopy(tx)
    }

    fun manualDetailsText(): String {
        val tx = transaction.value ?: return ""
        val amount = parseAmount(_ui.value.amountInput)
        val plan = amount?.let { UssdMenuBuilder.build(tx, amountOverride = it) }
        val line = _ui.value.simLines.firstOrNull {
            it.subscriptionId == _ui.value.selectedSubscriptionId
        }
        return buildString {
            appendLine("Dial ${UssdMenuBuilder.USSD_CODE}")
            if (line != null) appendLine("Use line: ${line.label}")
            appendLine("Type: ${tx.type}")
            appendLine("To: ${tx.counterpartyName ?: "—"} ${tx.counterpartyPhone ?: ""}")
            val shownAmount = amount ?: tx.amount
            if (shownAmount != null) {
                appendLine("Amount: ${formatKesMoney(shownAmount)}")
            } else {
                appendLine("Amount: —")
            }
            if (plan != null) {
                appendLine("Plan: ${plan.describe()}")
            }
            appendLine()
            appendLine("Enter your M-Pesa PIN on LipaBill’s keypad when using automation. PIN is not stored.")
        }
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
