package com.lipabill.app.tickets

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lipabill.app.MainActivity
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.notify.mainActivityPendingIntent
import com.lipabill.app.notify.postLipaBill
import com.lipabill.app.ui.util.formatEventSchedule
import java.time.Instant
import java.time.ZoneId

/**
 * Uber-style status-bar chip while a pass is on today’s calendar.
 * Quiet + ongoing: not a reminder ping — a live handle into the pass until it’s marked used
 * or the day rolls over. Never shows codes or booking refs.
 */
class PassLiveNotifier(private val context: Context) {

    fun sync(
        tickets: List<Ticket>,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val live = tickets
            .filter { it.effectiveStatus(nowMillis) != TicketStatus.USED && it.isOnDay(zone, today) }
            .sortedBy { it.occasionMillis ?: Long.MAX_VALUE }
        if (live.isEmpty()) {
            clear()
            return
        }
        show(live, nowMillis)
    }

    fun clear() {
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
    }

    private fun show(live: List<Ticket>, nowMillis: Long) {
        val primary = live.first()
        val start = primary.occasionMillis
        val schedule = formatEventSchedule(start, primary.notes)
        val (title, body) = copy(primary, live.size, schedule.time, nowMillis)
        val open = context.mainActivityPendingIntent(NOTIFY_ID) {
            putExtra(MainActivity.EXTRA_OPEN_TICKET_ID, primary.id)
        }
        val countdown = start != null && start > nowMillis
        context.postLipaBill(
            channelId = CHANNEL_ID,
            channelName = "Pass today",
            channelDescription = "A quiet status while a pass is on for today. Tap to open it.",
            importance = NotificationManager.IMPORTANCE_LOW,
            notifyId = NOTIFY_ID,
            title = title,
            body = body,
            contentIntent = open,
            priority = NotificationCompat.PRIORITY_LOW,
            category = NotificationCompat.CATEGORY_EVENT,
            bigText = true,
            publicTitle = "Pass today",
            publicBody = "Open LipaBill for details.",
            ongoing = true,
            whenMillis = start?.takeIf { countdown },
            countdown = countdown
        )
    }

    companion object {
        const val CHANNEL_ID = "pass_today_live"
        const val NOTIFY_ID = 4_720_119

        fun copy(
            primary: Ticket,
            count: Int,
            time: String?,
            nowMillis: Long
        ): Pair<String, String> {
            val start = primary.occasionMillis
            val headline = when {
                start != null && start > nowMillis -> "Pass today"
                else -> "Show your pass"
            }
            val detail = buildList {
                primary.title.takeIf { it.isNotBlank() }?.let(::add)
                time?.takeIf { it.isNotBlank() }?.let(::add)
                primary.venue?.takeIf { it.isNotBlank() }?.let(::add)
                if (count > 1) add("+${count - 1} more")
            }.joinToString(" · ")
            return headline to detail.ifBlank { primary.title }
        }
    }
}
