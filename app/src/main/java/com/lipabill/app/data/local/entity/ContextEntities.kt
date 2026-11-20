package com.lipabill.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A scheduled life event derived from a pass (or later, a recurring payment).
 * Unique on (sourceType, sourceId, type) so re-syncing a pass replaces the same row.
 */
@Entity(
    tableName = "context_events",
    indices = [
        Index(value = ["sourceType", "sourceId", "type"], unique = true),
        Index(value = ["scheduledAt"]),
        Index(value = ["status"])
    ]
)
data class ContextEventEntity(
    @PrimaryKey val id: String,
    /** e.g. UPCOMING_PASS, PAST_DUE_PASS */
    val type: String,
    /** e.g. pass */
    val sourceType: String,
    /** Ticket id as text. */
    val sourceId: String,
    val scheduledAt: Long,
    val timezoneId: String,
    /** PLANNED, ACTIVE, DONE, CANCELLED */
    val status: String
)

/**
 * One WorkManager reminder for a [ContextEventEntity].
 * Stable id is `"<eventId>:<ruleId>"` (e.g. pass-42:DAY_BEFORE).
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["fireAt"]),
        Index(value = ["state"])
    ]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val fireAt: Long,
    /** SCHEDULED, FIRED, CANCELLED */
    val state: String,
    val workName: String
)
