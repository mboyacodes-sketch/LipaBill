package com.lipabill.app.notify

import android.app.NotificationManager
import android.content.Context
import com.lipabill.app.MainActivity
import com.lipabill.app.engage.MoneyMoments

/** Posts a money moment; tapping it opens Insights (plan, budgets, goals). */
object MoneyMomentNotifier {

    private const val CHANNEL_ID = "money_moments"

    fun show(context: Context, moment: MoneyMoments.Moment) {
        val open = context.mainActivityPendingIntent(moment.key.hashCode()) {
            putExtra(MainActivity.EXTRA_OPEN_INSIGHTS, true)
        }
        context.postLipaBill(
            channelId = CHANNEL_ID,
            channelName = "Money moments",
            channelDescription = "Budget pace, payday and habit nudges right after a payment.",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            notifyId = moment.key.hashCode(),
            title = moment.title,
            body = moment.body,
            contentIntent = open,
            bigText = true
        )
    }
}
