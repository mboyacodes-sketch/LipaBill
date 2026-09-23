package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.contacts.PhoneBookSearcher
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.SimLineHelper
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.ui.util.sanitizeAmountInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
    val hasContactsPermission: Boolean = false,
    val dialStarted: Boolean = false,
    val statusMessage: String? = null,
    /** What the user should do next on the recipient step (null when ready). */
    val guidanceMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SendMoneyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LipaBillApp
    private val coordinator = app.repeatCoordinator

    private val query = MutableStateFlow("")
    private val selected = MutableStateFlow<SendContact?>(null)
    private val amountInput = MutableStateFlow("")
    private val _gates = MutableStateFlow(SendMoneyUiState())
    private val contactsAccess = MutableStateFlow(PhoneBookSearcher.hasPermission(application))

    private val txContacts = app.repository.observeSendContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val phoneBookHits: StateFlow<List<SendContact>> = combine(query, contactsAccess) { q, _ -> q }
        .debounce(250)
        .flatMapLatest { q ->
            flow {
                val trimmed = q.trim()
                if (trimmed.length < 2 || !PhoneBookSearcher.hasPermission(getApplication())) {
                    emit(emptyList())
                } else {
                    emit(PhoneBookSearcher.search(getApplication(), trimmed))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<SendMoneyUiState> = combine(
        combine(txContacts, phoneBookHits, query, selected, amountInput) { tx, book, q, sel, amount ->
            RecipientDraft(tx, book, q, sel, amount)
        },
        _gates
    ) { draft, gates ->
        val trimmed = draft.query.trim()
        val filtered = mergeRecipientSearch(draft.tx, draft.book, trimmed)
        val amountOk = RepeatTransactionViewModel.parseAmount(draft.amount) != null
        val detailsOk = draft.selected != null ||
            UssdMenuBuilder.normalizePhoneNumber(trimmed) != null
        val probeOk = detailsOk && buildProbe(draft.selected, trimmed) != null
        val hasPerm = PhoneBookSearcher.hasPermission(getApplication())
        gates.copy(
            contacts = filtered,
            query = draft.query,
            selected = draft.selected,
            amountInput = draft.amount,
            amountValid = amountOk,
            detailsValid = detailsOk,
            canSend = detailsOk && amountOk && probeOk && !gates.dialStarted,
            hasContactsPermission = hasPerm,
            guidanceMessage = sendDetailsGuidance(
                query = trimmed,
                selected = draft.selected,
                contacts = filtered,
                detailsOk = detailsOk,
                hasContactsPermission = hasPerm
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SendMoneyUiState())

    private fun sendDetailsGuidance(
        query: String,
        selected: SendContact?,
        contacts: List<SendContact>,
        detailsOk: Boolean,
        hasContactsPermission: Boolean
    ): String? {
        if (detailsOk || selected != null) return null
        val label = query.take(28).let { if (query.length > 28) "$it…" else it }
        return when {
            query.isBlank() ->
                "Search a past contact, or type a phone number."
            contacts.isEmpty() && query.any { it.isLetter() } ->
                if (!hasContactsPermission) {
                    "No past matches for \"$label\". Allow Contacts, or type a phone number."
                } else {
                    "No match for \"$label\". Keep typing a phone number to continue."
                }
            contacts.isEmpty() ->
                "Enter a full phone number to continue."
            else ->
                "Pick a contact, or enter a full phone number."
        }
    }

    private data class RecipientDraft(
        val tx: List<SendContact>,
        val book: List<SendContact>,
        val query: String,
        val selected: SendContact?,
        val amount: String
    )

    init {
        refreshGates()
    }

    fun refreshGates() {
        val ctx = getApplication<Application>()
        contactsAccess.value = PhoneBookSearcher.hasPermission(ctx)
        val needsPerm = !SimLineHelper.hasPhoneStatePermission(ctx)
        val lines = if (needsPerm) emptyList() else SimLineHelper.listActiveLines(ctx)
        val selectedSub = if (needsPerm) {
            null
        } else {
            SimLineHelper.ensureSafaricomPreferred(app.securePreferences, lines)
        }
        val preferred = app.securePreferences.preferredSimSubscriptionId
        _gates.update {
            it.copy(
                featureEnabled = coordinator.isFeatureEnabled(),
                accessibilityEnabled = AccessibilityHelper.isLipaBillServiceEnabled(ctx),
                simLines = lines,
                selectedSubscriptionId = selectedSub,
                hasSavedSimPreference = preferred >= 0 &&
                    lines.any { line -> line.subscriptionId == preferred && line.isSafaricom },
                needsPhoneStatePermission = needsPerm,
                hasContactsPermission = PhoneBookSearcher.hasPermission(ctx)
            )
        }
    }

    fun refreshContactsAccess() {
        contactsAccess.value = PhoneBookSearcher.hasPermission(getApplication())
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

    /** Full reset when the sheet is dismissed. */
    fun resetSession() {
        clearSelection()
        resumeOnAmountStep = false
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
        resumeOnAmountStep = true
    }

    /** True once after [restoreAmountEntry]; sheet should land on Amount. */
    var resumeOnAmountStep: Boolean = false
        private set

    fun consumeResumeOnAmountStep(): Boolean {
        val value = resumeOnAmountStep
        resumeOnAmountStep = false
        return value
    }

    fun setAmountInput(value: String) {
        amountInput.value = sanitizeAmountInput(value)
    }

    fun appendAmountKey(key: String) {
        when (key) {
            "." -> {
                if (!amountInput.value.contains('.')) {
                    amountInput.value =
                        if (amountInput.value.isEmpty()) "0." else amountInput.value + "."
                }
            }
            else -> {
                val current = amountInput.value
                val parts = current.split('.')
                if (parts.size == 2 && parts[1].length >= 2) return
                if (current == "0" && key != ".") {
                    amountInput.value = key
                } else {
                    amountInput.value = sanitizeAmountInput(current + key)
                }
            }
        }
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

    fun startDial(prepared: RepeatTransactionCoordinator.PreparedRepeat) {
        val state = uiState.value
        val subId = state.selectedSubscriptionId
        if (subId != null) {
            app.securePreferences.preferredSimSubscriptionId = subId
        }
        coordinator.startDialing(prepared, subId)
        val line = state.simLines.firstOrNull { it.subscriptionId == subId }
        _gates.update {
            it.copy(
                dialStarted = true,
                statusMessage = if (line != null) {
                    "USSD on ${line.label} — enter PIN on the LipaBill keypad when prompted."
                } else {
                    "USSD started — enter PIN on the LipaBill keypad when prompted."
                }
            )
        }
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
        /** Transaction matches first; phone-book fills gaps for the same query. */
        fun mergeRecipientSearch(
            txContacts: List<SendContact>,
            phoneBook: List<SendContact>,
            query: String
        ): List<SendContact> {
            val trimmed = query.trim()
            val txFiltered = if (trimmed.isEmpty()) {
                txContacts
            } else {
                txContacts.filter { matchesQuery(it, trimmed) }
            }
            if (trimmed.isEmpty()) return txFiltered

            val seen = LinkedHashSet<String>()
            val out = ArrayList<SendContact>(txFiltered.size + phoneBook.size)
            for (c in txFiltered) {
                if (seen.add(c.normalizedPhone)) out.add(c)
            }
            for (c in phoneBook) {
                if (seen.add(c.normalizedPhone)) out.add(c)
            }
            return out
        }

        private fun matchesQuery(contact: SendContact, query: String): Boolean =
            contact.name?.contains(query, ignoreCase = true) == true ||
                contact.phone.contains(query, ignoreCase = true) ||
                contact.normalizedPhone.contains(query, ignoreCase = true)
    }
}
