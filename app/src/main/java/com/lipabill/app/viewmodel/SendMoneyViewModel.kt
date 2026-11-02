package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.ussd.PaymentManualDetails
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.ui.util.appendAmountKey as applyAmountKey
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.ui.util.sanitizeAmountInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SendMoneyUiState(
    val contacts: List<SendContact> = emptyList(),
    val query: String = "",
    val selected: SendContact? = null,
    val amountInput: String = "",
    val amountValid: Boolean = false,
    val detailsValid: Boolean = false,
    val canSend: Boolean = false,
    val featureEnabled: Boolean = true,
    val accessibilityEnabled: Boolean = false,
    val simLines: List<SimLine> = emptyList(),
    val selectedSubscriptionId: Int? = null,
    val hasSavedSimPreference: Boolean = false,
    val needsPhoneStatePermission: Boolean = false,
    val dialStarted: Boolean = false,
    val statusMessage: String? = null,
    /** What the user should do next on the recipient step (null when ready). */
    val guidanceMessage: String? = null
)

class SendMoneyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LipaBillApp
    private val coordinator = app.repeatCoordinator

    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<SendContact?>(null)
    private val amountInput = MutableStateFlow("")
    private val _gates = MutableStateFlow(SendMoneyUiState())

    private val txContacts = app.repository.observeSendContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<SendMoneyUiState> = combine(
        combine(txContacts, query, selected, amountInput) { tx, q, sel, amount ->
            RecipientDraft(tx, q, sel, amount)
        },
        _gates
    ) { draft, gates ->
        val trimmed = draft.query.trim()
        val filtered = filterRecipientSearch(draft.tx, trimmed)
        val amountOk = RepeatTransactionViewModel.parseAmount(draft.amount) != null
        val detailsOk = draft.selected != null ||
            UssdMenuBuilder.normalizePhoneNumber(trimmed) != null
        val probeOk = detailsOk && buildProbe(draft.selected, trimmed) != null
        gates.copy(
            contacts = filtered,
            query = draft.query,
            selected = draft.selected,
            amountInput = draft.amount,
            amountValid = amountOk,
            detailsValid = detailsOk,
            canSend = detailsOk && amountOk && probeOk && !gates.dialStarted,
            guidanceMessage = sendDetailsGuidance(
                query = trimmed,
                selected = draft.selected,
                contacts = filtered,
                detailsOk = detailsOk
            )
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SendMoneyUiState())

    private fun sendDetailsGuidance(
        query: String,
        selected: SendContact?,
        contacts: List<SendContact>,
        detailsOk: Boolean
    ): String? {
        if (detailsOk || selected != null) return null
        val label = query.take(28).let { if (query.length > 28) "$it…" else it }
        return when {
            query.isBlank() ->
                "Search a past send, choose a contact, or type a phone number."
            contacts.isEmpty() && query.any { it.isLetter() } ->
                "No past matches for \"$label\". Choose a contact, or type a phone number."
            contacts.isEmpty() ->
                "Enter a full phone number to continue."
            else ->
                "Pick a past send, choose a contact, or enter a full phone number."
        }
    }

    private data class RecipientDraft(
        val tx: List<SendContact>,
        val query: String,
        val selected: SendContact?,
        val amount: String
    )

    init {
        refreshGates()
    }

    fun refreshGates() {
        val ctx = getApplication<Application>()
        val lines = ctx.paymentLineSnapshot(coordinator, app.securePreferences)
        _gates.update {
            it.copy(
                featureEnabled = lines.featureEnabled,
                accessibilityEnabled = lines.accessibilityEnabled,
                simLines = lines.simLines,
                selectedSubscriptionId = lines.selectedSubscriptionId,
                hasSavedSimPreference = lines.hasSavedSimPreference,
                needsPhoneStatePermission = lines.needsPhoneStatePermission
            )
        }
    }

    fun setQuery(value: String) {
        query.value = value
        selected.value = null
    }

    fun select(contact: SendContact) {
        selected.value = contact
        query.value = contact.name ?: contact.normalizedPhone
    }

    fun clearSelection() {
        selected.value = null
        amountInput.value = ""
        query.value = ""
        _gates.update { it.copy(dialStarted = false, statusMessage = null) }
    }

    private val amountResume = AmountStepResume()

    /** Full reset when the sheet is dismissed. */
    fun resetSession() {
        clearSelection()
        amountResume.clear()
    }

    /**
     * Re-open Send on the amount step after the user cancels the M-Pesa PIN pad.
     */
    fun restoreAmountEntry(phone: String, name: String?, amountInput: String) {
        select(
            SendContact(
                transactionId = 0L,
                name = name,
                phone = phone,
                normalizedPhone = phone
            )
        )
        setAmountInput(amountInput)
        _gates.update { it.copy(dialStarted = false, statusMessage = null) }
        amountResume.mark()
    }

    fun consumeResumeOnAmountStep(): Boolean = amountResume.consume()

    fun setAmountInput(value: String) {
        amountInput.value = sanitizeAmountInput(value)
    }

    fun appendAmountKey(key: String) {
        amountInput.value = applyAmountKey(amountInput.value, key)
    }

    fun deleteAmountKey() {
        amountInput.value = amountInput.value.dropLast(1)
    }

    fun buildSyntheticTransaction(): MpesaTransaction? {
        val state = uiState.value
        val amount = RepeatTransactionViewModel.parseAmount(state.amountInput) ?: return null
        val phone = state.selected?.normalizedPhone
            ?: UssdMenuBuilder.normalizePhoneNumber(state.query)
            ?: return null
        val name = state.selected?.name
            ?: state.query.trim().takeIf { it.any { c -> c.isLetter() } && it != phone }
            ?: phone
        val now = System.currentTimeMillis()
        val sourceId = state.selected?.takeIf { !it.fromPhoneBook && it.transactionId > 0 }?.transactionId ?: 0L
        return MpesaTransaction(
            id = sourceId,
            code = "MANUAL-SEND-$now",
            type = TransactionType.SENT,
            amount = amount,
            counterpartyName = name,
            counterpartyPhone = phone,
            timestampMillis = now,
            balance = null,
            cost = null,
            rawBody = ""
        )
    }

    suspend fun prepare(): RepeatTransactionCoordinator.PreparedRepeat? {
        val tx = buildSyntheticTransaction() ?: return null
        val amount = RepeatTransactionViewModel.parseAmount(uiState.value.amountInput) ?: return null
        return coordinator.prepareSession(tx, amount)
    }

    fun startDial(prepared: RepeatTransactionCoordinator.PreparedRepeat): Boolean {
        val state = uiState.value
        val status = coordinator.startDialWithStatus(
            prepared,
            app.securePreferences,
            state.selectedSubscriptionId,
            state.simLines
        ) ?: return false
        _gates.update { it.copy(dialStarted = true, statusMessage = status) }
        return true
    }

    fun logManualCopy() {
        viewModelScope.launch {
            val tx = buildSyntheticTransaction() ?: return@launch
            coordinator.recordManualCopy(tx)
        }
    }

    fun manualDetailsText(): String {
        val state = uiState.value
        val amount = RepeatTransactionViewModel.parseAmount(state.amountInput)
        val summary = confirmSummary()
        val phone = state.selected?.normalizedPhone
            ?: UssdMenuBuilder.normalizePhoneNumber(state.query)
        val name = state.selected?.name ?: phone
        return PaymentManualDetails.format(
            typeLabel = summary?.first ?: "Send",
            toLine = summary?.second
                ?: listOfNotNull(name, phone).distinct().joinToString(" "),
            amountLabel = summary?.third ?: amount?.let { formatKesMoney(it) },
            planDescription = PaymentManualDetails.planDescription(
                buildSyntheticTransaction(),
                amount
            ),
            simLabel = PaymentManualDetails.simLabel(state.simLines, state.selectedSubscriptionId)
        )
    }

    fun confirmSummary(): Triple<String, String, String>? {
        val state = uiState.value
        val amount = RepeatTransactionViewModel.parseAmount(state.amountInput) ?: return null
        val phone = state.selected?.normalizedPhone
            ?: UssdMenuBuilder.normalizePhoneNumber(state.query)
            ?: return null
        val name = state.selected?.name ?: phone
        val amountLabel = formatKesMoney(amount)
        return Triple("Send", listOfNotNull(name.takeIf { it != phone }, phone).joinToString(" · "), amountLabel)
    }

    private fun buildProbe(sel: SendContact?, query: String): MpesaTransaction? {
        val phone = sel?.normalizedPhone
            ?: UssdMenuBuilder.normalizePhoneNumber(query)
            ?: return null
        return MpesaTransaction(
            id = 0,
            code = "PROBE",
            type = TransactionType.SENT,
            amount = 1.0,
            counterpartyName = sel?.name,
            counterpartyPhone = phone,
            timestampMillis = System.currentTimeMillis(),
            balance = null,
            cost = null,
            rawBody = ""
        ).takeIf { UssdMenuBuilder.canRepeat(it) }
    }

    companion object {
        fun filterRecipientSearch(
            txContacts: List<SendContact>,
            query: String
        ): List<SendContact> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return txContacts
            return txContacts.filter { matchesQuery(it, trimmed) }
        }

        private fun matchesQuery(contact: SendContact, query: String): Boolean =
            contact.name?.contains(query, ignoreCase = true) == true ||
                contact.phone.contains(query, ignoreCase = true) ||
                contact.normalizedPhone.contains(query, ignoreCase = true)
    }
}
