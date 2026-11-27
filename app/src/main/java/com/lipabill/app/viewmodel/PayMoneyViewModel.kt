package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.parser.PaybillPasteParser
import com.lipabill.app.data.parser.sanitizeAccountNumber
import com.lipabill.app.data.repository.MerchantHit
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.ussd.PaymentManualDetails
import com.lipabill.app.ussd.PaymentWallet
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.ussd.WalletMenuPlans
import com.lipabill.app.ui.util.appendAmountKey as applyAmountKey
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.ui.util.sanitizeAmountInput
import com.lipabill.app.ui.util.sanitizeDigits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PayMethod {
    PAYBILL,
    TILL,
    POCHI
}

/**
 * Pay tabs the active MNO can actually dial. Pochi is Safaricom-only;
 * Airtel / MTN get Till (merchant) and Paybill only.
 */
fun payMethodsFor(wallet: PaymentWallet?): List<PayMethod> = when (wallet) {
    PaymentWallet.SAFARICOM_MPESA -> listOf(
        PayMethod.TILL,
        PayMethod.PAYBILL,
        PayMethod.POCHI
    )
    PaymentWallet.AIRTEL_KENYA,
    PaymentWallet.AIRTEL_UGANDA,
    PaymentWallet.MTN_UGANDA,
    null -> listOf(PayMethod.TILL, PayMethod.PAYBILL)
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
    override val lines: PaymentLineSnapshot = InitialPaymentLines,
    val dialStarted: Boolean = false,
    val statusMessage: String? = null,
    /** What the user should do next on the details step (null when ready). */
    val guidanceMessage: String? = null,
    /** From-picker choice this sheet session (required for same-MNO dual-SIM). */
    val sessionPickedSubscriptionId: Int? = null
) : PaymentLineState

@OptIn(ExperimentalCoroutinesApi::class)
class PayMoneyViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LipaBillApp
    private val coordinator = app.repeatCoordinator

    private val _ui = MutableStateFlow(PayMoneyUiState())
    private val pochiQuery = MutableStateFlow("")
    private val merchantQuery = MutableStateFlow("")

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
        combine(_ui, pochiMerchantHits, pochiQuery, merchantHits) {
                state, pochiMerchants, pochiQ, hits ->
            PochiDraft(state, pochiMerchants, pochiQ, hits)
        },
        merchantQuery
    ) { draft, merchantQ ->
        val trimmed = draft.pochiQuery.trim()
        val learnedPochi = draft.pochiMerchants.map { it.toPochiContact() }
        val filteredPochi = SendMoneyViewModel.filterRecipientSearch(learnedPochi, trimmed)
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
        val hits: List<MerchantHit>
    )

    init {
        refreshGates()
    }

    fun refreshGates() {
        val ctx = getApplication<Application>()
        val lines = ctx.paymentLineSnapshot(coordinator, app.securePreferences)
        _ui.update { state ->
            state.copy(lines = lines).dropUnsupportedMethod().revalidate()
        }
    }

    fun selectPayLine(subscriptionId: Int) {
        val state = _ui.value
        setPreferredPayLine(
            app.securePreferences,
            state.region,
            state.simLines,
            subscriptionId
        ) ?: return
        _ui.update { it.copy(sessionPickedSubscriptionId = subscriptionId) }
        refreshGates()
    }

    fun applySiblingRetry(subscriptionId: Int) {
        selectPayLine(subscriptionId)
        _ui.update {
            it.copy(
                dialStarted = false,
                statusMessage = "Switched to the other line — confirm again."
            ).revalidate()
        }
    }

    fun selectMethod(method: PayMethod) {
        if (method !in payMethodsFor(_ui.value.activeWallet)) return
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

    /** Clears a method the new wallet cannot dial (e.g. Pochi after switching to Airtel). */
    private fun PayMoneyUiState.dropUnsupportedMethod(): PayMoneyUiState {
        val selected = method ?: return this
        if (selected in payMethodsFor(activeWallet)) return this
        if (selected == PayMethod.POCHI) {
            this@PayMoneyViewModel.pochiQuery.value = ""
        }
        return copy(
            method = null,
            selectedPochi = null,
            dialStarted = false,
            statusMessage = null
        )
    }

    fun clearMethod() {
        pochiQuery.value = ""
        merchantQuery.value = ""
        _ui.update {
            PayMoneyUiState(lines = it.lines)
        }
    }

    private val amountResume = AmountStepResume()

    /** Full reset when the sheet is dismissed. */
    fun resetSession() {
        clearMethod()
        amountResume.clear()
        com.lipabill.app.ussd.SiblingLineRetry.clear()
        _ui.update { it.copy(sessionPickedSubscriptionId = null) }
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
        refreshGates()
        if (method !in payMethodsFor(_ui.value.activeWallet)) return
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
        amountResume.mark()
    }

    fun consumeResumeOnAmountStep(): Boolean = amountResume.consume()

    fun setMerchantQuery(value: String) {
        merchantQuery.value = value
        val digits = sanitizeDigits(value)
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
        merchantQuery.value = if (hit.type == TransactionType.PAYBILL) {
            hit.identifier
        } else {
            hit.displayName
        }
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
            it.copy(accountNumber = sanitizeAccountNumber(value))
        }
    }

    /**
     * Fills Paybill + account (and amount when clearly present) from pasted text.
     * @return true when both paybill and account were found.
     */
    fun applyPaybillPaste(raw: String): Boolean {
        val draft = PaybillPasteParser.parse(raw) ?: return false
        selectMethod(PayMethod.PAYBILL)
        merchantQuery.value = draft.businessNumber
        val amount = draft.amountInput.orEmpty()
        _ui.update {
            it.copy(
                businessNumber = draft.businessNumber,
                accountNumber = draft.accountNumber,
                amountInput = amount,
                dialStarted = false,
                statusMessage = null
            )
        }
        if (amount.isNotBlank()) {
            amountResume.mark()
        }
        return true
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

    fun setAmountInput(value: String) {
        _ui.update { it.copy(amountInput = sanitizeAmountInput(value)) }
    }

    fun appendAmountKey(key: String) {
        _ui.update { it.copy(amountInput = applyAmountKey(it.amountInput, key)) }
    }

    fun deleteAmountKey() {
        _ui.update { it.copy(amountInput = it.amountInput.dropLast(1)) }
    }

    fun buildSyntheticTransaction(): MpesaTransaction? {
        val state = uiState.value
        val amount = RepeatTransactionViewModel.parseAmount(state.amountInput) ?: return null
        val method = state.method ?: return null
        if (method !in payMethodsFor(state.activeWallet)) return null
        val now = System.currentTimeMillis()
        return when (method) {
            PayMethod.PAYBILL -> {
                val business = state.businessNumber.trim()
                val account = state.accountNumber.trim()
                if (business.length < 5 || account.isEmpty()) return null
                val knownName = state.merchantHits
                    .firstOrNull { it.identifier == business }
                    ?.displayName
                manualPayment(
                    kind = "PB",
                    type = TransactionType.PAYBILL,
                    amount = amount,
                    name = knownName ?: "Paybill $business",
                    phone = business,
                    rawBody = "Account $account",
                    now = now,
                    source = state.activeWallet?.ledgerSource ?: MoneySource.MPESA
                )
            }
            PayMethod.TILL -> {
                val till = state.tillNumber.trim()
                if (till.length < 5) return null
                val knownName = state.merchantHits
                    .firstOrNull { it.identifier == till }
                    ?.displayName
                manualPayment(
                    kind = "TILL",
                    type = TransactionType.BUY_GOODS,
                    amount = amount,
                    name = knownName ?: "Till $till",
                    phone = till,
                    now = now,
                    source = state.activeWallet?.ledgerSource ?: MoneySource.MPESA
                )
            }
            PayMethod.POCHI -> {
                val phone = state.selectedPochi?.normalizedPhone
                    ?: UssdMenuBuilder.normalizePhoneNumber(state.pochiQuery)
                    ?: return null
                val name = state.selectedPochi?.name
                    ?: "Pochi La Biashara $phone"
                manualPayment(
                    kind = "POCHI",
                    type = TransactionType.POCHI,
                    amount = amount,
                    name = name,
                    phone = phone,
                    now = now,
                    source = state.activeWallet?.ledgerSource ?: MoneySource.MPESA
                )
            }
        }
    }

    suspend fun prepare(): RepeatTransactionCoordinator.PreparedRepeat? =
        coordinator.preparePayment(buildSyntheticTransaction(), uiState.value.amountInput)

    fun startDial(prepared: RepeatTransactionCoordinator.PreparedRepeat): Boolean {
        refreshGates()
        val state = uiState.value
        if (state.requiresExplicitLinePick && state.sessionPickedSubscriptionId == null) {
            return false
        }
        val dialSubId = state.sessionPickedSubscriptionId ?: state.selectedSubscriptionId
        val started = coordinator.startPaymentDial(
            prepared,
            app.securePreferences,
            state.region,
            state.simLines,
            dialSubId,
            preferredSource = state.activeWallet?.ledgerSource
        ) ?: return false
        com.lipabill.app.ussd.SiblingLineRetry.noteTried(started.subscriptionId)
        _ui.update {
            it.copy(
                dialStarted = true,
                statusMessage = started.status,
                lines = it.lines.after(started),
                sessionPickedSubscriptionId = started.subscriptionId
            )
        }
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
        return PaymentManualDetails.format(
            typeLabel = summary?.first ?: (state.method?.name ?: "Pay"),
            toLine = summary?.second ?: "",
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
        if (!needsPhoneStatePermission && !hasEligiblePaySim) {
            return missingPaySimMessage.ifBlank { "Insert a SIM for this wallet to pay." }
        }
        if (requiresExplicitLinePick && sessionPickedSubscriptionId == null) {
            return explicitLinePickMessage ?: "Pick which SIM pays."
        }
        if (detailsOk) return null
        return when (method) {
            null -> {
                val labels = payMethodsFor(activeWallet).map { it.choiceLabel() }
                val choices = when (labels.size) {
                    0 -> "a method"
                    1 -> labels[0]
                    2 -> "${labels[0]} or ${labels[1]}"
                    else -> labels.dropLast(1).joinToString(", ") + ", or " + labels.last()
                }
                "Choose $choices to continue."
            }
            PayMethod.PAYBILL -> paybillGuidance()
            PayMethod.TILL -> tillGuidance()
            PayMethod.POCHI -> pochiGuidance()
        }
    }

    private fun PayMethod.choiceLabel(): String = when (this) {
        PayMethod.PAYBILL -> "Paybill"
        PayMethod.TILL -> "Till"
        PayMethod.POCHI -> "Pochi"
    }

    private fun PayMoneyUiState.paybillGuidance(): String =
        savedNumberGuidance(businessNumber, "paybill") {
            if (accountNumber.isBlank()) {
                "Enter the account number for this paybill."
            } else {
                "Add the paybill and account number to continue."
            }
        }

    private fun PayMoneyUiState.tillGuidance(): String =
        savedNumberGuidance(tillNumber, "till") {
            "Add a till number to continue."
        }

    private fun PayMoneyUiState.savedNumberGuidance(
        number: String,
        noun: String,
        afterNumber: () -> String
    ): String {
        val q = merchantQuery.trim()
        val label = q.take(28).let { if (q.length > 28) "$it…" else it }
        return when {
            q.isBlank() && number.isEmpty() ->
                "Search a saved business, or type the $noun number (5+ digits)."
            number.isEmpty() && merchantHits.isEmpty() && q.any { it.isLetter() } ->
                "No saved business matches \"$label\". Type the $noun number (5+ digits) instead."
            number.isEmpty() ->
                "Enter a full $noun number (at least 5 digits)."
            else -> afterNumber()
        }
    }

    private fun PayMoneyUiState.pochiGuidance(): String {
        val q = pochiQuery.trim()
        return when {
            q.isBlank() ->
                "Search a saved Pochi, choose a contact, or type their phone number."
            pochiContacts.isEmpty() &&
                UssdMenuBuilder.normalizePhoneNumber(q) == null &&
                q.any { it.isLetter() } ->
                "No saved match for \"$q\". Choose a contact, or enter a full phone number."
            UssdMenuBuilder.normalizePhoneNumber(q) == null ->
                "Enter a valid phone number to continue."
            else ->
                "Choose a recipient or enter a phone number."
        }
    }

    private fun computeCanPay(state: PayMoneyUiState): Boolean {
        if (!state.detailsValid || !state.amountValid || state.method == null) return false
        if (state.method !in payMethodsFor(state.activeWallet)) return false
        if (state.dialStarted) return false
        val simReady = state.needsPhoneStatePermission || state.hasEligiblePaySim
        if (!simReady) return false
        if (state.requiresExplicitLinePick && state.sessionPickedSubscriptionId == null) return false
        val wallet = state.activeWallet ?: return false
        val probe = buildProbe(state) ?: return false
        return WalletMenuPlans.canRepeat(wallet, probe)
    }

    private fun buildProbe(state: PayMoneyUiState): MpesaTransaction? {
        val method = state.method ?: return null
        if (method !in payMethodsFor(state.activeWallet)) return null
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
