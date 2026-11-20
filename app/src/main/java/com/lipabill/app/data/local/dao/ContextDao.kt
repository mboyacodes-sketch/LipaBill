package com.lipabill.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lipabill.app.data.local.entity.ContextEventEntity
import com.lipabill.app.data.local.entity.ReminderEntity

@Dao
interface ContextDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: ContextEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM context_events WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun eventsForSource(sourceType: String, sourceId: String): List<ContextEventEntity>

    @Query("SELECT * FROM context_events WHERE id = :id LIMIT 1")
    suspend fun eventById(id: String): ContextEventEntity?

    @Query("SELECT * FROM reminders WHERE eventId = :eventId")
    suspend fun remindersForEvent(eventId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE state = :state AND fireAt <= :beforeMillis")
    suspend fun dueReminders(state: String, beforeMillis: Long): List<ReminderEntity>

    @Query("UPDATE reminders SET state = :state WHERE id = :id")
    suspend fun setReminderState(id: String, state: String)

    @Query("UPDATE context_events SET status = :status WHERE id = :id")
    suspend fun setEventStatus(id: String, status: String)

    @Query("DELETE FROM reminders WHERE eventId IN (SELECT id FROM context_events WHERE sourceType = :sourceType AND sourceId = :sourceId)")
    suspend fun deleteRemindersForSource(sourceType: String, sourceId: String)

    @Query("DELETE FROM context_events WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteEventsForSource(sourceType: String, sourceId: String)

    @Query("SELECT * FROM reminders WHERE state = 'SCHEDULED'")
    suspend fun scheduledReminders(): List<ReminderEntity>
}
