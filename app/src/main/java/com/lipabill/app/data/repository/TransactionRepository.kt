package com.lipabill.app.data.repository

import com.lipabill.app.data.local.dao.TransactionDao
import com.lipabill.app.data.local.entity.TransactionEntity
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.data.parser.MpesaSmsParser
import com.lipabill.app.data.sms.MpesaSmsFilter
import com.lipabill.app.data.sms.SmsInboxReader
import com.lipabill.app.ussd.UssdMenuBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

class TransactionRepository(
    private val dao: TransactionDao,
    private val inboxReader: SmsInboxReader,
    private val securePreferences: SecurePreferences,
    private val merchantDirectory: MerchantDirectory
) {

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
        return combine(source, dao.observeRecent(NAME_LINK_SCAN_LIMIT)) { list, wide ->
            val directory = CounterpartyNameLinker.buildPhoneDirectoryFromTransactions(
                wide.map { it.toListItem() }
            )
            CounterpartyNameLinker.enrichAll(list.map { it.toListItem() }, directory)
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Past Send Money recipients consolidated by normalized phone.
     * Sorted by send frequency (desc), then most recent send.
     */
    fun observeSendContacts(limit: Int = SEND_SCAN_LIMIT): Flow<List<SendContact>> =
        dao.observeSentWithPhone(limit)
            .map { rows -> consolidateSendContacts(rows) }
            .flowOn(Dispatchers.Default)

    /** Top frequent send counterparts for the home strip. */
    fun observeFrequentContacts(limit: Int = FREQUENT_LIMIT): Flow<List<SendContact>> =
        observeSendContacts().map { it.take(limit) }

    fun observeMerchantSearch(
        type: TransactionType,
        query: String,
        limit: Int = 12
    ): Flow<List<MerchantHit>> = merchantDirectory.observeSearch(type, query, limit)

    fun observeById(id: Long): Flow<MpesaTransaction?> =
        combine(dao.observeById(id), dao.observeRecent(NAME_LINK_SCAN_LIMIT)) { entity, wide ->
            val tx = entity?.toDomain() ?: return@combine null
            val directory = CounterpartyNameLinker.buildPhoneDirectoryFromTransactions(
                wide.map { it.toListItem() }
            )
            CounterpartyNameLinker.enrich(tx, directory)
        }.flowOn(Dispatchers.Default)

    suspend fun upsert(tx: MpesaTransaction): Boolean = withContext(Dispatchers.IO) {
        // Skip non-confirmation SMS (promos, PIN notices, etc.)
        if (tx.rawBody.isNotBlank() && !MpesaSmsFilter.isTransactionConfirmation(tx.rawBody)) {
            return@withContext false
        }
        val enriched = merchantDirectory.enrichIncomingSms(tx)
        val withPhone = enrichSingleFromDirectory(enriched)
        val rowId = dao.insertIgnore(TransactionEntity.fromDomain(withPhone))
        if (rowId != -1L) return@withContext true
        // Already present — refresh money fields from the latest parse (fixes bad amounts).
        dao.updateParsedMoneyByCode(
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
     * Re-query inbox and insert any new messages (deduped by transaction code).
     * Existing Room rows are never deleted here — only additive inserts.
     * @return number of newly inserted rows
     */
    suspend fun rescanInbox(): Int = withContext(Dispatchers.IO) {
        purgeNonConfirmationsIfNeeded()
        repairParsedAmountsIfNeeded()
        // Without SMS access, leave prior rows alone and do not mark backfill complete.
        if (!inboxReader.hasSmsPermission()) {
            linkPhonesByName()
            return@withContext 0
        }
        val parsed = inboxReader.parseAll(maxMessages = SmsInboxReader.DEFAULT_MAX_MESSAGES)
        if (parsed.isEmpty()) {
            // Keep any already-imported history; only mark backfill done when we could
            // actually query the inbox (permission granted above).
            securePreferences.smsBackfillDone = true
            linkPhonesByName()
            return@withContext 0
        }
        var inserted = 0
        for (tx in parsed) {
            if (upsert(tx)) inserted++
        }
        securePreferences.smsBackfillDone = true
        linkPhonesByName()
        inserted
    }

    suspend fun backfillIfNeeded(): Int {
        purgeNonConfirmationsIfNeeded()
        repairParsedAmountsIfNeeded()
        if (securePreferences.smsBackfillDone && dao.count() > 0) {
            linkPhonesByName()
            return 0
        }
        // Never treat "no SMS permission" as a finished backfill — that would skip
        // re-import after the user grants access, leaving an empty history.
        if (!inboxReader.hasSmsPermission()) {
            linkPhonesByName()
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

    suspend fun repairParsedAmounts(): Int = withContext(Dispatchers.IO) {
        val rows = dao.getAllForAmountRepair()
        var fixed = 0
        for (row in rows) {
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
     * One-time cleanup of rows that are not real Confirmed + M-PESA balance SMS.
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

    suspend fun purgeNonConfirmations(): Int = withContext(Dispatchers.IO) {
        val doomed = dao.getAllForPurge()
            .filter { row ->
                val code = row.code
                if (code.startsWith("MANUAL-") || code.startsWith("PROBE")) return@filter false
                !MpesaSmsFilter.isTransactionConfirmation(row.rawBody)
            }
            .map { it.id }
        if (doomed.isEmpty()) return@withContext 0
        doomed.chunked(200).forEach { chunk -> dao.deleteByIds(chunk) }
        doomed.size
    }

    companion object {
        const val LIST_LIMIT = 80
        const val ANALYTICS_LIMIT = 500
        const val SMS_SCAN_LIMIT = SmsInboxReader.DEFAULT_MAX_MESSAGES
        const val SEND_SCAN_LIMIT = 250
        const val FREQUENT_LIMIT = 12
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
