package com.lipabill.app.data.tickets

import com.lipabill.app.data.local.dao.ContextDao
import com.lipabill.app.data.local.entity.ContextEventEntity
import com.lipabill.app.data.local.entity.ReminderEntity
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketStatus
import java.time.ZoneId

/**
 * Keeps [context_events] + [reminders] in step with a pass. Timing still comes from
 * [planPassReminders]; WorkManager job names stay `pass-reminder-<ticketId>-<kind>`.
 */
class PassContextSync(private val dao: ContextDao) {

    suspend fun syncPass(
        ticket: Ticket,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        leadTimes: PassReminderLeadTimes = PassReminderLeadTimes.DEFAULT
    ) {
        val sourceId = ticket.id.toString()
        dao.deleteRemindersForSource(SOURCE_PASS, sourceId)
        dao.deleteEventsForSource(SOURCE_PASS, sourceId)

        val start = ticket.occasionMillis ?: return
        if (ticket.effectiveStatus(nowMillis) != TicketStatus.ACTIVE) return

        val eventId = eventId(ticket.id)
        dao.upsertEvent(
            ContextEventEntity(
                id = eventId,
                type = TYPE_UPCOMING_PASS,
                sourceType = SOURCE_PASS,
                sourceId = sourceId,
                scheduledAt = start,
                timezoneId = zone.id,
                status = STATUS_PLANNED
            )
        )
        for (planned in planPassReminders(start, nowMillis, zone, leadTimes)) {
            dao.upsertReminder(
                ReminderEntity(
                    id = "$eventId:${planned.kind.name}",
                    eventId = eventId,
                    fireAt = planned.atMillis,
                    state = STATE_SCHEDULED,
                    workName = workName(ticket.id, planned.kind)
                )
            )
        }
    }

    suspend fun removePass(ticketId: Long) {
        val sourceId = ticketId.toString()
        dao.deleteRemindersForSource(SOURCE_PASS, sourceId)
        dao.deleteEventsForSource(SOURCE_PASS, sourceId)
    }

    /** Work names the table still expects to run — survives process death, unlike memory. */
    suspend fun scheduledWorkNames(): Set<String> =
        dao.scheduledReminders().map { it.workName }.toSet()

    /**
     * Whether a reminder job that just woke up is still wanted. Once a pass has an event row,
     * only a SCHEDULED reminder with this job name may fire, so a slot turned off in Settings
     * or a moved date can't notify from an old job. Before back-fill (no event row yet) the
     * job is trusted, so an upgrade never drops a reminder.
     */
    suspend fun isStillWanted(ticketId: Long, kind: PassReminderKind): Boolean {
        val eventId = eventId(ticketId)
        if (dao.eventById(eventId) == null) return true
        val name = workName(ticketId, kind)
        return dao.remindersForEvent(eventId).any { it.workName == name && it.state == STATE_SCHEDULED }
    }

    suspend fun markReminderFired(workName: String) {
        dao.scheduledReminders()
            .firstOrNull { it.workName == workName }
            ?.let { dao.setReminderState(it.id, STATE_FIRED) }
    }

    companion object {
        const val SOURCE_PASS = "pass"
        const val TYPE_UPCOMING_PASS = "UPCOMING_PASS"
        const val STATUS_PLANNED = "PLANNED"
        const val STATE_SCHEDULED = "SCHEDULED"
        const val STATE_FIRED = "FIRED"

        fun eventId(ticketId: Long): String = "pass-$ticketId"

        fun workName(ticketId: Long, kind: PassReminderKind): String =
            "pass-reminder-$ticketId-${kind.name}"
    }
}
