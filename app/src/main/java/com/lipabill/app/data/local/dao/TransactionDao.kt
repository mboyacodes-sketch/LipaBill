package com.lipabill.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lipabill.app.data.local.entity.TransactionEntity
import com.lipabill.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TransactionEntity?>

    @Query(
        """
        SELECT * FROM transactions
        WHERE (counterpartyName LIKE '%' || :query || '%' COLLATE NOCASE
               OR counterpartyPhone LIKE '%' || :query || '%' COLLATE NOCASE
               OR code LIKE '%' || :query || '%' COLLATE NOCASE
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
    suspend fun insertAllIgnore(entities: List<TransactionEntity>): List<Long>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE code = :code)")
    suspend fun existsByCode(code: String): Boolean

    @Query("SELECT id, amount, balance, cost, rawBody, timestampMillis FROM transactions")
    suspend fun getAllForAmountRepair(): List<AmountRepairRow>

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
        WHERE code = :code
        """
    )
    suspend fun updateParsedMoneyByCode(
        code: String,
        amount: Double?,
        balance: Double?,
        cost: Double?,
        rawBody: String
    ): Int

    @Query("SELECT id, code, rawBody FROM transactions")
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
}

/** Lightweight projection for confirmation-body purge. */
data class TransactionPurgeRow(
    val id: Long,
    val code: String,
    val rawBody: String
)

data class AmountRepairRow(
    val id: Long,
    val amount: Double?,
    val balance: Double?,
    val cost: Double?,
    val rawBody: String,
    val timestampMillis: Long
)

data class NamedCounterpartyRow(
    val id: Long,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val timestampMillis: Long
)
