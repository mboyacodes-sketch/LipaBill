package com.lipabill.app.engage

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lipabill.app.LipaBillApp
import com.lipabill.app.MainActivity
import com.lipabill.app.R
import com.lipabill.app.notify.mainActivityPendingIntent
import com.lipabill.app.notify.postLipaBill
import java.util.concurrent.TimeUnit

class EngagementScheduler(private val context: Context) {

    fun ensureScheduled() {
        scheduleCheckIn(ChallengeClock.deviceZone())
        scheduleChallengeSweep()
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
        CheckInNotifier(applicationContext).show()
        app.engagementScheduler.scheduleCheckIn(ChallengeClock.deviceZone())
        return Result.success()
    }
}

class CheckInNotifier(private val context: Context) {

    fun show() {
        val open = context.mainActivityPendingIntent(REQUEST) {
            putExtra(MainActivity.EXTRA_OPEN_CHECK_IN, true)
        }
        context.postLipaBill(
            channelId = CHANNEL_ID,
            channelName = context.getString(R.string.checkin_channel_name),
            channelDescription = context.getString(R.string.checkin_channel_description),
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notifyId = NOTIFY_ID,
            title = context.getString(R.string.checkin_notification_title),
            body = context.getString(R.string.checkin_notification_body),
            contentIntent = open,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    companion object {
        private const val CHANNEL_ID = "weekly_checkin"
        private const val NOTIFY_ID = 5441
        private const val REQUEST = 5441
    }
}
