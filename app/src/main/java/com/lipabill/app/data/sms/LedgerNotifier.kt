package com.lipabill.app.data.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lipabill.app.MainActivity
import com.lipabill.app.R
import com.lipabill.app.data.model.MpesaTransaction

/**
 * Posts one notification for a confirmation that just arrived.
 * Older inbox imports stay quiet.
 */
class LedgerNotifier(private val context: Context) {

    fun show(tx: MpesaTransaction) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        ensureChannel(manager)
        val (title, body) = ledgerNotificationCopy(tx)
        val open = PendingIntent.getActivity(
            context,
            tx.code.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_RECEIPT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse(receiptOpenData(tx.id, tx.code))
                putExtra(MainActivity.EXTRA_OPEN_RECEIPT_ID, tx.id)
                putExtra(MainActivity.EXTRA_OPEN_RECEIPT_CODE, tx.code)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lipabill)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            manager.notify(tx.code.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the post.
        }
    }

    private fun ensureChannel(manager: NotificationManagerCompat) {
        // The first channel was default importance, which stays in the shade.
        // Channel importance is fixed after creation, so alerts use a new id.
        if (manager.getNotificationChannel(LEGACY_CHANNEL_ID) != null) {
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "M-Pesa activity",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "A confirmation when M-Pesa activity arrives on this phone."
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
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
