package com.lipabill.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.lipabill.app.LipaBillApp
import com.lipabill.app.R
import com.lipabill.app.notify.mainActivityPendingIntent
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.viewmodel.MoneyPlanUi
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home-screen widget: safe to spend and the balance, masked like the app. The eye reveals the
 * amounts for 30 seconds, and only while LipaBill is unlocked within its lock timeout;
 * otherwise it opens the app to unlock. Reveal state lives in memory, so it never survives
 * the process.
 */
class BalanceWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REVEAL -> {
                val app = context.applicationContext as? LipaBillApp ?: return
                if (app.authManager.unlockedWithinTimeout()) {
                    revealUntil = SystemClock.elapsedRealtime() + REVEAL_MS
                    scheduleHide(context)
                }
                refreshAsync(context)
            }
            ACTION_HIDE -> {
                revealUntil = 0L
                refreshAsync(context)
            }
        }
    }

    private fun refreshAsync(context: Context) {
        val pending = goAsync()
        val app = context.applicationContext as? LipaBillApp
        if (app == null) {
            pending.finish()
            return
        }
        app.launchInBackground {
            try {
                render(context, runCatching { app.widgetSnapshot() }.getOrNull(), app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val ACTION_REVEAL = "com.lipabill.app.widget.REVEAL"
        private const val ACTION_HIDE = "com.lipabill.app.widget.HIDE"
        private const val REVEAL_MS = 30_000L
        private val Until = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

        @Volatile
        private var revealUntil = 0L

        /** Redraw every placed widget (after a payment, a plan change, or returning to the app). */
        fun refresh(context: Context) {
            val app = context.applicationContext as? LipaBillApp ?: return
            if (ids(context).isEmpty()) return
            app.launchInBackground {
                render(context, runCatching { app.widgetSnapshot() }.getOrNull(), app)
            }
        }

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, BalanceWidget::class.java))

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, BalanceWidget::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun scheduleHide(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            alarms.set(AlarmManager.ELAPSED_REALTIME, revealUntil, broadcast(context, ACTION_HIDE, 2))
        }

        private fun render(context: Context, plan: MoneyPlanUi?, app: LipaBillApp) {
            val widgetIds = ids(context)
            if (widgetIds.isEmpty()) return
            val revealed = SystemClock.elapsedRealtime() < revealUntil
            val safe = plan?.safe
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            views.setTextViewText(
                R.id.widget_safe,
                when {
                    safe == null -> "Waiting for a message with your balance"
                    revealed -> "${formatKsh(safe.perDay)} a day"
                    else -> "KSh •••• a day"
                }
            )
            views.setTextViewText(
                R.id.widget_until,
                safe?.let {
                    buildString {
                        append("until ")
                        append(it.until.format(Until))
                        if (it.paydayEstimated) append(" (payday)")
                        if (revealed && it.billsDue > 0) append(" · ${formatKsh(it.reservedForBills)} held for bills")
                    }
                } ?: ""
            )
            views.setTextViewText(
                R.id.widget_balance,
                when {
                    safe == null -> ""
                    revealed -> "Balance ${formatKsh(safe.balance)}"
                    else -> "Balance KSh ••••"
                }
            )
            val open = context.mainActivityPendingIntent(7) {}
            views.setOnClickPendingIntent(R.id.widget_root, open)
            views.setImageViewResource(R.id.widget_eye, if (revealed) R.drawable.ic_widget_eye_off else R.drawable.ic_widget_eye)
            views.setContentDescription(
                R.id.widget_eye,
                context.getString(if (revealed) R.string.widget_hide else R.string.widget_reveal)
            )
            views.setOnClickPendingIntent(
                R.id.widget_eye,
                when {
                    revealed -> broadcast(context, ACTION_HIDE, 2)
                    app.authManager.unlockedWithinTimeout() -> broadcast(context, ACTION_REVEAL, 1)
                    // Locked: the eye opens LipaBill to unlock first.
                    else -> open
                }
            )
            AppWidgetManager.getInstance(context).updateAppWidget(widgetIds, views)
        }
    }
}
