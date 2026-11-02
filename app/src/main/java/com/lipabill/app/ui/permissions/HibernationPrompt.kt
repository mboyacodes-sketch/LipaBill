package com.lipabill.app.ui.permissions

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import com.lipabill.app.LipaBillApp
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Opens the consent wizard on Home when Android will reset permissions after months of disuse.
 */
@Composable
fun HibernationExemptionPrompt(
    enabled: Boolean,
    onNeedConsent: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LipaBillApp
    var opened by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled || opened || app.securePreferences.hibernationPromptShown) return@LaunchedEffect
        val status = readUnusedAppRestrictions(context)
        val shouldAsk = shouldAskToDisableHibernation(
            status = unusedAppRestrictionOf(status),
            alreadyAsked = false
        )
        if (shouldAsk) {
            opened = true
            app.securePreferences.hibernationPromptShown = true
            onNeedConsent()
        }
    }
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
