package com.lipabill.app.tickets

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lipabill.app.LipaBillApp
import com.lipabill.app.MainActivity
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.tickets.PassContextSync
import com.lipabill.app.data.tickets.PassReminderKind
import com.lipabill.app.data.tickets.PassReminderLeadTimes
import com.lipabill.app.data.tickets.planPassReminders
import com.lipabill.app.metrics.runEngagementWork
import com.lipabill.app.notify.mainActivityPendingIntent
import com.lipabill.app.notify.postLipaBill
import com.lipabill.app.ui.util.formatEventSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps one scheduled alert per pass per kind. Source of truth is the `reminders` table
 * (via [PassContextSync]); WorkManager jobs stay named `pass-reminder-<id>-<kind>`.
 */
class PassReminderScheduler(
    private val context: Context,
    private val contextSync: PassContextSync? = null,
    private val leadTimes: () -> PassReminderLeadTimes = { PassReminderLeadTimes.DEFAULT }
) {

    suspend fun sync(tickets: List<Ticket>, nowMillis: Long = System.currentTimeMillis()) {
        val zone = ZoneId.systemDefault()
        val leads = leadTimes()
        val planned = tickets.flatMap { ticket ->
            val start = ticket.occasionMillis ?: return@flatMap emptyList()
            if (ticket.effectiveStatus(nowMillis) != TicketStatus.ACTIVE) return@flatMap emptyList()
            planPassReminders(start, nowMillis, zone, leads).map { reminder ->
                Scheduled(ticket.id, reminder.kind, reminder.atMillis)
            }
        }
        val signature = planned.joinToString("|") { "${it.ticketId}:${it.kind}:${it.atMillis}" } +
            "|${leads.dayBeforeHour}:${leads.hoursBefore}:${leads.morningHour}"
        if (signature == lastSignature) return

        val desired = planned.associateBy { PassContextSync.workName(it.ticketId, it.kind) }
        val wm = WorkManager.getInstance(context)

        // One-time move off SharedPreferences onto the reminders table.
        migrateLegacyPrefs(wm)

        // Memory alone forgets after a restart; the table remembers what was scheduled.
        val previous = (lastWorkNames ?: emptySet()) +
            (contextSync?.let { runCatching { it.scheduledWorkNames() }.getOrNull() } ?: emptySet())
        previous.filter { it !in desired }.forEach { wm.cancelUniqueWork(it) }
        desired.forEach { (name, item) ->
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
        lastWorkNames = desired.keys
        lastSignature = signature

        // Persist events/reminders (back-fill + keep table aligned with the plan).
        val sync = contextSync ?: return
        val activeIds = tickets.map { it.id }.toSet()
        for (ticket in tickets) {
            sync.syncPass(ticket, nowMillis, zone, leads)
        }
        for (name in previous - desired.keys) {
            val id = name.removePrefix("pass-reminder-").substringBefore('-').toLongOrNull()
            if (id != null && id !in activeIds) sync.removePass(id)
        }
    }

    private fun migrateLegacyPrefs(wm: WorkManager) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val legacy = prefs.getStringSet(KEY_NAMES, null) ?: return
        legacy.forEach { wm.cancelUniqueWork(it) }
        prefs.edit().clear().apply()
    }

    fun ensureDailyRecheck() {
        val request = PeriodicWorkRequestBuilder<PassReminderRecheckWorker>(1, TimeUnit.DAYS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            RECHECK_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun requestImmediateRecheck() {
        val request = OneTimeWorkRequestBuilder<PassReminderRecheckWorker>()
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RECHECK_ONCE,
            ExistingWorkPolicy.REPLACE,
            request
        )
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
        private const val RECHECK_WORK = "pass-reminder-recheck"
        private const val RECHECK_ONCE = "pass-reminder-recheck-once"
        private var lastSignature: String? = null
        private var lastWorkNames: Set<String>? = null

        fun workName(ticketId: Long, kind: PassReminderKind) =
            PassContextSync.workName(ticketId, kind)
    }
}

class PassReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runEngagementWork("pass_reminder") {
        val id = inputData.getLong(KEY_TICKET_ID, 0L)
        val kind = inputData.getString(KEY_KIND)?.let { raw ->
            runCatching { PassReminderKind.valueOf(raw) }.getOrNull()
        } ?: return@runEngagementWork
        if (id == 0L) return@runEngagementWork
        val app = applicationContext as LipaBillApp
        val ticket = app.ticketRepository.get(id) ?: return@runEngagementWork
        val start = ticket.occasionMillis ?: return@runEngagementWork
        if (ticket.effectiveStatus() == TicketStatus.USED) return@runEngagementWork
        if (start < System.currentTimeMillis() - TimeUnit.HOURS.toMillis(6)) {
            return@runEngagementWork
        }
        val sync = runCatching { app.passContextSync }.getOrNull()
        if (sync != null && !sync.isStillWanted(id, kind)) return@runEngagementWork
        sync?.markReminderFired(PassContextSync.workName(id, kind))
        PassReminderNotifier(applicationContext).show(ticket, kind)
    }

    companion object {
        const val KEY_TICKET_ID = "ticketId"
        const val KEY_KIND = "kind"
    }
}

/** Re-plans reminders after timezone/clock changes, app updates, and once a day. */
class PassReminderRecheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? LipaBillApp ?: return@withContext Result.success()
        val tickets = runCatching { app.ticketRepository.observeAll().first() }.getOrNull()
            ?: return@withContext Result.success()
        PassReminderScheduler(
            app,
            runCatching { app.passContextSync }.getOrNull(),
            { app.securePreferences.passReminderLeadTimes }
        ).sync(tickets)
        Result.success()
    }
}

class PassReminderRecheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        PassReminderScheduler(context.applicationContext).requestImmediateRecheck()
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
        // Private shade may show title/time/venue; lock screen stays generic — never code or booking ref.
        context.postLipaBill(
            channelId = CHANNEL_ID,
            channelName = "Pass reminders",
            channelDescription = "A day before, ahead of time, and the morning of a saved pass.",
            importance = NotificationManager.IMPORTANCE_HIGH,
            notifyId = notifyId(ticket.id, kind),
            title = title,
            body = body,
            contentIntent = open,
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_EVENT,
            bigText = true,
            publicTitle = "Pass reminder",
            publicBody = "Open LipaBill for details."
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
                PassReminderKind.HOURS_24 -> "Coming up"
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
