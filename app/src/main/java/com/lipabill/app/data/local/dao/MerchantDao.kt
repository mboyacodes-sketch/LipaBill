package com.lipabill.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lipabill.app.data.local.entity.MerchantEntity
import com.lipabill.app.data.local.entity.PendingMerchantPaymentEntity
import com.lipabill.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantDao {

    @Query(
        """
        SELECT * FROM merchants
        WHERE type = :type
          AND (
            displayName LIKE '%' || :query || '%' COLLATE NOCASE
            OR identifier LIKE '%' || :query || '%'
            OR IFNULL(accountHint, '') LIKE '%' || :query || '%' COLLATE NOCASE
          )
        ORDER BY lastUsedMillis DESC
        LIMIT :limit
        """
    )
    fun observeSearch(type: TransactionType, query: String, limit: Int): Flow<List<MerchantEntity>>

    @Query(
        """
        SELECT * FROM merchants
        WHERE type = :type
        ORDER BY lastUsedMillis DESC
        LIMIT :limit
        """
    )
    fun observeRecent(type: TransactionType, limit: Int): Flow<List<MerchantEntity>>

    @Query("SELECT * FROM merchants WHERE type = :type AND identifier = :identifier LIMIT 1")
    suspend fun findByIdentifier(type: TransactionType, identifier: String): MerchantEntity?

    @Query("SELECT COUNT(*) FROM merchants")
    suspend fun count(): Int

    @Query("SELECT * FROM merchants ORDER BY lastUsedMillis DESC")
    suspend fun listAll(): List<MerchantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MerchantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MerchantEntity): Long

    @Update
    suspend fun update(entity: MerchantEntity)

    @Insert
    suspend fun insertPending(entity: PendingMerchantPaymentEntity): Long

    @Query(
        """
        SELECT * FROM pending_merchant_payments
        WHERE consumed = 0
          AND type = :type
          AND ABS(amount - :amount) < 0.02
          AND createdAtMillis >= :sinceMillis
        ORDER BY createdAtMillis DESC
        LIMIT 1
        """
    )
    suspend fun findMatchingPending(
        type: TransactionType,
        amount: Double,
        sinceMillis: Long
    ): PendingMerchantPaymentEntity?

    @Query("UPDATE pending_merchant_payments SET consumed = 1 WHERE id = :id")
    suspend fun consumePending(id: Long)

    @Query("DELETE FROM pending_merchant_payments WHERE createdAtMillis < :beforeMillis OR consumed = 1")
    suspend fun prunePending(beforeMillis: Long)
}
