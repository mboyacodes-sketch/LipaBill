package com.lipabill.app.data.sms

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.lipabill.app.MainActivity
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.notify.mainActivityPendingIntent
import com.lipabill.app.notify.postLipaBill

/**
 * Posts one notification for a confirmation that just arrived.
 * Older inbox imports stay quiet.
 */
class LedgerNotifier(private val context: Context) {

    fun show(tx: MpesaTransaction) {
        val (title, body) = ledgerNotificationCopy(tx)
        val open = context.mainActivityPendingIntent(tx.code.hashCode()) {
            action = MainActivity.ACTION_OPEN_RECEIPT
            data = Uri.parse(receiptOpenData(tx.id, tx.code))
            putExtra(MainActivity.EXTRA_OPEN_RECEIPT_ID, tx.id)
            putExtra(MainActivity.EXTRA_OPEN_RECEIPT_CODE, tx.code)
        }
        postActivity(
            notifyId = tx.code.hashCode(),
            title = title,
            body = body,
            contentIntent = open
        )
    }

    /** Failure SMS undid a mistaken or unsettled payment — not a deposit card. */
    fun showFailure(title: String, body: String, notifyId: Int) {
        val open = context.mainActivityPendingIntent(notifyId) {
            // Home — there is no receipt row for a voided payment.
        }
        postActivity(
            notifyId = notifyId,
            title = title,
            body = body,
            contentIntent = open
        )
    }

    private fun postActivity(
        notifyId: Int,
        title: String,
        body: String,
        contentIntent: android.app.PendingIntent
    ) {
        context.postLipaBill(
            channelId = CHANNEL_ID,
            channelName = "Money activity",
            channelDescription = "A confirmation when money activity arrives on this phone.",
            importance = NotificationManager.IMPORTANCE_HIGH,
            notifyId = notifyId,
            title = title,
            body = body,
            contentIntent = contentIntent,
            vibrate = true,
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_STATUS,
            bigText = true,
            prepare = { manager ->
                // The first channel was default importance, which stays in the shade.
                // Importance is fixed after creation, so alerts use a new id.
                if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
                    manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
                }
            }
        )
    }

    companion object {
        /** Historical id. The user-visible name is "Money activity". */
        const val CHANNEL_ID = "mpesa_activity_alert"
        private const val LEGACY_CHANNEL_ID = "mpesa_activity"

        /** Live arrivals only. A history import is older than this. */
        const val FRESH_WINDOW_MS = 20 * 60 * 1000L
        private const val CLOCK_SKEW_MS = 2 * 60 * 1000L

        fun isFresh(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
            val age = nowMillis - timestampMillis
            return age in -CLOCK_SKEW_MS..FRESH_WINDOW_MS
        }
    }
}
