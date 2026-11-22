package com.lipabill.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lipabill.app.data.local.entity.TransactionEntity
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

/** Ledger row count for one source. */
data class SourceCount(val source: MoneySource, val n: Int)

/** Newest stored balance for one source (wallet or bank). */
data class SourceBalance(val source: MoneySource, val balance: Double)

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    /**
     * One ledger at a time. [fuliza] = 1 is Fuliza draws only; 0 is every other row.
     * No row cap — Insights / challenge math; Home uses [observeWalletList].
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (
            (:fuliza = 1 AND type = 'FULIZA')
            OR (:fuliza = 0 AND type != 'FULIZA')
        )
        ORDER BY timestampMillis DESC
        """
    )
    fun observeWallet(fuliza: Int): Flow<List<TransactionEntity>>

    /**
     * Home feed window: newest rows only, no SMS body (list UI never shows it).
     */
    @Query(
        """
        SELECT id, code, type, amount, counterpartyName, counterpartyPhone,
               timestampMillis, balance, cost, '' AS rawBody, source
        FROM transactions
        WHERE (
            (:fuliza = 1 AND type = 'FULIZA')
            OR (:fuliza = 0 AND type != 'FULIZA')
        )
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    fun observeWalletList(fuliza: Int, limit: Int): Flow<List<TransactionEntity>>

    /**
     * Home ledger window for the picked sources only, so a quiet bank is not crowded out
     * of the row cap by M-Pesa. No SMS bodies.
     */
    @Query(
        """
        SELECT id, code, type, amount, counterpartyName, counterpartyPhone,
               timestampMillis, balance, cost, '' AS rawBody, source
        FROM transactions
        WHERE type != 'FULIZA' AND source IN (:sources)
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    fun observeLedgerWindow(sources: List<String>, limit: Int): Flow<List<TransactionEntity>>

    /** Rows per source across the whole ledger (chip order, not the Home window). */
    @Query("SELECT source, COUNT(*) AS n FROM transactions WHERE type != 'FULIZA' GROUP BY source")
    fun observeSourceCounts(): Flow<List<SourceCount>>

    /** Recent Send Money rows for fee / monthly-count estimates on the Send sheet. */
    @Query(
        """
        SELECT id, code, type, amount, counterpartyName, counterpartyPhone,
               timestampMillis, balance, cost, '' AS rawBody, source
        FROM transactions
        WHERE type = 'SENT'
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    fun observeRecentSends(limit: Int): Flow<List<TransactionEntity>>

    /**
     * Rows whose timestamp falls in [startInclusive, endExclusive).
     * Used by challenge and check-in windows. Backed by index_transactions_timestampMillis.
     */
    @Query(
        """
        SELECT id, counterpartyName, counterpartyPhone, type, amount, cost, timestampMillis, code, rawBody
        FROM transactions
        WHERE timestampMillis >= :startInclusive AND timestampMillis < :endExclusive
        ORDER BY timestampMillis ASC, id ASC
        """
    )
    suspend fun listBetween(startInclusive: Long, endExclusive: Long): List<WindowTransactionRow>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT code FROM transactions WHERE id = :id LIMIT 1")
    suspend fun codeForId(id: Long): String?

    @Query("SELECT id FROM transactions WHERE code = :code LIMIT 1")
    suspend fun idForCode(code: String): Long?

    @Query(
        """
        SELECT * FROM transactions
        WHERE (counterpartyName LIKE '%' || :query || '%' COLLATE NOCASE
               OR counterpartyPhone LIKE '%' || :query || '%' COLLATE NOCASE
               OR code LIKE '%' || :query || '%' COLLATE NOCASE
               OR source LIKE '%' || :query || '%' COLLATE NOCASE
               OR CAST(amount AS TEXT) LIKE '%' || :query || '%')
          AND (:type IS NULL OR type = :type)
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    fun observeSearch(query: String, type: TransactionType?, limit: Int): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE type = 'SENT'
          AND counterpartyPhone IS NOT NULL
          AND LENGTH(TRIM(counterpartyPhone)) > 0
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    fun observeSentWithPhone(limit: Int): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(entities: List<TransactionEntity>): List<Long>

    @Query("SELECT source || ':' || code FROM transactions")
    suspend fun allSourceCodes(): List<String>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT id, amount, balance, cost, rawBody, timestampMillis, source FROM transactions")
    suspend fun getAllForAmountRepair(): List<AmountRepairRow>

    @Query("SELECT id, type, counterpartyName, rawBody, timestampMillis, source FROM transactions")
    suspend fun getAllForClassification(): List<ClassificationRepairRow>

    @Query(
        """
        UPDATE transactions
        SET type = :type,
            counterpartyName = COALESCE(:name, counterpartyName),
            counterpartyPhone = COALESCE(:phone, counterpartyPhone)
        WHERE id = :id
        """
    )
    suspend fun updateParty(
        id: Long,
        type: TransactionType,
        name: String?,
        phone: String?
    ): Int

    @Query(
        """
        UPDATE transactions
        SET amount = :amount, balance = :balance, cost = :cost
        WHERE id = :id
        """
    )
    suspend fun updateParsedMoney(
        id: Long,
        amount: Double?,
        balance: Double?,
        cost: Double?
    ): Int

    @Query(
        """
        UPDATE transactions
        SET amount = :amount, balance = :balance, cost = :cost, rawBody = :rawBody
        WHERE source = :source AND code = :code
        """
    )
    suspend fun updateParsedMoneyByCode(
        source: MoneySource,
        code: String,
        amount: Double?,
        balance: Double?,
        cost: Double?,
        rawBody: String
    ): Int

    /**
     * Fuliza outstanding lives in [balance]. Always keep [type] = FULIZA when writing it,
     * or home M-Pesa will treat the figure as the wallet balance.
     */
    @Query(
        """
        UPDATE transactions
        SET type = :type,
            counterpartyName = COALESCE(:name, counterpartyName),
            amount = :amount, balance = :balance, cost = :cost, rawBody = :rawBody
        WHERE source = :source AND code = :code
        """
    )
    suspend fun updateParsedFulizaByCode(
        source: MoneySource,
        code: String,
        type: TransactionType,
        name: String?,
        amount: Double?,
        balance: Double?,
        cost: Double?,
        rawBody: String
    ): Int

    @Query("SELECT id, code, rawBody, source FROM transactions")
    suspend fun getAllForPurge(): List<TransactionPurgeRow>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query(
        """
        SELECT id, counterpartyName, counterpartyPhone, timestampMillis FROM transactions
        WHERE counterpartyName IS NOT NULL
          AND LENGTH(TRIM(counterpartyName)) >= 3
        ORDER BY timestampMillis DESC
        LIMIT :limit
        """
    )
    suspend fun getNamedCounterparties(limit: Int): List<NamedCounterpartyRow>

    @Query(
        """
        UPDATE transactions
        SET counterpartyPhone = :phone
        WHERE id = :id
          AND (counterpartyPhone IS NULL OR LENGTH(TRIM(counterpartyPhone)) = 0)
        """
    )
    suspend fun updatePhoneIfMissing(id: Long, phone: String): Int

    /** Newest stored M-Pesa wallet balance for Send/Pay/Repeat Fuliza cover. */
    @Query(
        """
        SELECT balance FROM transactions
        WHERE source = 'MPESA' AND type != 'FULIZA' AND balance IS NOT NULL
        ORDER BY timestampMillis DESC
        LIMIT 1
        """
    )
    suspend fun latestMpesaBalance(): Double?

    /** Ledger rows since [sinceMillis], newest first, without SMS bodies (money moments). */
    @Query(
        """
        SELECT id, code, type, amount, counterpartyName, counterpartyPhone,
               timestampMillis, balance, cost, '' AS rawBody, source
        FROM transactions
        WHERE type != 'FULIZA' AND timestampMillis >= :sinceMillis
        ORDER BY timestampMillis DESC
        """
    )
    suspend fun ledgerSince(sinceMillis: Long): List<TransactionEntity>

    /** Newest balance per source, outside the Home row cap (a quiet second SIM still has one). */
    @Query(
        """
        SELECT source, balance FROM transactions t
        WHERE type != 'FULIZA' AND balance IS NOT NULL
          AND timestampMillis = (
              SELECT MAX(timestampMillis) FROM transactions
              WHERE source = t.source AND type != 'FULIZA' AND balance IS NOT NULL
          )
        """
    )
    fun observeLatestBalances(): Flow<List<SourceBalance>>

    /** Lightweight rows for payee-key linking (rename / count similar). */
    @Query(
        """
        SELECT id, counterpartyName, counterpartyPhone, type
        FROM transactions
        """
    )
    suspend fun listPayeeLinkRows(): List<PayeeLinkRow>

    @Query(
        """
        UPDATE transactions
        SET counterpartyName = :name
        WHERE id IN (:ids)
        """
    )
    suspend fun updateCounterpartyNameForIds(ids: List<Long>, name: String): Int
}

/** Lightweight projection for confirmation-body purge. */
data class TransactionPurgeRow(
    val id: Long,
    val code: String,
    val rawBody: String,
    val source: MoneySource = MoneySource.MPESA
)

data class ClassificationRepairRow(
    val id: Long,
    val type: TransactionType,
    val counterpartyName: String?,
    val rawBody: String,
    val timestampMillis: Long,
    val source: MoneySource = MoneySource.MPESA
)

data class AmountRepairRow(
    val id: Long,
    val amount: Double?,
    val balance: Double?,
    val cost: Double?,
    val rawBody: String,
    val timestampMillis: Long,
    val source: MoneySource = MoneySource.MPESA
)

data class WindowTransactionRow(
    val id: Long,
    val counterpartyName: String?,
    val counterpartyPhone: String? = null,
    val type: TransactionType,
    val amount: Double?,
    val cost: Double? = null,
    val timestampMillis: Long,
    val code: String = "",
    val rawBody: String = ""
)

data class NamedCounterpartyRow(
    val id: Long,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val timestampMillis: Long
)

data class PayeeLinkRow(
    val id: Long,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val type: TransactionType
)
