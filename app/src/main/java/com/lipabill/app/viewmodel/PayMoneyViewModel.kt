package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.contacts.PhoneBookSearcher
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.repository.MerchantHit
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class PayMethod {
    PAYBILL,
    TILL,
    POCHI
}

data class PayMoneyUiState(
    val method: PayMethod? = null,
    val businessNumber: String = "",
    val accountNumber: String = "",
    val tillNumber: String = "",
    val merchantQuery: String = "",
    val merchantHits: List<MerchantHit> = emptyList(),
    val pochiContacts: List<SendContact> = emptyList(),
    val pochiQuery: String = "",
    val selectedPochi: SendContact? = null,
    val amountInput: String = "",
    val detailsValid: Boolean = false,
    val amountValid: Boolean = false,
    val canPay: Boolean = false,
    val featureEnabled: Boolean = true,
    val accessibilityEnabled: Boolean = false,
    val simLines: List<SimLine> = emptyList(),
    val selectedSubscriptionId: Int? = null,
    val hasSavedSimPreference: Boolean = false,
    val needsPhoneStatePermission: Boolean = false,
    val dialStarted: Boolean = false,
    val statusMessage: String? = null,
    /** What the user should do next on the details step (null when ready). */
    val guidanceMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PayMoneyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LipaBillApp
    private val coordinator = app.repeatCoordinator

    private val _ui = MutableStateFlow(PayMoneyUiState())
    private val pochiQuery = MutableStateFlow("")
    private val merchantQuery = MutableStateFlow("")
    private val contactsAccess = MutableStateFlow(PhoneBookSearcher.hasPermission(application))

    private val phoneBookHits: StateFlow<List<SendContact>> =
        combine(pochiQuery, contactsAccess) { q, _ -> q }
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

    private val merchantHits = combine(_ui, merchantQuery) { state, q ->
        state.method to q
    }.flatMapLatest { (method, q) ->
        when (method) {
            PayMethod.PAYBILL ->
                app.repository.observeMerchantSearch(TransactionType.PAYBILL, q)
            PayMethod.TILL ->
                app.repository.observeMerchantSearch(TransactionType.BUY_GOODS, q)
            else -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Learned Pochi recipients (phone ↔ name), same portfolio path as Paybill/Till. */
    private val pochiMerchantHits = combine(_ui, pochiQuery) { state, q ->
        (state.method == PayMethod.POCHI) to q
    }.flatMapLatest { (isPochi, q) ->
        if (!isPochi) flowOf(emptyList())
        else app.repository.observeMerchantSearch(TransactionType.POCHI, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<PayMoneyUiState> = combine(
        combine(_ui, pochiMerchantHits, pochiQuery, phoneBookHits, merchantHits) {
                state, pochiMerchants, pochiQ, book, hits ->
            PochiDraft(state, pochiMerchants, pochiQ, book, hits)
        },
        merchantQuery
    ) { draft, merchantQ ->
        val trimmed = draft.pochiQuery.trim()
        val learnedPochi = draft.pochiMerchants.map { it.toPochiContact() }
        val filteredPochi = SendMoneyViewModel.mergeRecipientSearch(
            txContacts = learnedPochi,
            phoneBook = draft.book,
            query = trimmed
        )
        draft.state.copy(
            pochiContacts = filteredPochi,
            pochiQuery = draft.pochiQuery,
            merchantHits = draft.hits,
            merchantQuery = merchantQ
        ).revalidate()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PayMoneyUiState())

    private data class PochiDraft(
        val state: PayMoneyUiState,
        val pochiMerchants: List<MerchantHit>,
        val pochiQuery: String,
        val book: List<SendContact>,
        val hits: List<MerchantHit>
    )

    init {
        refreshGates()
    }

    fun refreshGates() {
        val ctx = getApplication<Application>()
        contactsAccess.value = PhoneBookSearcher.hasPermission(ctx)
        val needsPerm = !SimLineHelper.hasPhoneStatePermission(ctx)
        val lines = if (needsPerm) emptyList() else SimLineHelper.listActiveLines(ctx)
        val selected = if (needsPerm) {
            null
        } else {
            SimLineHelper.ensureSafaricomPreferred(app.securePreferences, lines)
        }
        val preferred = app.securePreferences.preferredSimSubscriptionId
        _ui.update { state ->
            state.copy(
                featureEnabled = coordinator.isFeatureEnabled(),
                accessibilityEnabled = AccessibilityHelper.isLipaBillServiceEnabled(ctx),
                simLines = lines,
                selectedSubscriptionId = selected,
                hasSavedSimPreference = preferred >= 0 &&
                    lines.any { line -> line.subscriptionId == preferred && line.isSafaricom },
                needsPhoneStatePermission = needsPerm
            )
        }
    }

    fun refreshContactsAccess() {
        contactsAccess.value = PhoneBookSearcher.hasPermission(getApplication())
    }

    fun selectMethod(method: PayMethod) {
        pochiQuery.value = ""
        merchantQuery.value = ""
        _ui.update {
            it.copy(
                method = method,
                businessNumber = "",
                accountNumber = "",
                tillNumber = "",
                selectedPochi = null,
                amountInput = "",
                dialStarted = false,
                statusMessage = null
            )
        }
    }

    fun clearMethod() {
        pochiQuery.value = ""
        merchantQuery.value = ""
        _ui.update {
            PayMoneyUiState(
                featureEnabled = it.featureEnabled,
                accessibilityEnabled = it.accessibilityEnabled,
                simLines = it.simLines,
                selectedSubscriptionId = it.selectedSubscriptionId,
                hasSavedSimPreference = it.hasSavedSimPreference,
                needsPhoneStatePermission = it.needsPhoneStatePermission
            )
        }
    }

    /** Full reset when the sheet is dismissed. */
    fun resetSession() {
        clearMethod()
        resumeOnAmountStep = false
    }

    /**
     * Re-open Pay on the amount step after the user cancels the M-Pesa PIN pad.
     */
    fun restoreAmountEntry(
        method: PayMethod,
        amountInput: String,
        businessNumber: String = "",
        accountNumber: String = "",
        tillNumber: String = "",
        pochiPhone: String? = null,
        pochiName: String? = null
    ) {
        selectMethod(method)
        when (method) {
            PayMethod.PAYBILL -> {
                merchantQuery.value = businessNumber
                _ui.update {
                    it.copy(
                        businessNumber = businessNumber,
                        accountNumber = accountNumber,
                        amountInput = sanitizeAmountInput(amountInput),
                        dialStarted = false,
                        statusMessage = null
                    )
                }
            }
            PayMethod.TILL -> {
                merchantQuery.value = tillNumber
                _ui.update {
                    it.copy(
                        tillNumber = tillNumber,
                        amountInput = sanitizeAmountInput(amountInput),
                        dialStarted = false,
                        statusMessage = null
                    )
                }
            }
            PayMethod.POCHI -> {
                val phone = pochiPhone.orEmpty()
                if (phone.isNotBlank()) {
                    selectPochiContact(
                        SendContact(
                            transactionId = 0L,
                            name = pochiName,
                            phone = phone,
                            normalizedPhone = phone
                        )
                    )
                }
                _ui.update {
                    it.copy(
                        amountInput = sanitizeAmountInput(amountInput),
                        dialStarted = false,
                        statusMessage = null
                    )
                }
            }
        }
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

    fun setMerchantQuery(value: String) {
        merchantQuery.value = value
        val digits = value.filter { it.isDigit() }.take(12)
        val numericEntry = value.isNotBlank() && value.none { it.isLetter() } && digits.length >= 5
        _ui.update { state ->
            when (state.method) {
                PayMethod.PAYBILL -> state.copy(
                    businessNumber = if (numericEntry) digits else ""
                )
                PayMethod.TILL -> state.copy(
                    tillNumber = if (numericEntry) digits else ""
                )
                else -> state
            }
        }
    }

    fun selectMerchant(hit: MerchantHit) {
        merchantQuery.value = hit.displayName
        when (hit.type) {
            TransactionType.PAYBILL -> {
                _ui.update {
                    it.copy(
                        businessNumber = hit.identifier,
                        accountNumber = hit.accountHint.orEmpty().ifBlank { it.accountNumber },
                        dialStarted = false,
                        statusMessage = null
                    )
                }
            }
            TransactionType.BUY_GOODS -> {
                _ui.update {
                    it.copy(
                        tillNumber = hit.identifier,
                        dialStarted = false,
                        statusMessage = null
                    )
                }
            }
            else -> Unit
        }
    }

    fun setAccountNumber(value: String) {
        _ui.update {
            it.copy(accountNumber = value.filter { ch -> ch.isLetterOrDigit() }.take(20))
        }
    }

    fun setPochiQuery(value: String) {
        pochiQuery.value = value
        _ui.update { it.copy(selectedPochi = null) }
    }

    fun selectPochiContact(contact: SendContact) {
        pochiQuery.value = contact.name ?: contact.normalizedPhone
        _ui.update {
            it.copy(
                selectedPochi = contact,
                dialStarted = false,
                statusMessage = null
            )
        }
    }

    fun clearPochiContact() {
        pochiQuery.value = ""
        _ui.update {
            it.copy(
                selectedPochi = null,
                dialStarted = false,
                statusMessage = null
            )
        }
    }

    fun setAmountInput(value: String) {
        _ui.update { it.copy(amountInput = sanitizeAmountInput(value)) }
    }

    fun appendAmountKey(key: String) {
        val current = _ui.value.amountInput
        when (key) {
            "." -> {
                if (!current.contains('.')) {
                    _ui.update {
                        it.copy(amountInput = if (current.isEmpty()) "0." else current + ".")
                    }
                }
            }
            else -> {
                val parts = current.split('.')
                if (parts.size == 2 && parts[1].length >= 2) return
                val next = if (current == "0") key else current + key
                _ui.update { it.copy(amountInput = sanitizeAmountInput(next)) }
            }
        }
    }

    fun deleteAmountKey() {
        _ui.update { it.copy(amountInput = it.amountInput.dropLast(1)) }
    }

    fun buildSyntheticTransaction(): MpesaTransaction? {
        val state = uiState.value
        val amount = RepeatTransactionViewModel.parseAmount(state.amountInput) ?: return null
        val method = state.method ?: return null
        val now = System.currentTimeMillis()
        return when (method) {
            PayMethod.PAYBILL -> {
                val business = state.businessNumber.trim()
                val account = state.accountNumber.trim()
                if (business.length < 5 || account.isEmpty()) return null
                val knownName = state.merchantHits
                    .firstOrNull { it.identifier == business }
                    ?.displayName
                MpesaTransaction(
                    id = 0,
                    code = "MANUAL-PB-$now",
                    type = TransactionType.PAYBILL,
                    amount = amount,
                    counterpartyName = knownName ?: "Paybill $business",
                    counterpartyPhone = business,
                    timestampMillis = now,
                    balance = null,
                    cost = null,
                    rawBody = "Account $account"
                )
            }
            PayMethod.TILL -> {
                val till = state.tillNumber.trim()
                if (till.length < 5) return null
                val knownName = state.merchantHits
                    .firstOrNull { it.identifier == till }
                    ?.displayName
                MpesaTransaction(
                    id = 0,
                    code = "MANUAL-TILL-$now",
                    type = TransactionType.BUY_GOODS,
                    amount = amount,
                    counterpartyName = knownName ?: "Till $till",
                    counterpartyPhone = till,
                    timestampMillis = now,
                    balance = null,
                    cost = null,
                    rawBody = ""
                )
            }
            PayMethod.POCHI -> {
                val phone = state.selectedPochi?.normalizedPhone
                    ?: UssdMenuBuilder.normalizePhoneNumber(state.pochiQuery)
                    ?: return null
                val name = state.selectedPochi?.name
                    ?: "Pochi La Biashara $phone"
                MpesaTransaction(
                    id = 0,
                    code = "MANUAL-POCHI-$now",
                    type = TransactionType.POCHI,
                    amount = amount,
                    counterpartyName = name,
                    counterpartyPhone = phone,
                    timestampMillis = now,
                    balance = null,
                    cost = null,
                    rawBody = ""
                )
            }
        }
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
        _ui.update {
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
        val method = state.method ?: return null
        val title = when (method) {
            PayMethod.PAYBILL -> "Paybill"
            PayMethod.TILL -> "Till"
            PayMethod.POCHI -> "Pochi La Biashara"
        }
        val detail = when (method) {
            PayMethod.PAYBILL -> {
                val name = state.merchantHits
                    .firstOrNull { it.identifier == state.businessNumber.trim() }
                    ?.displayName
                listOfNotNull(
                    name,
                    "Business ${state.businessNumber.trim()}",
                    "Acc ${state.accountNumber.trim()}"
                ).joinToString(" · ")
            }
            PayMethod.TILL -> {
                val name = state.merchantHits
                    .firstOrNull { it.identifier == state.tillNumber.trim() }
                    ?.displayName
                listOfNotNull(name, "Till ${state.tillNumber.trim()}").joinToString(" · ")
            }
            PayMethod.POCHI -> {
                state.selectedPochi?.let { c ->
                    listOfNotNull(c.name, c.normalizedPhone).joinToString(" · ")
                } ?: UssdMenuBuilder.normalizePhoneNumber(state.pochiQuery)
                ?: return null
            }
        }
        val amountLabel = formatKesMoney(amount)
        return Triple(title, detail, amountLabel)
    }

    private fun PayMoneyUiState.revalidate(): PayMoneyUiState {
        val detailsOk = when (method) {
            null -> false
            PayMethod.PAYBILL ->
                businessNumber.length >= 5 && accountNumber.isNotBlank()
            PayMethod.TILL -> tillNumber.length >= 5
            PayMethod.POCHI ->
                selectedPochi != null ||
                    UssdMenuBuilder.normalizePhoneNumber(pochiQuery) != null
        }
        val amountOk = RepeatTransactionViewModel.parseAmount(amountInput) != null
        val next = copy(
            detailsValid = detailsOk,
            amountValid = amountOk,
            guidanceMessage = computeDetailsGuidance(detailsOk)
        )
        return next.copy(canPay = computeCanPay(next))
    }

    private fun PayMoneyUiState.computeDetailsGuidance(detailsOk: Boolean): String? {
        if (detailsOk) return null
        return when (method) {
            null -> "Choose Paybill, Till, or Pochi to continue."
            PayMethod.PAYBILL -> paybillGuidance()
            PayMethod.TILL -> tillGuidance()
            PayMethod.POCHI -> pochiGuidance()
        }
    }

    private fun PayMoneyUiState.paybillGuidance(): String {
        val q = merchantQuery.trim()
        val label = q.take(28).let { if (q.length > 28) "$it…" else it }
        return when {
            q.isBlank() && businessNumber.isEmpty() ->
                "Search a saved business, or type the paybill number (5+ digits)."
            businessNumber.isEmpty() && merchantHits.isEmpty() && q.any { it.isLetter() } ->
                "No saved business matches \"$label\". Type the paybill number (5+ digits) instead."
            businessNumber.isEmpty() ->
                "Enter a full paybill number (at least 5 digits)."
            accountNumber.isBlank() ->
                "Enter the account / shop code for this paybill."
            else ->
                "Add paybill number and account to continue."
        }
    }

    private fun PayMoneyUiState.tillGuidance(): String {
        val q = merchantQuery.trim()
        val label = q.take(28).let { if (q.length > 28) "$it…" else it }
        return when {
            q.isBlank() && tillNumber.isEmpty() ->
                "Search a saved business, or type the till number (5+ digits)."
            tillNumber.isEmpty() && merchantHits.isEmpty() && q.any { it.isLetter() } ->
                "No saved business matches \"$label\". Type the till number (5+ digits) instead."
            tillNumber.isEmpty() ->
                "Enter a full till number (at least 5 digits)."
            else ->
                "Add a till number to continue."
        }
    }

    private fun PayMoneyUiState.pochiGuidance(): String {
        val q = pochiQuery.trim()
        return when {
            q.isBlank() ->
                "Search a saved Pochi recipient, or type their phone number."
            pochiContacts.isEmpty() &&
                UssdMenuBuilder.normalizePhoneNumber(q) == null &&
                q.any { it.isLetter() } ->
                "No saved match for \"$q\". Enter a full phone number to continue."
            UssdMenuBuilder.normalizePhoneNumber(q) == null ->
                "Enter a valid M-Pesa phone number to continue."
            else ->
                "Choose a recipient or enter a phone number."
        }
    }

    private fun computeCanPay(state: PayMoneyUiState): Boolean {
        if (!state.detailsValid || !state.amountValid || state.method == null) return false
        val probe = buildProbe(state) ?: return false
        return UssdMenuBuilder.canRepeat(probe)
    }

    private fun buildProbe(state: PayMoneyUiState): MpesaTransaction? {
        val method = state.method ?: return null
        val now = System.currentTimeMillis()
        return when (method) {
            PayMethod.PAYBILL -> {
                if (state.businessNumber.length < 5 || state.accountNumber.isBlank()) return null
                MpesaTransaction(
                    id = 0,
                    code = "PROBE",
                    type = TransactionType.PAYBILL,
                    amount = 1.0,
                    counterpartyName = null,
                    counterpartyPhone = state.businessNumber,
                    timestampMillis = now,
                    balance = null,
                    cost = null,
                    rawBody = "Account ${state.accountNumber}"
                )
            }
            PayMethod.TILL -> {
                if (state.tillNumber.length < 5) return null
                MpesaTransaction(
                    id = 0,
                    code = "PROBE",
                    type = TransactionType.BUY_GOODS,
                    amount = 1.0,
                    counterpartyName = null,
                    counterpartyPhone = state.tillNumber,
                    timestampMillis = now,
                    balance = null,
                    cost = null,
                    rawBody = ""
                )
            }
            PayMethod.POCHI -> {
                val phone = state.selectedPochi?.normalizedPhone
                    ?: UssdMenuBuilder.normalizePhoneNumber(state.pochiQuery)
                    ?: return null
                MpesaTransaction(
                    id = 0,
                    code = "PROBE",
                    type = TransactionType.POCHI,
                    amount = 1.0,
                    counterpartyName = state.selectedPochi?.name,
                    counterpartyPhone = phone,
                    timestampMillis = now,
                    balance = null,
                    cost = null,
                    rawBody = ""
                )
            }
        }
    }

    private fun MerchantHit.toPochiContact(): SendContact {
        val phone = UssdMenuBuilder.normalizePhoneNumber(identifier) ?: identifier
        return SendContact(
            transactionId = id,
            name = displayName,
            phone = phone,
            normalizedPhone = phone,
            sendCount = 1,
            lastSentMillis = 0L
        )
    }
}
