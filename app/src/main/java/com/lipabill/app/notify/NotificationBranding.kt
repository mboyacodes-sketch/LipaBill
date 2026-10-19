package com.lipabill.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.lipabill.app.MainActivity
import com.lipabill.app.R

/**
 * Status-bar icons have to be a white silhouette. The launcher art is a solid
 * bitmap, so using it as the small icon draws a white square. The shade still
 * shows the real app icon through the large icon, tinted with the brand navy.
 */
fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

fun NotificationCompat.Builder.asLipaBill(context: Context): NotificationCompat.Builder =
    setSmallIcon(R.drawable.ic_stat_lipabill)
        .setLargeIcon(lipaBillLargeIcon(context))
        .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))

/** Creates the channel once. Importance cannot change after the first create. */
fun NotificationManagerCompat.ensureChannel(
    id: String,
    name: String,
    importance: Int,
    description: String,
    vibrate: Boolean = false
) {
    val existing = getNotificationChannel(id)
    if (existing != null) {
        if (existing.lockscreenVisibility != Notification.VISIBILITY_PRIVATE) {
            existing.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            createNotificationChannel(existing)
        }
        return
    }
    createNotificationChannel(
        NotificationChannel(id, name, importance).apply {
            this.description = description
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            if (vibrate) enableVibration(true)
        }
    )
}

/** Opens [MainActivity] from a notification, replacing any stale tap target. */
fun Context.mainActivityPendingIntent(
    requestCode: Int,
    configure: Intent.() -> Unit = {}
): PendingIntent {
    val intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP
        configure()
    }
    return PendingIntent.getActivity(
        this,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/**
 * Shared post path for ledger, pass, and check-in alerts.
 * [prepare] runs after the permission check and before the channel is created,
 * so a caller can retire a legacy channel id.
 */
fun Context.postLipaBill(
    channelId: String,
    channelName: String,
    channelDescription: String,
    importance: Int,
    notifyId: Int,
    title: String,
    body: String,
    contentIntent: PendingIntent,
    vibrate: Boolean = false,
    priority: Int = NotificationCompat.PRIORITY_DEFAULT,
    category: String? = null,
    bigText: Boolean = false,
    prepare: (NotificationManagerCompat) -> Unit = {}
) {
    if (!canPostNotifications()) return
    val manager = NotificationManagerCompat.from(this)
    prepare(manager)
    manager.ensureChannel(
        id = channelId,
        name = channelName,
        importance = importance,
        description = channelDescription,
        vibrate = vibrate
    )
    val builder = NotificationCompat.Builder(this, channelId)
        .asLipaBill(this)
        .setContentTitle(title)
        .setContentText(body)
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setPriority(priority)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    if (bigText) {
        builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
    }
    if (category != null) builder.setCategory(category)
    try {
        manager.notify(notifyId, builder.build())
    } catch (_: SecurityException) {
        // Permission was revoked between the check and the post.
    }
}

private fun lipaBillLargeIcon(context: Context): Bitmap {
    val size = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    return context.packageManager
        .getApplicationIcon(context.packageName)
        .mutate()
        .toBitmap(width = size, height = size)
}
