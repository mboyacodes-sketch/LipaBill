package com.lipabill.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

/**
 * One permission lesson, shown only when that access is missing.
 * Already-granted access never reaches the dialog.
 */
enum class PermissionLesson(
    val title: String,
    val body: String,
    val allowLabel: String
) {
    Phone(
        title = "Phone access starts the payment",
        body = "Send and Pay place a Safaricom M-Pesa call from this phone, " +
            "and only after you tap Confirm. " +
            "Your M-Pesa PIN stays on the secure keypad and is not stored.",
        allowLabel = "Allow phone"
    ),
    PhoneState(
        title = "Which SIM is Safaricom",
        body = "LipaBill checks the lines on this phone so the M-Pesa call " +
            "goes out on Safaricom. It does not read your call history.",
        allowLabel = "Allow SIM access"
    ),
    PhoneAndSim(
        title = "Phone access for this payment",
        body = "To start M-Pesa, LipaBill places the call and checks which SIM is Safaricom. " +
            "Both are used only for a payment you confirm. " +
            "Call history is not read, and your PIN is not stored.",
        allowLabel = "Continue"
    ),
    Contacts(
        title = "Contacts make names easier",
        body = "When you type a name, LipaBill can match it to people saved on this phone. " +
            "You can still type a number without contacts. " +
            "Those numbers stay on this device.",
        allowLabel = "Allow contacts"
    ),
    Camera(
        title = "Camera scans the pass",
        body = "The camera reads a ticket code so you can keep it in Passes. " +
            "LipaBill does not save photos or video.",
        allowLabel = "Allow camera"
    ),
    Notifications(
        title = "M-Pesa alerts on this phone",
        body = "LipaBill can notify you when an M-Pesa confirmation arrives, " +
            "and if a payment you started needs you. " +
            "You can use the app without alerts.",
        allowLabel = "Allow notifications"
    ),
    Hibernation(
        title = "Keep M-Pesa access",
        body = "If you do not open LipaBill for a few months, Android can remove SMS and phone access. " +
            "History updates and payments then wait until you allow them again.",
        allowLabel = "Keep access"
    )
}

fun phoneLessonFor(needCall: Boolean, needState: Boolean): PermissionLesson? = when {
    needCall && needState -> PermissionLesson.PhoneAndSim
    needCall -> PermissionLesson.Phone
    needState -> PermissionLesson.PhoneState
    else -> null
}

fun Context.permissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Optional prompts the user already declined this process. Required payment access is asked again. */
object PermissionPromptMemory {
    var contactsDeclined: Boolean = false
    var notificationsDeclined: Boolean = false
}

/** Notification access is asked when alerts become useful, not when Home first opens. */
fun Context.shouldOfferPostNotifications(): Boolean =
    Build.VERSION.SDK_INT >= 33 &&
        !permissionGranted(Manifest.permission.POST_NOTIFICATIONS) &&
        !PermissionPromptMemory.notificationsDeclined

@Composable
fun PermissionGuideDialog(
    lesson: PermissionLesson,
    onAllow: () -> Unit,
    onNotNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(lesson.title) },
        text = { Text(lesson.body) },
        confirmButton = {
            TextButton(onClick = onAllow) { Text(lesson.allowLabel) }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text("Not now") }
        }
    )
}

/**
 * Offers contacts access the first time a recipient field is focused.
 * Returns immediately when contacts are already allowed, or the user chose Not now.
 */
@Composable
fun rememberContactsAccessOffer(onGranted: () -> Unit): (focused: Boolean) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    var show by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onGranted() else PermissionPromptMemory.contactsDeclined = true
    }
    if (show) {
        PermissionGuideDialog(
            lesson = PermissionLesson.Contacts,
            onAllow = {
                show = false
                launcher.launch(Manifest.permission.READ_CONTACTS)
            },
            onNotNow = {
                show = false
                PermissionPromptMemory.contactsDeclined = true
            }
        )
    }
    return { focused ->
        if (focused && !show && !PermissionPromptMemory.contactsDeclined) {
            if (context.permissionGranted(Manifest.permission.READ_CONTACTS)) {
                onGranted()
            } else {
                show = true
            }
        }
    }
}
