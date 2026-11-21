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
import com.lipabill.app.ussd.AccessibilityHelper
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Opens the consent wizard on Home when Android will reset permissions after months of disuse.
 * Re-asks rarely when Accessibility was preferred but the Secure grant cleared.
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
        if (!enabled || opened) return@LaunchedEffect
        val statusCode = readUnusedAppRestrictions(context)
        val status = unusedAppRestrictionOf(statusCode)
        if (status == UnusedAppRestriction.Disabled) {
            app.securePreferences.hibernationExemptionSatisfied = true
            app.securePreferences.hibernationPromptShown = true
            return@LaunchedEffect
        }
        val prefs = app.securePreferences
        val now = System.currentTimeMillis()
        val last = prefs.hibernationLastPromptAt
        val cooldownElapsed = last <= 0L || now - last >= HIBERNATION_REASK_COOLDOWN_MS
        val grantCleared = prefs.accessibilityPreferredOn &&
            !AccessibilityHelper.isLipaBillListedInSecureSettings(context)
        val shouldAsk = shouldAskToDisableHibernation(
            status = status,
            alreadyAsked = prefs.hibernationPromptShown || last > 0L,
            grantClearedWhilePreferred = grantCleared,
            cooldownElapsed = cooldownElapsed,
            exemptionSatisfied = prefs.hibernationExemptionSatisfied
        )
        if (shouldAsk) {
            opened = true
            prefs.hibernationLastPromptAt = now
            // Keep hibernationPromptShown as "asked once" for first-pass UX, but
            // exemptionSatisfied is what permanently stops asks after Disabled.
            prefs.hibernationPromptShown = true
            onNeedConsent()
        }
    }
}

/**
 * OEM battery / Never-sleeping coach when Accessibility is preferred but unbound,
 * or after first enable without battery exemption.
 */
@Composable
fun OemSurvivalPrompt(
    enabled: Boolean,
    onNeedConsent: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as LipaBillApp
    var opened by remember { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled || opened) return@LaunchedEffect
        val unused = unusedAppRestrictionOf(readUnusedAppRestrictions(context))
        val health = AccessibilityHealthProbe.snapshot(context, app.securePreferences, unused)
        if (!OemSurvivalHelper.shouldPromptSurvival(health, app.securePreferences)) return@LaunchedEffect
        opened = true
        OemSurvivalHelper.markPromptShown(app.securePreferences)
        onNeedConsent()
    }
}

suspend fun readUnusedAppRestrictions(context: Context): Int =
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
