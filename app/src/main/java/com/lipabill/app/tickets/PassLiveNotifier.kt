package com.lipabill.app.tickets

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lipabill.app.LipaBillApp
import com.lipabill.app.MainActivity
import com.lipabill.app.R
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.notify.asLipaBill
import com.lipabill.app.notify.canPostNotifications
import com.lipabill.app.notify.ensureChannel
import com.lipabill.app.notify.mainActivityPendingIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Uber-style Live Update while a pass is on today's calendar: a status-bar chip ("7:30 PM",
 * a countdown, "Now"), a bar with the ticket marker moving toward the start, and the button
 * that matters right now. Promoted to the lock screen / Now Bar on Android 16+; a quiet
 * ongoing notification before that. Never shows a code, seat or booking reference.
 */
class PassLiveNotifier(private val context: Context) {

    fun sync(
        tickets: List<Ticket>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        val plan = PassLivePlanner.plan(tickets, nowMillis, zone)
        if (plan == null) {
            clear()
            return
        }
        show(plan, nowMillis)
        scheduleRefresh(plan.refreshAt, nowMillis)
    }

    fun clear() {
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
        WorkManager.getInstance(context).cancelUniqueWork(REFRESH_WORK)
    }

    private fun show(plan: PassLiveContent, nowMillis: Long) {
        if (!context.canPostNotifications()) return
        val manager = NotificationManagerCompat.from(context)
        manager.ensureChannel(
            id = CHANNEL_ID,
            name = "Pass today",
            importance = NotificationManager.IMPORTANCE_LOW,
            description = "A live status while a pass is on for today. Tap to open it."
        )
        val open = openPass(plan.ticketId, requestCode = NOTIFY_ID)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .asLipaBill(context, largeIcon = false)
            .setContentTitle(plan.title)
            .setContentText(plan.body)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            // Live Update: status-bar chip, lock screen, Now Bar (Android 16+).
            .setRequestPromotedOngoing(true)
            .setTimeoutAfter((plan.clearAt - nowMillis).coerceAtLeast(60_000L))
            .setStyle(progressStyle(plan.progress))
            .setPublicVersion(publicVersion(open))
        plan.chip?.let { builder.setShortCriticalText(it) }
        val countdown = plan.countdownTo
        if (countdown != null) {
            builder.setWhen(countdown)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        plan.actions.forEach { action ->
            pendingFor(action, plan)?.let { builder.addAction(0, label(action), it) }
        }
        try {
            manager.notify(NOTIFY_ID, builder.build())
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }

    private fun progressStyle(progress: LiveProgress): NotificationCompat.ProgressStyle {
        val lead = ContextCompat.getColor(context, R.color.ic_launcher_background)
        val event = LIVE_GREEN
        return NotificationCompat.ProgressStyle()
            .setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(progress.leadMinutes).setColor(lead),
                    NotificationCompat.ProgressStyle.Segment(progress.eventMinutes).setColor(event)
                )
            )
            .setProgressPoints(
                listOf(NotificationCompat.ProgressStyle.Point(progress.leadMinutes).setColor(lead))
            )
            .setProgress(progress.value)
            .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_stat_lipabill))
    }

    /** Lock screen with private content hidden: no event name, venue or time. */
    private fun publicVersion(open: PendingIntent) = NotificationCompat.Builder(context, CHANNEL_ID)
        .asLipaBill(context, largeIcon = false)
        .setContentTitle("Pass today")
        .setContentText("Open LipaBill for details.")
        .setContentIntent(open)
        .setOngoing(true)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .build()

    private fun label(action: LiveAction): String = when (action) {
        LiveAction.SHOW_CODE -> "Show code"
        LiveAction.MARK_USED -> "Mark used"
        LiveAction.ADD_BOARDING_PASS -> "Add boarding pass"
        LiveAction.ADD_CODE -> "Add code"
        LiveAction.DIRECTIONS -> "Directions"
    }

    private fun pendingFor(action: LiveAction, plan: PassLiveContent): PendingIntent? {
        val code = NOTIFY_ID + 1 + action.ordinal
        return when (action) {
            // The pass screen opens on the code and raises brightness for active passes.
            LiveAction.SHOW_CODE,
            LiveAction.ADD_BOARDING_PASS,
            LiveAction.ADD_CODE -> openPass(plan.ticketId, code)
            LiveAction.MARK_USED -> PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, PassLiveActionReceiver::class.java)
                    .setAction(ACTION_MARK_USED)
                    .putExtra(EXTRA_TICKET_ID, plan.ticketId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // Only the venue as printed goes to the maps app, and only on tap.
            LiveAction.DIRECTIONS -> plan.venue?.let { venue ->
                PendingIntent.getActivity(
                    context,
                    code,
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(venue)))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        }
    }

    private fun openPass(ticketId: Long, requestCode: Int): PendingIntent =
        context.mainActivityPendingIntent(requestCode) {
            putExtra(MainActivity.EXTRA_OPEN_TICKET_ID, ticketId)
        }

    /** Redraws at the next stage change, or every 15 minutes while the bar is moving. */
    private fun scheduleRefresh(at: Long?, nowMillis: Long) {
        val wm = WorkManager.getInstance(context)
        if (at == null) {
            wm.cancelUniqueWork(REFRESH_WORK)
            return
        }
        val request = OneTimeWorkRequestBuilder<PassLiveRefreshWorker>()
            .setInitialDelay((at - nowMillis).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(REFRESH_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val CHANNEL_ID = "pass_today_live"
        const val NOTIFY_ID = 4_720_119
        const val ACTION_MARK_USED = "com.lipabill.app.action.PASS_MARK_USED"
        const val EXTRA_TICKET_ID = "ticketId"
        private const val REFRESH_WORK = "pass-live-refresh"
        private const val LIVE_GREEN = 0xFF15803D.toInt()

        /** Re-reads passes and redraws (or clears) the live notification. */
        suspend fun resync(app: LipaBillApp) {
            val tickets = app.ticketRepository.observeAll().first()
            PassLiveNotifier(app).sync(tickets)
        }
    }
}

/** Redraws the live notification at stage changes and while its bar moves. */
class PassLiveRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? LipaBillApp ?: return Result.success()
        runCatching { PassLiveNotifier.resync(app) }
        return Result.success()
    }
}

/** "Mark used" from the notification: marks the pass, then redraws or clears. */
class PassLiveActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != PassLiveNotifier.ACTION_MARK_USED) return
        val id = intent.getLongExtra(PassLiveNotifier.EXTRA_TICKET_ID, 0L)
        if (id == 0L) return
        val app = context.applicationContext as? LipaBillApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.ticketRepository.markUsed(id)
                PassLiveNotifier.resync(app)
            } finally {
                pending.finish()
            }
        }
    }
}
