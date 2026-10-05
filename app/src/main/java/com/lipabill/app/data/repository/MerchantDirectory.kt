package com.lipabill.app.data.repository

import android.util.Log
import com.lipabill.app.data.local.dao.MerchantDao
import com.lipabill.app.data.local.entity.MerchantEntity
import com.lipabill.app.data.local.entity.PendingMerchantPaymentEntity
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.parser.digitsOnly
import com.lipabill.app.data.parser.extractAccount
import com.lipabill.app.data.parser.isPhoneLikeName
import com.lipabill.app.data.parser.phoneContradicts
import com.lipabill.app.data.parser.phonesMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

data class MerchantHit(
    val id: Long,
    val type: TransactionType,
    val identifier: String,
    val accountHint: String?,
    val displayName: String
)

/**
 * Learns paybill / till / Pochi identifier ↔ SMS name mappings from payments the user makes.
 * Mirrors the directory to a plain JSON snapshot so learning survives reinstall when
 * Auto Backup restores the file but SQLCipher’s Keystore key is gone.
 */
class MerchantDirectory(
    private val dao: MerchantDao,
    private val snapshotStore: MerchantSnapshotStore
) {

    private val restoreMutex = Mutex()

    /**
     * Import snapshot into an empty merchant table (idempotent).
     * Also seeds the snapshot file if Room already has merchants but the file is missing.
     */
    suspend fun restoreFromSnapshotIfEmpty(): Int = withContext(Dispatchers.IO) {
        restoreMutex.withLock {
            val count = dao.count()
            if (count > 0) {
                persistSnapshot()
                return@withContext 0
            }
            val fromFile = snapshotStore.read()
            if (fromFile.isEmpty()) return@withContext 0
            dao.upsertAll(fromFile)
            Log.i(TAG, "restored_merchants count=${fromFile.size}")
            fromFile.size
        }
    }

    fun observeSearch(type: TransactionType, query: String, limit: Int = 12): Flow<List<MerchantHit>> {
        val trimmed = query.trim()
        val source = if (trimmed.isEmpty()) {
            dao.observeRecent(type, limit)
        } else {
            dao.observeSearch(type, trimmed, limit)
        }
        return source
            .map { rows -> rows.map { it.toHit() } }
            .flowOn(Dispatchers.Default)
    }

    /**
     * Called when dialing so the next matching SMS can learn the recipient name
     * (especially for numbers not already in contacts / history).
     */
    suspend fun rememberOutgoing(tx: MpesaTransaction, amount: Double) = withContext(Dispatchers.IO) {
        if (!isLearnableType(tx.type)) return@withContext
        val identifier = digitsOnly(tx.counterpartyPhone) ?: return@withContext
        val account = if (tx.type == TransactionType.PAYBILL) {
            extractAccount(tx.rawBody)
        } else {
            null
        }
        val now = System.currentTimeMillis()
        dao.prunePending(now - PENDING_TTL_MS)
        dao.insertPending(
            PendingMerchantPaymentEntity(
                type = tx.type,
                identifier = identifier,
                accountCode = account,
                amount = amount,
                createdAtMillis = now
            )
        )
        // Seed directory immediately so search works before SMS arrives.
        val placeholder = tx.counterpartyName?.takeIf { it.isNotBlank() }
            ?: placeholderName(tx.type, identifier)
        upsertMerchant(
            type = tx.type,
            identifier = identifier,
            accountHint = account,
            displayName = placeholder,
            now = now,
            preferIncomingName = false
        )
    }

    /**
     * When a confirmation SMS arrives, match a recent pending dial and prefer SMS party details
     * (name from M-Pesa) over dial-time placeholders for new numbers.
     */
    suspend fun enrichIncomingSms(tx: MpesaTransaction): MpesaTransaction = withContext(Dispatchers.IO) {
        val amount = tx.amount ?: return@withContext tx
        val now = System.currentTimeMillis()
        dao.prunePending(now - PENDING_TTL_MS)

        if (!tx.type.isOutgoing()) return@withContext tx

        if (tx.type == TransactionType.PAYBILL ||
            tx.type == TransactionType.BUY_GOODS ||
            tx.type == TransactionType.SENT
        ) {
            val pending = dao.findMatchingPending(
                type = tx.type,
                amount = amount,
                sinceMillis = now - PENDING_TTL_MS
            )
            if (pending != null && !pendingPhoneContradicts(tx, pending)) {
                return@withContext applyPendingEnrichment(tx, pending, now)
            }
        }

        // Pochi receipts are often ordinary "sent to" messages, so a Send pending that
        // names a different phone must not swallow them.
        enrichPochiIncoming(tx, amount, now)
    }

    /**
     * Attach a pending Pochi dial (phone known at dial time) to a name-only confirmation SMS.
     */
    private suspend fun enrichPochiIncoming(
        tx: MpesaTransaction,
        amount: Double,
        now: Long
    ): MpesaTransaction {
        val pending = dao.findMatchingPending(
            type = TransactionType.POCHI,
            amount = amount,
            sinceMillis = now - PENDING_TTL_MS
        ) ?: return tx

        // 254… and 07… are the same handset. A masked receipt must still show the dialed number.
        if (pendingPhoneContradicts(tx, pending)) return tx

        return applyPendingEnrichment(
            tx = tx.copy(type = TransactionType.POCHI),
            pending = pending,
            now = now
        )
    }

    /** Send and Pochi only. A missing phone does not contradict the dial. */
    private fun pendingPhoneContradicts(
        tx: MpesaTransaction,
        pending: PendingMerchantPaymentEntity
    ): Boolean {
        val phonePayment = tx.type == TransactionType.SENT ||
            tx.type == TransactionType.POCHI ||
            pending.type == TransactionType.POCHI
        if (!phonePayment) return false
        return phoneContradicts(tx.counterpartyPhone, pending.identifier)
    }

    private suspend fun applyPendingEnrichment(
        tx: MpesaTransaction,
        pending: PendingMerchantPaymentEntity,
        now: Long
    ): MpesaTransaction {
        dao.consumePending(pending.id)
        val smsName = tx.counterpartyName?.trim()?.takeIf { it.isNotBlank() }
        val bestName = when {
            smsName != null && !isPhoneLikeName(smsName) -> smsName
            smsName != null -> smsName
            else -> null
        }
        upsertMerchant(
            type = pending.type,
            identifier = pending.identifier,
            accountHint = pending.accountCode,
            displayName = bestName
                ?: placeholderName(pending.type, pending.identifier),
            now = now,
            preferIncomingName = bestName != null && !isPhoneLikeName(bestName)
        )

        val enrichedRaw = when {
            pending.accountCode.isNullOrBlank() -> tx.rawBody
            tx.rawBody.contains("Account", ignoreCase = true) -> tx.rawBody
            else -> "${tx.rawBody} Account ${pending.accountCode}"
        }
        val smsPhone = tx.counterpartyPhone
        val phone = when {
            smsPhone.isNullOrBlank() || smsPhone.contains('*') -> pending.identifier
            else -> digitsOnly(smsPhone) ?: pending.identifier
        }
        return tx.copy(
            type = pending.type,
            counterpartyPhone = phone,
            counterpartyName = bestName ?: tx.counterpartyName,
            rawBody = enrichedRaw
        )
    }

    private suspend fun upsertMerchant(
        type: TransactionType,
        identifier: String,
        accountHint: String?,
        displayName: String,
        now: Long,
        preferIncomingName: Boolean
    ) {
        val existing = dao.findByIdentifier(type, identifier)
        if (existing == null) {
            dao.upsert(
                MerchantEntity(
                    type = type,
                    identifier = identifier,
                    accountHint = accountHint,
                    displayName = displayName,
                    normalizedName = normalizeName(displayName),
                    lastUsedMillis = now,
                    useCount = 1
                )
            )
            persistSnapshot()
            return
        }
        val name = when {
            preferIncomingName -> displayName
            isPlaceholderName(existing.displayName, identifier) -> displayName
            else -> existing.displayName
        }
        dao.update(
            existing.copy(
                accountHint = accountHint ?: existing.accountHint,
                displayName = name,
                normalizedName = normalizeName(name),
                lastUsedMillis = now,
                useCount = existing.useCount + 1
            )
        )
        persistSnapshot()
    }

    private suspend fun persistSnapshot() {
        runCatching {
            snapshotStore.write(dao.listAll())
        }.onFailure { Log.w(TAG, "snapshot_write_failed", it) }
    }

    private fun MerchantEntity.toHit() = MerchantHit(
        id = id,
        type = type,
        identifier = identifier,
        accountHint = accountHint,
        displayName = displayName
    )

    private fun isLearnableType(type: TransactionType): Boolean =
        type == TransactionType.PAYBILL ||
            type == TransactionType.BUY_GOODS ||
            type == TransactionType.POCHI ||
            type == TransactionType.SENT

    private fun placeholderName(type: TransactionType, identifier: String): String = when (type) {
        TransactionType.PAYBILL -> "Paybill $identifier"
        TransactionType.BUY_GOODS -> "Till $identifier"
        TransactionType.POCHI -> "Pochi $identifier"
        TransactionType.SENT -> identifier
        else -> identifier
    }

    private fun isPlaceholderName(name: String, identifier: String): Boolean {
        val n = name.trim().lowercase(Locale.US)
        if (isPhoneLikeName(name) && phonesMatch(name.filter { it.isDigit() }, identifier)) {
            return true
        }
        return n == "paybill $identifier" ||
            n == "till $identifier" ||
            n == "pochi $identifier" ||
            n == "pochi la biashara $identifier" ||
            n.startsWith("paybill ") && n.endsWith(identifier) ||
            n.startsWith("till ") && n.endsWith(identifier) ||
            n.startsWith("pochi ") && n.endsWith(identifier)
    }

    private fun normalizeName(name: String): String =
        name.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")

    companion object {
        private const val TAG = "LipaBill.Merchants"
        private val PENDING_TTL_MS = TimeUnit.MINUTES.toMillis(45)
    }
}
