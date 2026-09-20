package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.RepeatOutcome
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.SimLineHelper
import com.lipabill.app.ussd.UssdMenuBuilder
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
                        else -> "0"
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

    fun selectSim(subscriptionId: Int) {
        app.securePreferences.preferredSimSubscriptionId = subscriptionId
        _ui.value = _ui.value.copy(
            selectedSubscriptionId = subscriptionId,
            hasSavedSimPreference = true
        )
    }

    private fun refresh(tx: MpesaTransaction?, amountInput: String) {
        val amount = parseAmount(amountInput)
        val plan = if (tx != null && amount != null) {
            UssdMenuBuilder.build(tx, amountOverride = amount)
        } else {
            null
        }
        val ctx = getApplication<Application>()
        val needsPerm = !SimLineHelper.hasPhoneStatePermission(ctx)
        val lines = if (needsPerm) emptyList() else SimLineHelper.listActiveLines(ctx)
        val preferredRaw = app.securePreferences.preferredSimSubscriptionId
        if (preferredRaw < 0 && lines.size == 1) {
            app.securePreferences.preferredSimSubscriptionId = lines.first().subscriptionId
        }
        val preferred = app.securePreferences.preferredSimSubscriptionId
        val selected = if (preferred >= 0) {
            lines.firstOrNull { it.subscriptionId == preferred }?.subscriptionId
                ?: preferred.takeIf { lines.isEmpty() }
        } else {
            null
        }
        _ui.value = RepeatConfirmUiState(
            transaction = tx,
            plan = plan,
            amountInput = amountInput,
            amountValid = amount != null,
            featureEnabled = coordinator.isFeatureEnabled(),
            accessibilityEnabled = AccessibilityHelper.isLipaBillServiceEnabled(ctx),
            canPay = tx != null && UssdMenuBuilder.canRepeat(tx),
            statusMessage = _ui.value.statusMessage,
            dialStarted = _ui.value.dialStarted,
            simLines = lines,
            selectedSubscriptionId = selected,
            hasSavedSimPreference = preferred >= 0,
            needsPhoneStatePermission = needsPerm
        )
    }

    suspend fun prepare(): RepeatTransactionCoordinator.PreparedRepeat? {
        val tx = transaction.value ?: return null
        val amount = parseAmount(_ui.value.amountInput) ?: return null
        return coordinator.prepareSession(tx, amount)
    }

    fun startDial(prepared: RepeatTransactionCoordinator.PreparedRepeat) {
        val subId = _ui.value.selectedSubscriptionId
        if (subId != null) {
            app.securePreferences.preferredSimSubscriptionId = subId
        }
        coordinator.startDialing(prepared, subId)
        val line = _ui.value.simLines.firstOrNull { it.subscriptionId == subId }
        _ui.value = _ui.value.copy(
            dialStarted = true,
            statusMessage = if (line != null) {
                "USSD on ${line.label} — enter PIN on the LipaBill keypad when prompted."
            } else {
                "USSD started — enter PIN on the LipaBill keypad when prompted."
            }
        )
    }

    suspend fun cancelAttempt(auditId: Long, outcome: RepeatOutcome, detail: String?) {
        coordinator.cancelPrepared(auditId, outcome, detail)
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
            appendLine("Amount: ${amount ?: tx.amount}")
            if (plan != null) {
                appendLine("Plan: ${plan.describe()}")
            }
            appendLine()
            appendLine("Enter your M-Pesa PIN on LipaBill’s keypad when using automation. PIN is not stored.")
        }
    }

    companion object {
        fun parseAmount(raw: String): Double? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            val value = trimmed.toDoubleOrNull() ?: return null
            return value.takeIf { it > 0 }
        }
    }
}
