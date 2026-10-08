package com.lipabill.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lipabill.app.data.local.entity.TicketEntity
import com.lipabill.app.data.model.TicketStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {

    @Query(
        """
        SELECT * FROM tickets
        ORDER BY
          CASE
            WHEN startsAtMillis IS NOT NULL AND startsAtMillis >= :nowMillis THEN 0
            WHEN startsAtMillis IS NULL THEN 1
            ELSE 2
          END,
          CASE
            WHEN startsAtMillis IS NOT NULL AND startsAtMillis >= :nowMillis THEN startsAtMillis
            ELSE NULL
          END ASC,
          CASE
            WHEN startsAtMillis IS NOT NULL AND startsAtMillis < :nowMillis THEN startsAtMillis
            ELSE NULL
          END DESC,
          createdAtMillis DESC
        """
    )
    fun observeAll(nowMillis: Long): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<TicketEntity?>

    @Query("SELECT * FROM tickets WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TicketEntity?

    @Query("SELECT * FROM tickets WHERE barcodeValue = :value LIMIT 1")
    suspend fun getByBarcodeValue(value: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE boardingBarcodeValue = :value LIMIT 1")
    suspend fun getByBoardingBarcodeValue(value: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE returnBoardingBarcodeValue = :value LIMIT 1")
    suspend fun getByReturnBoardingBarcodeValue(value: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE orderId = :orderId LIMIT 1")
    suspend fun getByOrderId(orderId: String): TicketEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TicketEntity): Long

    @Update
    suspend fun update(entity: TicketEntity)

    @Query("UPDATE tickets SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TicketStatus)

    @Query("UPDATE tickets SET startsAtMillis = :startsAtMillis WHERE id = :id")
    suspend fun updateStartsAt(id: Long, startsAtMillis: Long?)

    @Query("DELETE FROM tickets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
