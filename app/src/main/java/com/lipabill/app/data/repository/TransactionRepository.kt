package com.lipabill.app.data.repository

import com.lipabill.app.data.local.dao.ClassificationRepairRow
import com.lipabill.app.BuildConfig
import com.lipabill.app.data.DemoLedger
import com.lipabill.app.data.local.dao.TransactionDao
import com.lipabill.app.data.local.entity.TransactionEntity
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.data.parser.KenyanConfirmationParser
import com.lipabill.app.data.parser.MpesaSmsParser
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.sms.ConfirmationIngestion
import com.lipabill.app.data.sms.InboxScan
import com.lipabill.app.data.sms.LedgerNotifier
import com.lipabill.app.data.sms.MpesaSmsFilter
import com.lipabill.app.data.sms.SmsInboxReader
import com.lipabill.app.ussd.PendingPaymentReceipt
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.viewmodel.idsMatchingPayeeKey
import com.lipabill.app.viewmodel.payeeKey
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class SendContact(
    val transactionId: Long,
    val name: String?,
    val phone: String,
    val normalizedPhone: String,
    val fromPhoneBook: Boolean = false,
    /** How many past Send Money SMS matched this phone (consolidated). */
    val sendCount: Int = 1,
    val lastSentMillis: Long = 0L
)

/** Result of renaming every row that shares a payee key. */
data class LinkedPayeeRenameResult(
    val linkedCount: Int,
    val renamedCount: Int,
    val previousPayeeKey: String?,
    val payeeKey: String?
)

class TransactionRepository(
    private val dao: TransactionDao,
    private val inboxReader: SmsInboxReader,
    private val securePreferences: SecurePreferences,
    private val merchantDirectory: MerchantDirectory,
    private val onNewTransaction: (MpesaTransaction) -> Unit = {},
    var afterInsert: (suspend () -> Unit)? = null,
    /** Narrows the user's picks to what may be imported now (region ∩ enabled; SIM is dial-only). */
    private val usableSources: (AppRegion, Set<MoneySource>) -> Set<MoneySource> = { _, chosen -> chosen }
) {

    private val rescanLock = Mutex()

    /**
     * Bumped when country or enabled rails change so an in-flight scan cannot
     * mark the inbox "caught up" and skip the full re-read that must follow.
     */
    private val inboxScanEpoch = AtomicInteger(0)

    /**
     * Drop high-water marks so the next import re-reads the whole inbox for
     * newly enabled wallets and banks.
     */
    fun invalidateInboxScanProgress() {
        inboxScanEpoch.incrementAndGet()
        securePreferences.resetInboxScanForRegionChange()
    }

    fun observeTransactions(
        query: String = "",
        type: TransactionType? = null,
        limit: Int = LIST_LIMIT
    ): Flow<List<MpesaTransaction>> {
        val trimmed = query.trim()
        val source = if (trimmed.isEmpty()) {
            dao.observeRecent(limit)
        } else {
            dao.observeSearch(trimmed, type, limit)
        }
        return mapListItems(source)
    }

    /**
     * Map Room rows to list items. Phones are persisted at insert / [linkPhonesByName];
     * do not re-enrich the whole list on every observe (that doubled work with nameLinks).
     */
    private fun mapListItems(source: Flow<List<TransactionEntity>>): Flow<List<MpesaTransaction>> =
        source.map { list -> list.map { it.toListItem() } }.flowOn(Dispatchers.Default)

    /**
     * Past Send Money recipients consolidated by normalized phone.
     * Sorted by send frequency (desc), then most recent send.
     */
    fun observeSendContacts(limit: Int = SEND_SCAN_LIMIT): Flow<List<SendContact>> =
        dao.observeSentWithPhone(limit)
            .map { rows -> consolidateSendContacts(rows) }
            .flowOn(Dispatchers.Default)

    fun observeMerchantSearch(
        type: TransactionType,
        query: String,
        limit: Int = 12
    ): Flow<List<MerchantHit>> = merchantDirectory.observeSearch(type, query, limit)

    /** Full history for one ledger. Money and Fuliza are not mixed in this query. */
    fun observeWallet(fuliza: Boolean): Flow<List<MpesaTransaction>> =
        mapListItems(dao.observeWallet(if (fuliza) 1 else 0))

    /** Newest [limit] rows for Home — no SMS bodies. */
    fun observeWalletList(fuliza: Boolean, limit: Int = HOME_LIST_LIMIT): Flow<List<MpesaTransaction>> =
        mapListItems(dao.observeWalletList(if (fuliza) 1 else 0, limit))

    /** Newest [limit] ledger rows from [sources] only — Home's window, no SMS bodies. */
    fun observeLedgerWindow(sources: Set<MoneySource>, limit: Int): Flow<List<MpesaTransaction>> =
        mapListItems(dao.observeLedgerWindow(sources.map { it.name }, limit))

    /** Ledger rows per source over all history. */
    fun observeSourceCounts(): Flow<Map<MoneySource, Int>> =
        dao.observeSourceCounts().map { rows -> rows.associate { it.source to it.n } }

    /** Recent Send Money rows for fee / monthly counts on the Send sheet. */
    fun observeRecentSends(limit: Int = SEND_HISTORY_LIMIT): Flow<List<MpesaTransaction>> =
        mapListItems(dao.observeRecentSends(limit))

    /** Ledger rows since [sinceMillis], no SMS bodies. */
    suspend fun ledgerSince(sinceMillis: Long): List<MpesaTransaction> = withContext(Dispatchers.IO) {
        dao.ledgerSince(sinceMillis).map { it.toListItem() }
    }

    /** Newest balance per source, for the home balance switch and money sheets. */
    fun observeLatestBalances(): Flow<Map<MoneySource, Double>> =
        dao.observeLatestBalances()
            .map { rows -> rows.associate { it.source to it.balance } }
            .flowOn(Dispatchers.Default)

    suspend fun latestMpesaBalance(): Double? = withContext(Dispatchers.IO) {
        dao.latestMpesaBalance()
    }

    fun observeById(id: Long): Flow<MpesaTransaction?> =
        dao.observeById(id).map { it?.toDomain() }.flowOn(Dispatchers.Default)

    /**
     * Notification taps prefer the stored row id, then the confirmation code
     * when that id is missing or belongs to a different payment.
     */
    suspend fun resolveReceiptId(id: Long?, code: String?): Long? = withContext(Dispatchers.IO) {
        if (id != null && id > 0L) {
            val stored = dao.codeForId(id)
            if (stored != null && (code.isNullOrBlank() || stored == code)) return@withContext id
        }
        val trimmed = code?.trim().orEmpty()
        if (trimmed.isNotEmpty()) dao.idForCode(trimmed) else null
    }

    suspend fun upsert(tx: MpesaTransaction): Boolean = withContext(Dispatchers.IO) {
        // Skip non-confirmation SMS (promos, PIN notices, etc.)
        if (tx.rawBody.isNotBlank() &&
            !ConfirmationIngestion.shouldAcceptBody(
                tx.source,
                tx.rawBody,
                securePreferences.appRegion
            )
        ) {
            return@withContext false
        }
        val enriched = if (tx.source == MoneySource.MPESA) {
            merchantDirectory.enrichIncomingSms(tx)
        } else {
            tx
        }
        val withPhone = enrichSingleFromDirectory(enriched)
        val rowId = dao.insertIgnore(TransactionEntity.fromDomain(withPhone))
        if (rowId != -1L) {
            if (LedgerNotifier.isFresh(withPhone.timestampMillis)) {
                onNewTransaction(withPhone.copy(id = rowId))
            }
            afterInsert?.invoke()
            return@withContext true
        }
        // Already present — refresh money fields from the latest parse (fixes bad amounts).
        dao.updateParsedMoneyByCode(
            source = withPhone.source,
            code = withPhone.code,
            amount = withPhone.amount,
            balance = withPhone.balance,
            cost = withPhone.cost,
            rawBody = withPhone.rawBody
        )
        false
    }

    suspend fun rememberMerchantPayment(tx: MpesaTransaction, amount: Double) {
        merchantDirectory.rememberOutgoing(tx, amount)
    }

    /**
     * Import confirmations that are not already stored.
     * The first scan reads the whole inbox. Later scans only ask for messages
     * newer than the last one considered, and rows already saved are left untouched.
     * Pass [forceFullHistory] after enabling a new wallet/bank so older SMS for
     * that rail are not skipped by the high-water mark.
     * @return number of newly inserted rows
     */
    suspend fun rescanInbox(forceFullHistory: Boolean = false): Int = withContext(Dispatchers.IO) {
        rescanLock.withLock { importNewConfirmations(forceFullHistory = forceFullHistory) }
    }

    /**
     * App foreground / resume: one-shot repairs once, then a quiet inbox import.
     * Shares [rescanLock] with [rescanInbox] so SMS watcher and lifecycle do not stack.
     */
    suspend fun catchUpOnForeground(): Int = withContext(Dispatchers.IO) {
        rescanLock.withLock {
            runOneShotLedgerRepairs()
            importNewConfirmations(forceFullHistory = false)
        }
    }

    /**
     * Debug / promo only. Lands a fake confirmation that matches [snapshot]
     * so ProcessingReceipt can show Confirmed and Home gains a new row.
     * Never dials USSD. Body avoids real “Confirmed.” / balance copy for OCR gates.
     */
    suspend fun insertDemoPaymentResult(
        snapshot: PendingPaymentReceipt.Snapshot
    ): MpesaTransaction? {
        if (!BuildConfig.DEBUG) return null
        return withContext(Dispatchers.IO) {
            val prior = dao.latestMpesaBalance()
            val amount = snapshot.amount
            val nextBalance = prior?.let { it - amount }
            val code = snapshot.demoLedgerCode?.takeIf { it.isNotBlank() }
                ?: "DEMO-${System.currentTimeMillis()}"
            val party = snapshot.counterpartyName ?: snapshot.counterpartyPhone ?: "Demo"
            val phone = snapshot.counterpartyPhone
            val typeLabel = when (snapshot.type) {
                TransactionType.SENT -> "sent to"
                TransactionType.BUY_GOODS -> "paid to till"
                TransactionType.PAYBILL -> "paid to paybill"
                TransactionType.POCHI -> "sent to Pochi"
                else -> "paid to"
            }
            val tx = MpesaTransaction(
                code = code,
                type = snapshot.type,
                amount = amount,
                counterpartyName = party,
                counterpartyPhone = phone,
                timestampMillis = System.currentTimeMillis(),
                balance = nextBalance,
                cost = null,
                rawBody = "Demo payment $code. Ksh${"%.0f".format(amount)} $typeLabel $party" +
                    (phone?.let { " $it" } ?: "") +
                    ". Balance shown in app only. Not a real message. Not a PIN.",
                source = snapshot.wallet?.ledgerSource ?: MoneySource.MPESA
            )
            dao.insertIgnore(TransactionEntity.fromDomain(tx))
            if (snapshot.type == TransactionType.PAYBILL ||
                snapshot.type == TransactionType.BUY_GOODS
            ) {
                merchantDirectory.rememberOutgoing(tx, amount)
            }
            tx
        }
    }

    /**
     * Debug captures only. Skipped when SMS is on (real inbox would mix in)
     * or when the ledger already has a row. Does not open a PIN screen.
     */
    suspend fun seedDemoLedgerIfEmpty() {
        if (!BuildConfig.DEBUG) return
        if (!securePreferences.firstRunSetupDone) return
        if (inboxReader.hasSmsPermission()) return
        if (securePreferences.payPhone.isNullOrBlank()) {
            securePreferences.payPhone = DemoLedger.REQUEST_PHONE
        }
        withContext(Dispatchers.IO) {
            if (dao.count() > 0) return@withContext
            val rows = DemoLedger.rows(System.currentTimeMillis())
            dao.insertIgnoreAll(rows)
            rows.forEach { row ->
                if (row.type == TransactionType.PAYBILL || row.type == TransactionType.BUY_GOODS) {
                    val tx = row.toDomain()
                    val amount = tx.amount ?: return@forEach
                    if (!tx.counterpartyPhone.isNullOrBlank()) {
                        merchantDirectory.rememberOutgoing(tx, amount)
                    }
                }
            }
        }
    }

    /** One-shot purge/repair/reclassify — prefs flags make repeats free after the first pass. */
    private suspend fun runOneShotLedgerRepairs() {
        purgeNonConfirmationsIfNeeded()
        repairParsedAmountsIfNeeded()
        reclassifySavingsIfNeeded()
        reclassifyUnknownPartiesIfNeeded()
        reclassifyBankDirectionsIfNeeded()
        reclassifyBankDetailsIfNeeded()
    }

    private suspend fun importNewConfirmations(forceFullHistory: Boolean = false): Int {
        val epochAtStart = inboxScanEpoch.get()
        // Without SMS access, leave prior rows alone and do not mark backfill complete.
        if (!inboxReader.hasSmsPermission()) {
            return 0
        }
        // Re-read prefs under the lock so a settings change that invalidated the
        // scan is visible even when this pass was queued before that change.
        val region = securePreferences.appRegion
        val enabled = usableSources(region, securePreferences.enabledLedgerSources(region))
        // Nothing enabled for this country: leave the high-water mark alone.
        if (enabled.isEmpty()) return 0
        // Bank-only (or any subset) full pass leaves older SMS for later rails behind
        // the high-water mark — re-read when enabled includes a rail never fully scanned.
        val fullHistory = InboxScan.needsFullHistory(
            forceFullHistory = forceFullHistory,
            walletRailsScanDone = securePreferences.walletRailsInboxScanDone,
            enabled = enabled,
            alreadyScanned = securePreferences.inboxFullyScannedSources()
        )
        val since = if (fullHistory) {
            null
        } else {
            InboxScan.newerThanMillis(
                historyImported = securePreferences.inboxHistoryImported,
                highWaterMillis = securePreferences.inboxHighWaterMillis
            )
        }
        val incoming = inboxReader.readMessages(
            newerThanMillis = since,
            region = region,
            enabled = enabled
        )
        val known = dao.allSourceCodes().toHashSet()
        var newestSeen = securePreferences.inboxHighWaterMillis
        var directory: Map<String, String>? = null
        val pending = ArrayList<MpesaTransaction>()
        // Settlement-twin receipt ids: a Lipa-na-KCB merchant receipt replaces its twin either way round.
        val batchTwinCodes = HashSet<String>()
        var storedTwinCodes: Set<String>? = null
        for (sms in incoming) {
            if (sms.dateMillis > newestSeen) newestSeen = sms.dateMillis
            val source = MoneySource.fromSender(sms.address, region) ?: continue
            val lipaNaKcb = KenyanConfirmationParser.isLipaNaKcbMerchantSms(sms.body)
            val settlementTwin = KenyanConfirmationParser.isSettlementTwinSms(sms.body)
            val quickCode = if (source == MoneySource.MPESA &&
                region == AppRegion.KENYA &&
                !lipaNaKcb
            ) {
                MpesaSmsParser.receiptCode(sms.body)
            } else {
                KenyanConfirmationParser.receiptCode(sms.body)
            }
            if (quickCode != null && "${source.name}:$quickCode" in known) {
                val twins = if (lipaNaKcb && quickCode !in batchTwinCodes) {
                    storedTwinCodes ?: storedSettlementTwinCodes().also { storedTwinCodes = it }
                } else {
                    batchTwinCodes
                }
                // Only the merchant receipt may take a code its settlement twin claimed first.
                if (!lipaNaKcb || quickCode !in twins) continue
                known.remove("${source.name}:$quickCode")
            }
            // Bank settlement twin of an already-stored Lipa-na-KCB merchant receipt.
            if (settlementTwin && ConfirmationIngestion.sharesKnownReceipt(sms.body, known)) {
                continue
            }
            val parsed = ConfirmationIngestion.acceptAndParse(
                sms.address,
                sms.body,
                sms.dateMillis,
                region,
                enabled
            ) ?: continue
            if (!known.add(ConfirmationIngestion.sourceKey(parsed))) continue
            for (code in KenyanConfirmationParser.allReceiptCodes(sms.body)) {
                known.add("${source.name}:$code")
            }
            val enriched = if (parsed.source == MoneySource.MPESA &&
                InboxScan.mayMatchPendingDial(parsed.timestampMillis)
            ) {
                merchantDirectory.enrichIncomingSms(parsed)
            } else {
                parsed
            }
            val withPhone = if (enriched.counterpartyPhone.isNullOrBlank()) {
                val names = directory ?: buildNamePhoneDirectory(NAME_LINK_SCAN_LIMIT)
                    .also { directory = it }
                CounterpartyNameLinker.enrich(enriched, names)
            } else {
                enriched
            }
            pending.add(withPhone)
            if (settlementTwin) batchTwinCodes += KenyanConfirmationParser.allReceiptCodes(sms.body)
        }
        var inserted = 0
        val toStore = ConfirmationIngestion.dedupeLipaNaKcbTwins(pending)
        if (toStore.isNotEmpty()) {
            // Merchant SMS landed after a twin was already stored — drop the twin rows.
            for (tx in toStore) {
                if (KenyanConfirmationParser.isLipaNaKcbMerchantSms(tx.rawBody)) {
                    deleteSettlementTwinsSharing(tx.rawBody)
                }
            }
            val ids = dao.insertIgnoreAll(toStore.map { TransactionEntity.fromDomain(it) })
            toStore.forEachIndexed { index, tx ->
                val rowId = ids.getOrNull(index) ?: return@forEachIndexed
                if (rowId == -1L) return@forEachIndexed
                inserted++
                if (LedgerNotifier.isFresh(tx.timestampMillis)) {
                    onNewTransaction(tx.copy(id = rowId))
                }
            }
        }
        // A newer country/rails change landed while we worked — leave watermarks
        // alone so the forced follow-up scan still reads the full inbox.
        if (epochAtStart != inboxScanEpoch.get()) {
            if (inserted > 0) {
                linkPhonesByName()
                afterInsert?.invoke()
            }
            return inserted
        }
        securePreferences.smsBackfillDone = true
        securePreferences.markInboxCaughtUp(newestSeen)
        if (fullHistory) {
            securePreferences.walletRailsInboxScanDone = true
            securePreferences.markInboxFullyScanned(enabled)
        }
        if (inserted > 0) {
            linkPhonesByName()
            afterInsert?.invoke()
        }
        return inserted
    }

    suspend fun backfillIfNeeded(): Int {
        runOneShotLedgerRepairs()
        if (securePreferences.smsBackfillDone && dao.count() > 0) {
            return 0
        }
        // Never treat "no SMS permission" as a finished backfill — that would skip
        // re-import after the user grants access, leaving an empty history.
        if (!inboxReader.hasSmsPermission()) {
            return 0
        }
        return rescanInbox()
    }

    /**
     * Re-parse [TransactionEntity.rawBody] and fix stored amount/balance/cost when thousand
     * separators were previously misread (e.g. space/NBSP grouped amounts).
     */
    suspend fun repairParsedAmountsIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (securePreferences.amountParseRepairDone) return@withContext 0
        val fixed = repairParsedAmounts()
        securePreferences.amountParseRepairDone = true
        fixed
    }

    private suspend fun repairParsedAmounts(): Int = withContext(Dispatchers.IO) {
        val rows = dao.getAllForAmountRepair()
        var fixed = 0
        for (row in rows) {
            if (row.source != MoneySource.MPESA) continue
            if (row.rawBody.isBlank()) continue
            if (!MpesaSmsFilter.isTransactionConfirmation(row.rawBody)) continue
            val reparsed = MpesaSmsParser.parse(row.rawBody, row.timestampMillis)
            val amountChanged = !moneyEquals(row.amount, reparsed.amount)
            val balanceChanged = !moneyEquals(row.balance, reparsed.balance)
            val costChanged = !moneyEquals(row.cost, reparsed.cost)
            if (!amountChanged && !balanceChanged && !costChanged) continue
            dao.updateParsedMoney(
                id = row.id,
                amount = reparsed.amount ?: row.amount,
                balance = reparsed.balance ?: row.balance,
                cost = reparsed.cost ?: row.cost
            )
            fixed++
        }
        fixed
    }

    /**
     * Older parses filed M-Shwari, airtime, agent cash-in, and "sent to … for account"
     * under Sent or Unknown. Re-read stored confirmations once. Balance checks are removed.
     */
    suspend fun reclassifySavingsIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (securePreferences.savingsClassifyRepairDone) return@withContext 0
        val fixed = reclassifySavings()
        securePreferences.savingsClassifyRepairDone = true
        fixed
    }

    private suspend fun reclassifySavings(): Int = withContext(Dispatchers.IO) {
        val rows = dao.getAllForClassification()
        var fixed = 0
        for (row in rows) {
            if (row.source != MoneySource.MPESA) continue
            if (row.rawBody.isBlank()) continue
            if (MpesaSmsFilter.isBalanceEnquiry(row.rawBody)) {
                dao.deleteByIds(listOf(row.id))
                fixed++
                continue
            }
            if (!MpesaSmsFilter.isTransactionConfirmation(row.rawBody)) continue
            val reparsed = MpesaSmsParser.parse(row.rawBody, row.timestampMillis)
            val shouldUpdate = when {
                reparsed.type == TransactionType.UNKNOWN -> false
                reparsed.type == row.type -> false
                row.type == TransactionType.UNKNOWN -> true
                row.type == TransactionType.SENT &&
                    reparsed.type != TransactionType.SENT -> true
                reparsed.type == TransactionType.SAVINGS -> true
                else -> false
            }
            if (!shouldUpdate) continue
            dao.updateParty(
                id = row.id,
                type = reparsed.type,
                name = reparsed.counterpartyName ?: row.counterpartyName,
                phone = reparsed.counterpartyPhone
            )
            fixed++
        }
        fixed
    }

    /**
     * Older parses left the party blank when the confirmation verb was unfamiliar.
     * Re-read those SMS once and fill the name, phone, and direction.
     */
    suspend fun reclassifyUnknownPartiesIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (securePreferences.unknownPartyRepairDone) return@withContext 0
        val fixed = reclassifyUnknownParties()
        securePreferences.unknownPartyRepairDone = true
        fixed
    }

    private suspend fun reclassifyUnknownParties(): Int = withContext(Dispatchers.IO) {
        val rows = dao.getAllForClassification()
        var fixed = 0
        for (row in rows) {
            if (row.source != MoneySource.MPESA) continue
            if (row.type != TransactionType.UNKNOWN) continue
            if (row.rawBody.isBlank()) continue
            if (!MpesaSmsFilter.isTransactionConfirmation(row.rawBody)) continue
            val reparsed = MpesaSmsParser.parse(row.rawBody, row.timestampMillis)
            if (updatePartyIfBetter(row, reparsed, typeChanged = reparsed.type != TransactionType.UNKNOWN)) fixed++
        }
        fixed
    }

    /**
     * Bank and non-Kenya-M-Pesa wallet rows: re-read SMS wording so send/receive
     * direction matches debit/credit language (one-shot after classify fix).
     */
    suspend fun reclassifyBankDirectionsIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (securePreferences.bankDirectionRepairDone) return@withContext 0
        val fixed = reclassifyBankDirections()
        securePreferences.bankDirectionRepairDone = true
        fixed
    }

    private suspend fun reclassifyBankDirections(): Int = withContext(Dispatchers.IO) {
        val rows = dao.getAllForClassification()
        var fixed = 0
        for (row in rows) {
            if (row.source == MoneySource.MPESA) continue
            if (row.rawBody.isBlank()) continue
            val nextType = KenyanConfirmationParser.classify(row.rawBody)
            if (nextType == TransactionType.UNKNOWN || nextType == row.type) continue
            dao.updateParty(
                id = row.id,
                type = nextType,
                name = row.counterpartyName,
                phone = null
            )
            fixed++
        }
        fixed + purgeLipaNaKcbSettlementTwins()
    }

    /**
     * Bank / non-M-Pesa rows: re-parse SMS for merchant, till, account holder, and
     * corrected send/receive type (one-shot after richer bank party extraction).
     */
    suspend fun reclassifyBankDetailsIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (securePreferences.bankDetailRepairDone) return@withContext 0
        val fixed = reclassifyBankDetails()
        securePreferences.bankDetailRepairDone = true
        fixed
    }

    private suspend fun reclassifyBankDetails(): Int = withContext(Dispatchers.IO) {
        val rows = dao.getAllForClassification()
        var fixed = 0
        for (row in rows) {
            if (row.source == MoneySource.MPESA) continue
            if (row.rawBody.isBlank()) continue
            val reparsed = KenyanConfirmationParser.parse(
                row.rawBody,
                row.timestampMillis,
                row.source
            ) ?: continue
            val typeChanged = reparsed.type != TransactionType.UNKNOWN && reparsed.type != row.type
            if (updatePartyIfBetter(row, reparsed, typeChanged)) fixed++
        }
        fixed
    }

    /**
     * Writes the name, phone and (when [typeChanged]) type a re-read SMS found.
     * False when the re-read added nothing, so nothing was written.
     */
    private suspend fun updatePartyIfBetter(
        row: ClassificationRepairRow,
        reparsed: MpesaTransaction,
        typeChanged: Boolean
    ): Boolean {
        val name = reparsed.counterpartyName?.takeIf { it.isNotBlank() }
        val phone = reparsed.counterpartyPhone?.takeIf { it.isNotBlank() }
        val nameChanged = name != null && name != row.counterpartyName
        if (!typeChanged && !nameChanged && phone == null) return false
        dao.updateParty(
            id = row.id,
            type = if (typeChanged) reparsed.type else row.type,
            name = name,
            phone = phone
        )
        return true
    }

    /** Drop bank suspense twins that share a Lipa-na-KCB ref with a merchant receipt. */
    private suspend fun purgeLipaNaKcbSettlementTwins(): Int {
        val rows = dao.getAllForClassification()
        val merchants = rows.filter { KenyanConfirmationParser.isLipaNaKcbMerchantSms(it.rawBody) }
        if (merchants.isEmpty()) return 0
        val merchantCodes = merchants
            .flatMap { KenyanConfirmationParser.allReceiptCodes(it.rawBody) }
            .toHashSet()
        if (merchantCodes.isEmpty()) return 0
        val doomed = rows.mapNotNull { row ->
            if (!KenyanConfirmationParser.isSettlementTwinSms(row.rawBody)) return@mapNotNull null
            val codes = KenyanConfirmationParser.allReceiptCodes(row.rawBody)
            if (codes.any { it in merchantCodes }) row.id else null
        }
        if (doomed.isEmpty()) return 0
        dao.deleteByIds(doomed)
        return doomed.size
    }

    /** Receipt ids on stored settlement-twin rows (Pay to M-PESA Biller / One Till Suspense). */
    private suspend fun storedSettlementTwinCodes(): Set<String> =
        dao.getAllForClassification()
            .filter { KenyanConfirmationParser.isSettlementTwinSms(it.rawBody) }
            .flatMapTo(HashSet()) { KenyanConfirmationParser.allReceiptCodes(it.rawBody) }

    private suspend fun deleteSettlementTwinsSharing(body: String) {
        val codes = KenyanConfirmationParser.allReceiptCodes(body)
        if (codes.isEmpty()) return
        val doomed = dao.getAllForClassification().mapNotNull { row ->
            if (!KenyanConfirmationParser.isSettlementTwinSms(row.rawBody)) return@mapNotNull null
            if (KenyanConfirmationParser.allReceiptCodes(row.rawBody).any { it in codes }) {
                row.id
            } else {
                null
            }
        }
        if (doomed.isNotEmpty()) dao.deleteByIds(doomed)
    }

    private fun moneyEquals(a: Double?, b: Double?): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return kotlin.math.abs(a - b) < 0.005
    }

    /**
     * Persist name→phone links onto rows missing a phone number.
     * @return number of rows updated
     */
    suspend fun linkPhonesByName(): Int = withContext(Dispatchers.IO) {
        val rows = dao.getNamedCounterparties(NAME_LINK_SCAN_LIMIT)
        if (rows.isEmpty()) return@withContext 0
        val directory = CounterpartyNameLinker.buildPhoneDirectory(
            names = rows.map { it.counterpartyName },
            phones = rows.map { it.counterpartyPhone }
        )
        if (directory.isEmpty()) return@withContext 0
        var updated = 0
        for (row in rows) {
            if (!row.counterpartyPhone.isNullOrBlank()) continue
            val key = CounterpartyNameLinker.normalizeName(row.counterpartyName) ?: continue
            val phone = directory[key] ?: continue
            updated += dao.updatePhoneIfMissing(row.id, phone)
        }
        updated
    }

    /** How many stored payments share the same payee identity as [tx]. */
    suspend fun countLinkedPayees(tx: MpesaTransaction): Int = withContext(Dispatchers.IO) {
        val key = payeeKey(tx) ?: return@withContext 1
        val ids = idsMatchingPayeeKey(dao.listPayeeLinkRows(), key)
        ids.size.coerceAtLeast(1)
    }

    /**
     * Rename every row linked by [payeeKey] for [fromTx]. Phones are left unchanged.
     * Returns how many rows matched and how many names were written.
     */
    suspend fun renameLinkedPayee(
        fromTx: MpesaTransaction,
        newName: String?
    ): LinkedPayeeRenameResult = withContext(Dispatchers.IO) {
        val previousKey = payeeKey(fromTx)
        val trimmed = newName?.trim()?.takeIf { it.isNotEmpty() }
        val linkedIds = if (previousKey == null) {
            listOf(fromTx.id).filter { it > 0L }
        } else {
            idsMatchingPayeeKey(dao.listPayeeLinkRows(), previousKey)
                .ifEmpty { listOf(fromTx.id).filter { it > 0L } }
        }
        val linkedCount = linkedIds.size.coerceAtLeast(1)
        if (trimmed == null || trimmed == fromTx.counterpartyName?.trim()) {
            return@withContext LinkedPayeeRenameResult(
                linkedCount = linkedCount,
                renamedCount = 0,
                previousPayeeKey = previousKey,
                payeeKey = previousKey
            )
        }
        var renamed = 0
        for (chunk in linkedIds.chunked(400)) {
            if (chunk.isEmpty()) continue
            renamed += dao.updateCounterpartyNameForIds(chunk, trimmed)
        }
        val nextKey = payeeKey(trimmed, fromTx.counterpartyPhone) ?: previousKey
        LinkedPayeeRenameResult(
            linkedCount = linkedCount,
            renamedCount = renamed,
            previousPayeeKey = previousKey,
            payeeKey = nextKey
        )
    }

    private suspend fun buildNamePhoneDirectory(limit: Int): Map<String, String> {
        val rows = dao.getNamedCounterparties(limit)
        return CounterpartyNameLinker.buildPhoneDirectory(
            names = rows.map { it.counterpartyName },
            phones = rows.map { it.counterpartyPhone }
        )
    }

    private suspend fun enrichSingleFromDirectory(tx: MpesaTransaction): MpesaTransaction {
        if (!tx.counterpartyPhone.isNullOrBlank()) return tx
        val directory = buildNamePhoneDirectory(NAME_LINK_SCAN_LIMIT)
        return CounterpartyNameLinker.enrich(tx, directory)
    }

    /**
     * One-time cleanup of rows that are not money confirmation SMS.
     * Manual/synthetic codes are kept. Does not wipe the database or touch
     * confirmation rows. Only forces a re-scan when something was actually removed.
     */
    suspend fun purgeNonConfirmationsIfNeeded(): Int = withContext(Dispatchers.IO) {
        if (securePreferences.confirmationFilterPurgeDone) return@withContext 0
        val removed = purgeNonConfirmations()
        securePreferences.confirmationFilterPurgeDone = true
        if (removed > 0) {
            // Only re-import when we deleted junk — leave smsBackfillDone alone if
            // nothing changed so updates do not thrash the inbox.
            securePreferences.smsBackfillDone = false
        }
        removed
    }

    private suspend fun purgeNonConfirmations(): Int = withContext(Dispatchers.IO) {
        val doomed = dao.getAllForPurge()
            .filter { row ->
                val code = row.code
                if (code.startsWith("MANUAL-") ||
                    code.startsWith("PROBE") ||
                    code.startsWith("DEMO")
                ) {
                    return@filter false
                }
                !ConfirmationIngestion.looksLikeStoredConfirmation(row.source, row.rawBody)
            }
            .map { it.id }
        if (doomed.isEmpty()) return@withContext 0
        doomed.chunked(200).forEach { chunk -> dao.deleteByIds(chunk) }
        doomed.size
    }

    companion object {
        const val LIST_LIMIT = 80
        const val HOME_LIST_LIMIT = 250
        const val SEND_SCAN_LIMIT = 250
        /** Enough recent sends for fee band + this-month counts without the full wallet. */
        const val SEND_HISTORY_LIMIT = 120
        const val NAME_LINK_SCAN_LIMIT = 500

        internal fun consolidateSendContacts(rows: List<TransactionEntity>): List<SendContact> {
            // rows are newest-first; first hit per phone is the latest send.
            data class Acc(
                val transactionId: Long,
                var name: String?,
                val phone: String,
                val normalizedPhone: String,
                var count: Int,
                val lastSentMillis: Long
            )
            val byPhone = LinkedHashMap<String, Acc>()
            for (row in rows) {
                val normalized = UssdMenuBuilder.normalizePhoneNumber(row.counterpartyPhone)
                    ?: continue
                val phone = row.counterpartyPhone?.trim().orEmpty()
                if (phone.isEmpty()) continue
                val name = row.counterpartyName?.trim()?.takeIf { it.isNotEmpty() }
                val existing = byPhone[normalized]
                if (existing == null) {
                    byPhone[normalized] = Acc(
                        transactionId = row.id,
                        name = name,
                        phone = phone,
                        normalizedPhone = normalized,
                        count = 1,
                        lastSentMillis = row.timestampMillis
                    )
                } else {
                    existing.count += 1
                    if (existing.name.isNullOrBlank() && !name.isNullOrBlank()) {
                        existing.name = name
                    }
                }
            }
            return byPhone.values
                .sortedWith(
                    compareByDescending<Acc> { it.count }
                        .thenByDescending { it.lastSentMillis }
                )
                .map { acc ->
                    SendContact(
                        transactionId = acc.transactionId,
                        name = acc.name,
                        phone = acc.phone,
                        normalizedPhone = acc.normalizedPhone,
                        sendCount = acc.count,
                        lastSentMillis = acc.lastSentMillis
                    )
                }
        }
    }
}
