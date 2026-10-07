package com.lipabill.app.engage

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lipabill.app.LipaBillApp
import com.lipabill.app.MainActivity
import com.lipabill.app.R
import java.util.concurrent.TimeUnit

class EngagementScheduler(private val context: Context) {

    fun ensureScheduled() {
        val app = context.applicationContext as LipaBillApp
        val prefs = app.securePreferences
        if (prefs.challengesEnabled || prefs.weeklyCheckInEnabled) {
            scheduleCheckIn(ChallengeClock.deviceZone())
        }
        if (prefs.challengesEnabled) {
            scheduleChallengeSweep()
        }
    }

    fun scheduleChallengeSweep() {
        val request = OneTimeWorkRequestBuilder<ChallengeWindowWorker>()
            .setInitialDelay(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHALLENGE_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleChallengeEnd(endAt: Long, now: Long = System.currentTimeMillis()) {
        val delay = (endAt - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ChallengeWindowWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHALLENGE_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun scheduleCheckIn(zone: java.time.ZoneId, now: Long = System.currentTimeMillis()) {
        val at = ChallengeClock.nextSundayEvening(now, zone)
        val delay = (at - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<WeeklyCheckInWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CHECKIN_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    companion object {
        private const val CHALLENGE_WORK = "challenge-window"
        private const val CHECKIN_WORK = "weekly-checkin"
    }
}

class ChallengeWindowWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LipaBillApp
        if (!app.securePreferences.challengesEnabled) return Result.success()
        val nextEnd = app.engagement.evaluate()
        if (nextEnd != null) {
            app.engagementScheduler.scheduleChallengeEnd(nextEnd)
        } else {
            app.engagementScheduler.scheduleChallengeSweep()
        }
        return Result.success()
    }
}

class WeeklyCheckInWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LipaBillApp
        if (app.securePreferences.weeklyCheckInEnabled) {
            CheckInNotifier(applicationContext).show()
        }
        app.engagementScheduler.scheduleCheckIn(ChallengeClock.deviceZone())
        return Result.success()
    }
}

class CheckInNotifier(private val context: Context) {

    fun show() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        ensureChannel(manager)
        val open = PendingIntent.getActivity(
            context,
            REQUEST,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_CHECK_IN, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lipabill)
            .setContentTitle(context.getString(R.string.checkin_notification_title))
            .setContentText(context.getString(R.string.checkin_notification_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        try {
            manager.notify(NOTIFY_ID, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }

    private fun ensureChannel(manager: NotificationManagerCompat) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.checkin_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.checkin_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "weekly_checkin"
        private const val NOTIFY_ID = 5441
        private const val REQUEST = 5441
    }
}
