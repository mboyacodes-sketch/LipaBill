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
        context.postLipaBill(
            channelId = CHANNEL_ID,
            channelName = "M-Pesa activity",
            channelDescription = "A confirmation when M-Pesa activity arrives on this phone.",
            importance = NotificationManager.IMPORTANCE_HIGH,
            notifyId = tx.code.hashCode(),
            title = title,
            body = body,
            contentIntent = open,
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
