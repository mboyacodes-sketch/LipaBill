package com.lipabill.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepeatAttemptDao {

    @Insert
    suspend fun insert(entity: RepeatAttemptEntity): Long

    @Update
    suspend fun update(entity: RepeatAttemptEntity)

    @Query("UPDATE repeat_attempts SET outcome = :outcome, detail = :detail WHERE id = :id")
    suspend fun updateOutcome(id: Long, outcome: com.lipabill.app.ussd.RepeatOutcome, detail: String?)

    @Query("SELECT * FROM repeat_attempts ORDER BY createdAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<RepeatAttemptEntity>>

    @Query("SELECT * FROM repeat_attempts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RepeatAttemptEntity?
}
