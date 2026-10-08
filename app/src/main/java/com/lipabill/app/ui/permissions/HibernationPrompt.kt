package com.lipabill.app.ui.permissions

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import com.lipabill.app.LipaBillApp
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * One explanation, on Home, when Android will reset permissions after months of disuse.
 * The settings page must be opened with a result contract, per the platform contract.
 */
@Composable
fun HibernationExemptionPrompt(enabled: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext as LipaBillApp
    var show by remember { mutableStateOf(false) }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    LaunchedEffect(enabled) {
        if (!enabled || app.securePreferences.hibernationPromptShown) {
            show = false
            return@LaunchedEffect
        }
        val status = readUnusedAppRestrictions(context)
        show = shouldAskToDisableHibernation(
            status = unusedAppRestrictionOf(status),
            alreadyAsked = false
        )
    }

    if (!show) return

    fun dismiss() {
        app.securePreferences.hibernationPromptShown = true
        show = false
    }

    PermissionGuideDialog(
        lesson = PermissionLesson.Hibernation,
        onAllow = {
            dismiss()
            val intent = runCatching {
                IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
            }.getOrNull() ?: return@PermissionGuideDialog
            settingsLauncher.launch(intent)
        },
        onNotNow = { dismiss() }
    )
}

private suspend fun readUnusedAppRestrictions(context: Context): Int =
    suspendCancellableCoroutine { cont ->
        val future = PackageManagerCompat.getUnusedAppRestrictionsStatus(context)
        future.addListener(
            {
                val status = runCatching { future.get() }
                    .getOrDefault(UnusedAppRestrictionsConstants.ERROR)
                if (cont.isActive) cont.resume(status)
            },
            ContextCompat.getMainExecutor(context)
        )
    }
