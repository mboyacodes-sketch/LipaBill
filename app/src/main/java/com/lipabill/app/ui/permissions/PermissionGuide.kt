package com.lipabill.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.lipabill.app.MainActivity
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.SetupBlocker
import com.lipabill.app.ui.util.findComponentActivity

/**
 * One permission lesson, shown only when that access is missing.
 * Already-granted access never reaches the dialog.
 */
enum class PermissionLesson(
    val title: String,
    val body: String,
    val allowLabel: String
) {
    Camera(
        title = "Camera scans the pass",
        body = "The camera reads a ticket code so you can keep it in Passes. " +
            "LipaBill does not save photos or video.",
        allowLabel = "Allow camera"
    ),
    Notifications(
        title = "Hear when money moves",
        body = "LipaBill can notify you when a confirmation arrives so you can open the receipt " +
            "without hunting SMS. It also alerts if a payment you started needs you. " +
            "You can use the app without alerts.",
        allowLabel = "Allow alerts"
    ),
    Hibernation(
        title = "Keep payment access",
        body = "If you do not open LipaBill for a few months, Android can remove SMS and phone access. " +
            "History updates and payments then wait until you allow them again.",
        allowLabel = "Keep access"
    )
}

fun Context.permissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Optional prompts the user already declined this process. Required payment access is asked again. */
object PermissionPromptMemory {
    var notificationsDeclined: Boolean = false
}

fun onNotificationPermission(granted: Boolean) {
    AppMetrics.notificationPermission(granted)
    if (!granted) PermissionPromptMemory.notificationsDeclined = true
}

fun onNotificationsDismissed() {
    PermissionPromptMemory.notificationsDeclined = true
    AppMetrics.setupBlocked(SetupBlocker.NotificationsOff)
}

/** Notification access is offered on Home with a reason, and again when a payment needs you. */
fun Context.shouldOfferPostNotifications(): Boolean =
    Build.VERSION.SDK_INT >= 33 &&
        !permissionGranted(Manifest.permission.POST_NOTIFICATIONS) &&
        !PermissionPromptMemory.notificationsDeclined

/** Opens the camera consent wizard, then [onGranted] if access was allowed. */
@Composable
fun rememberCameraAccessOffer(onGranted: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = context.findComponentActivity() as? MainActivity
    return {
        if (context.permissionGranted(Manifest.permission.CAMERA)) {
            onGranted()
        } else {
            activity?.askConsent(FirstRunPage.Camera) {
                if (context.permissionGranted(Manifest.permission.CAMERA)) onGranted()
            }
        }
    }
}
