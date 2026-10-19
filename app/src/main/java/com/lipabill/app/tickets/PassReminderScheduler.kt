package com.lipabill.app.tickets

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lipabill.app.LipaBillApp
import com.lipabill.app.MainActivity
import com.lipabill.app.notify.mainActivityPendingIntent
import com.lipabill.app.notify.postLipaBill
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.tickets.PassReminderKind
import com.lipabill.app.data.tickets.planPassReminders
import com.lipabill.app.ui.util.formatEventSchedule
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps one scheduled alert per pass per kind. Re-run whenever the pass list changes.
 */
class PassReminderScheduler(private val context: Context) {

    fun sync(tickets: List<Ticket>, nowMillis: Long = System.currentTimeMillis()) {
        val zone = ZoneId.systemDefault()
        val planned = tickets.flatMap { ticket ->
            val start = ticket.occasionMillis ?: return@flatMap emptyList()
            if (ticket.effectiveStatus(nowMillis) != TicketStatus.ACTIVE) return@flatMap emptyList()
            planPassReminders(start, nowMillis, zone).map { reminder ->
                Scheduled(ticket.id, reminder.kind, reminder.atMillis)
            }
        }
        val signature = planned.joinToString("|") { "${it.ticketId}:${it.kind}:${it.atMillis}" }
        if (signature == lastSignature) return

        val desired = planned.associateBy { workName(it.ticketId, it.kind) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_NAMES, emptySet()).orEmpty()
        val wm = WorkManager.getInstance(context)
        previous.filter { it !in desired }.forEach { wm.cancelUniqueWork(it) }
        desired.forEach { (name, item) ->
            val storedAt = prefs.getLong(name, Long.MIN_VALUE)
            if (name in previous && storedAt == item.atMillis) return@forEach
            val delay = (item.atMillis - nowMillis).coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<PassReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        KEY_TICKET_ID to item.ticketId,
                        KEY_KIND to item.kind.name
                    )
                )
                .addTag(TAG)
                .build()
            wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }
        val editor = prefs.edit().clear().putStringSet(KEY_NAMES, desired.keys)
        desired.forEach { (name, item) -> editor.putLong(name, item.atMillis) }
        editor.apply()
        lastSignature = signature
    }

    private data class Scheduled(
        val ticketId: Long,
        val kind: PassReminderKind,
        val atMillis: Long
    )

    companion object {
        private const val PREFS = "pass_reminders"
        private const val KEY_NAMES = "names"
        private const val TAG = "pass-reminder"
        private var lastSignature: String? = null

        fun workName(ticketId: Long, kind: PassReminderKind) = "pass-reminder-$ticketId-${kind.name}"
    }
}

class PassReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_TICKET_ID, 0L)
        val kind = inputData.getString(KEY_KIND)?.let { raw ->
            runCatching { PassReminderKind.valueOf(raw) }.getOrNull()
        } ?: return Result.success()
        if (id == 0L) return Result.success()
        val app = applicationContext as LipaBillApp
        val ticket = app.ticketRepository.get(id) ?: return Result.success()
        val start = ticket.occasionMillis ?: return Result.success()
        if (ticket.effectiveStatus() == TicketStatus.USED) return Result.success()
        if (start < System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6)) return Result.success()
        PassReminderNotifier(applicationContext).show(ticket, kind)
        return Result.success()
    }

    companion object {
        const val KEY_TICKET_ID = "ticketId"
        const val KEY_KIND = "kind"
    }
}

class PassReminderNotifier(private val context: Context) {

    fun show(ticket: Ticket, kind: PassReminderKind) {
        val schedule = formatEventSchedule(ticket.occasionMillis, ticket.notes)
        val (title, body) = copy(kind, ticket.title, schedule.time, ticket.venue)
        val open = context.mainActivityPendingIntent(
            (ticket.id xor kind.ordinal.toLong()).toInt()
        ) {
            putExtra(MainActivity.EXTRA_OPEN_TICKET_ID, ticket.id)
        }
        context.postLipaBill(
            channelId = CHANNEL_ID,
            channelName = "Pass reminders",
            channelDescription = "A day before, 24 hours before, and the morning of a saved pass.",
            importance = NotificationManager.IMPORTANCE_HIGH,
            notifyId = notifyId(ticket.id, kind),
            title = title,
            body = body,
            contentIntent = open,
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_EVENT,
            bigText = true
        )
    }

    companion object {
        private const val CHANNEL_ID = "pass_reminders"

        fun notifyId(ticketId: Long, kind: PassReminderKind): Int =
            (ticketId * 10 + kind.ordinal).toInt()

        fun copy(
            kind: PassReminderKind,
            title: String,
            time: String?,
            venue: String?
        ): Pair<String, String> {
            val headline = when (kind) {
                PassReminderKind.DAY_BEFORE -> "Tomorrow"
                PassReminderKind.HOURS_24 -> "In 24 hours"
                PassReminderKind.MORNING_OF -> "This morning"
            }
            val detail = listOfNotNull(
                title.takeIf { it.isNotBlank() },
                time?.takeIf { it.isNotBlank() },
                venue?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            return headline to detail.ifBlank { title }
        }
    }
}

private const val KEY_TICKET_ID = PassReminderWorker.KEY_TICKET_ID
private const val KEY_KIND = PassReminderWorker.KEY_KIND
