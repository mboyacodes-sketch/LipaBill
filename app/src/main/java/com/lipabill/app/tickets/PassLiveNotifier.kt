package com.lipabill.app.tickets

import android.app.NotificationManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
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
        val look = lookFor(nowMillis)
        show(plan, nowMillis, look)
        // A first Live Update is a probe: look again shortly to learn whether it was promoted.
        val probe = look == LiveLook.LIVE_UPDATE && learnedLook() == null
        val next = if (probe) minOf(plan.refreshAt ?: Long.MAX_VALUE, nowMillis + PROBE_MS) else plan.refreshAt
        scheduleRefresh(next, nowMillis)
    }

    fun clear() {
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
        WorkManager.getInstance(context).cancelUniqueWork(REFRESH_WORK)
    }

    private fun show(plan: PassLiveContent, nowMillis: Long, look: LiveLook) {
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
            .setTimeoutAfter((plan.clearAt - nowMillis).coerceAtLeast(60_000L))
            .setPublicVersion(publicVersion(open))
        if (look == LiveLook.LIVE_UPDATE) {
            // Live Update: status-bar chip, lock screen, Now Bar (Android 16+). Promotion
            // forbids custom views, so this keeps the system progress template.
            builder.setRequestPromotedOngoing(true)
                .setStyle(progressStyle(plan.progress))
                .addExtras(android.os.Bundle().apply { putBoolean(EXTRA_ASKED_PROMOTION, true) })
            plan.chip?.let { builder.setShortCriticalText(it) }
        } else {
            // No Live Updates here: the ride-style card, drawn by us inside the system frame.
            builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(card(plan, withTrack = false))
                .setCustomBigContentView(card(plan, withTrack = true))
        }
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
            .setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_live_ticket))
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

    /**
     * Live Update where the phone really promotes it (status-bar chip), the ride-style card
     * everywhere else. Learned from the notification itself and re-checked weekly, since a
     * system update can switch Live Updates on.
     */
    private fun lookFor(nowMillis: Long): LiveLook {
        if (Build.VERSION.SDK_INT < 36) return LiveLook.CARD
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = learnedLook()
        if (known != null && nowMillis - prefs.getLong(KEY_CHECKED_AT, 0L) < RECHECK_MS) return known
        val shown = context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            ?.firstOrNull { it.id == NOTIFY_ID }
            ?.notification
        if (shown != null && shown.extras.getBoolean(EXTRA_ASKED_PROMOTION)) {
            val look = if (isPromoted(shown)) LiveLook.LIVE_UPDATE else LiveLook.CARD
            prefs.edit().putString(KEY_LOOK, look.name).putLong(KEY_CHECKED_AT, nowMillis).apply()
            return look
        }
        // Not known yet: ask for a Live Update once; the next refresh reads the answer.
        prefs.edit().remove(KEY_LOOK).apply()
        return LiveLook.LIVE_UPDATE
    }

    private fun learnedLook(): LiveLook? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LOOK, null)
            ?.let { runCatching { LiveLook.valueOf(it) }.getOrNull() }

    @androidx.annotation.RequiresApi(36)
    private fun isPromoted(notification: android.app.Notification): Boolean =
        notification.flags and android.app.Notification.FLAG_PROMOTED_ONGOING != 0

    /** Ride-style card: coloured countdown, event, when and where, then the route track. */
    private fun card(plan: PassLiveContent, withTrack: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_pass_live)
        val lead = if (plan.stage.isBlocked) warnColor() else accentColor()
        val countdown = plan.countdownTo
        if (countdown != null) {
            views.setViewVisibility(R.id.live_countdown, View.VISIBLE)
            views.setChronometer(
                R.id.live_countdown,
                SystemClock.elapsedRealtime() + (countdown - System.currentTimeMillis()),
                null,
                true
            )
            views.setChronometerCountDown(R.id.live_countdown, true)
            views.setTextColor(R.id.live_countdown, lead)
            views.setTextViewText(R.id.live_headline, plan.leadRest)
        } else {
            views.setViewVisibility(R.id.live_countdown, View.GONE)
            val headline = SpannableString(plan.lead + plan.leadRest).apply {
                setSpan(ForegroundColorSpan(lead), 0, plan.lead.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            views.setTextViewText(R.id.live_headline, headline)
        }
        views.setTextViewText(R.id.live_event, plan.eventName)
        views.setTextViewText(R.id.live_time, plan.startText)
        views.setTextViewText(R.id.live_place, plan.venue.orEmpty())
        if (withTrack) {
            views.setImageViewBitmap(R.id.live_track, trackBitmap(plan.progress, accentColor()))
        } else {
            views.setViewVisibility(R.id.live_track, View.GONE)
        }
        return views
    }

    /**
     * The route: solid behind the ticket marker, dashed ahead, a stop at the start and one at
     * the end. Drawn once per refresh; the countdown text does the minute-by-minute work.
     */
    private fun trackBitmap(progress: LiveProgress, accent: Int): Bitmap {
        val metrics = context.resources.displayMetrics
        val d = metrics.density
        val width = (metrics.widthPixels - 72 * d).toInt().coerceAtLeast((200 * d).toInt())
        val height = (28 * d).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val cy = height / 2f
        val x0 = 12 * d
        val x1 = width - 12 * d
        fun xAt(minutes: Int) = x0 + (x1 - x0) * (minutes.toFloat() / progress.max.coerceAtLeast(1))
        val xNow = xAt(progress.value)
        val xStart = xAt(progress.leadMinutes)
        val grey = greyColor()
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 4 * d
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(x0, cy, xNow, cy, line.apply { color = accent })
        if (xNow < x1) {
            line.color = grey
            line.pathEffect = DashPathEffect(floatArrayOf(7 * d, 6 * d), 0f)
            canvas.drawLine(xNow, cy, x1, cy, line)
        }
        drawStop(canvas, xStart, cy, if (progress.value >= progress.leadMinutes) accent else grey, d)
        drawStop(canvas, x1, cy, if (progress.value >= progress.max) accent else grey, d)
        // The marker: a pill with the ticket in it, like the car on a pickup route.
        val pillW = 32 * d
        val pillH = 22 * d
        val left = (xNow - pillW / 2).coerceIn(0f, width - pillW)
        val pill = RectF(left, cy - pillH / 2, left + pillW, cy + pillH / 2)
        canvas.drawRoundRect(pill, pillH / 2, pillH / 2, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
        ContextCompat.getDrawable(context, R.drawable.ic_live_ticket)?.mutate()?.let { icon ->
            val s = (14 * d).toInt()
            val l = (pill.centerX() - s / 2f).toInt()
            val t = (pill.centerY() - s / 2f).toInt()
            icon.setTint(android.graphics.Color.WHITE)
            icon.setBounds(l, t, l + s, t + s)
            icon.draw(canvas)
        }
        return bitmap
    }

    private fun drawStop(canvas: android.graphics.Canvas, x: Float, cy: Float, color: Int, d: Float) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * d
        }
        canvas.drawCircle(x, cy, 7 * d, ring)
        canvas.drawCircle(x, cy, 3 * d, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private val night: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Brand navy, lifted on a dark shade so it stays readable. */
    private fun accentColor(): Int = if (night) 0xFF8FB3E3.toInt() else 0xFF2F4A6E.toInt()
    private fun warnColor(): Int = if (night) 0xFFF5C26B.toInt() else 0xFFB45309.toInt()
    private fun greyColor(): Int = if (night) 0xFF6B7280.toInt() else 0xFFB8BEC8.toInt()

    private fun label(action: LiveAction): String = when (action) {
        LiveAction.SHOW_CODE -> "Show ticket"
        LiveAction.MARK_USED -> "Mark as used"
        LiveAction.ADD_BOARDING_PASS -> "Add boarding pass"
        LiveAction.ADD_CODE -> "Add entry code"
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
        private const val PREFS = "pass_live"
        private const val KEY_LOOK = "look"
        private const val KEY_CHECKED_AT = "look_checked_at"
        private const val EXTRA_ASKED_PROMOTION = "com.lipabill.app.ASKED_PROMOTION"
        private const val RECHECK_MS = 7L * 24L * 60L * 60L * 1000L
        private const val PROBE_MS = 60_000L
        private const val LIVE_GREEN = 0xFF15803D.toInt()

        /** Re-reads passes and redraws (or clears) the live notification. */
        suspend fun resync(app: LipaBillApp) {
            val tickets = app.ticketRepository.observeAll().first()
            PassLiveNotifier(app).sync(tickets)
        }
    }
}

/** Which notification the phone gets: the system Live Update or our ride-style card. */
enum class LiveLook { LIVE_UPDATE, CARD }

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
