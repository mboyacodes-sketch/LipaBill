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
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.metrics.runEngagementWork
import com.lipabill.app.notify.mainActivityPendingIntent
import com.lipabill.app.notify.postLipaBill
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.buildMonthWrap
import com.lipabill.app.viewmodel.wrappedMonth
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/** Hour on the 1st when "your month is wrapped" goes out. */
internal const val WRAPPED_READY_HOUR = 9

/** Next 1st-of-the-month at [WRAPPED_READY_HOUR] strictly after [now]. */
fun nextWrappedReady(now: ZonedDateTime): ZonedDateTime {
    val thisMonth = now.toLocalDate().withDayOfMonth(1).atTime(LocalTime.of(WRAPPED_READY_HOUR, 0)).atZone(now.zone)
    return if (now.isBefore(thisMonth)) thisMonth else thisMonth.plusMonths(1)
}

/** Notification title for [month]: "Your October is wrapped". */
fun wrappedReadyTitle(month: YearMonth): String =
    "Your ${month.month.getDisplayName(TextStyle.FULL, Locale.UK)} is wrapped"

fun scheduleWrappedReady(context: Context, zone: ZoneId = ZoneId.systemDefault()) {
    val now = ZonedDateTime.now(zone)
    val delay = java.time.Duration.between(now, nextWrappedReady(now)).toMillis().coerceAtLeast(0L)
    val request = OneTimeWorkRequestBuilder<WrappedReadyWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(WRAPPED_READY_WORK, ExistingWorkPolicy.REPLACE, request)
}

private const val WRAPPED_READY_WORK = "wrapped-ready"

/** On the 1st, tells the user last month's story is ready, then books next month. */
class WrappedReadyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runEngagementWork("wrapped_ready") {
        val app = applicationContext as LipaBillApp
        val month = wrappedMonth(LocalDate.now())
        val scope = app.enabledSources.value
        val ledger = app.repository.observeWalletList(fuliza = false, limit = TransactionListViewModel.PLAN_WINDOW).first()
        val fuliza = if (MoneySource.MPESA in scope) {
            app.repository.observeWalletList(fuliza = true, limit = TransactionListViewModel.WRAP_FULIZA_WINDOW).first()
        } else {
            emptyList()
        }
        val cards = buildMonthWrap(
            month,
            (ledger + fuliza).filter { scope.isEmpty() || it.source in scope },
            app.spendCategoryOverrides.value
        )
        // Nothing moved last month: no story, so no nudge.
        if (cards != null) WrappedReadyNotifier(applicationContext).show(month)
        scheduleWrappedReady(applicationContext)
    }
}

class WrappedReadyNotifier(private val context: Context) {

    /** No amounts: this can show on the lock screen. */
    fun show(month: YearMonth) {
        val open = context.mainActivityPendingIntent(REQUEST) {
            putExtra(MainActivity.EXTRA_OPEN_INSIGHTS, true)
            putExtra(MainActivity.EXTRA_OPEN_WRAPPED, true)
        }
        context.postLipaBill(
            channelId = CHANNEL_ID,
            channelName = "Monthly Wrapped",
            channelDescription = "On the 1st, when last month's story is ready.",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notifyId = NOTIFY_ID,
            title = wrappedReadyTitle(month),
            body = "Money in vs out, your top spots and your money personality. Tap to see.",
            contentIntent = open,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    companion object {
        private const val CHANNEL_ID = "monthly_wrapped"
        private const val NOTIFY_ID = 5442
        private const val REQUEST = 5442
    }
}
