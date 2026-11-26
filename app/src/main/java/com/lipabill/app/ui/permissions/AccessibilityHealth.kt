package com.lipabill.app.ui.permissions

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.UssdAccessibilityService
import kotlinx.coroutines.delay

/**
 * Live Accessibility health for OEM rollback diagnosis.
 *
 * [secureOn] = listed in Settings.Secure; [bound] = [UssdAccessibilityService] connected.
 * Samsung Device Care / low-RAM often leave the grant on while unbinding the service.
 */
data class AccessibilityHealth(
    val preferred: Boolean,
    val secureOn: Boolean,
    val bound: Boolean,
    val batteryIgnoring: Boolean,
    val unusedAppStatus: String,
    val manufacturer: String,
    val model: String,
    val sdk: Int
) {
    /** Ready to drive USSD without coaching. */
    val paymentReady: Boolean get() = bound

    val coachKind: A11yCoachKind
        get() = when {
            bound -> A11yCoachKind.None
            secureOn && preferred -> A11yCoachKind.Unbound
            // Secure on but user never preferred — still unbound for payment gates.
            secureOn -> A11yCoachKind.Unbound
            preferred -> A11yCoachKind.GrantCleared
            else -> A11yCoachKind.None
        }
}

enum class A11yCoachKind {
    /** No coach needed. */
    None,
    /** Preferred (or required for pay) but Secure grant cleared — reopen Accessibility toggle. */
    GrantCleared,
    /** Grant still listed, service not bound — Device Care / battery / hibernation. */
    Unbound
}

object AccessibilityHealthProbe {

    private const val BIND_WAIT_MS = 1_200L
    private const val BIND_POLL_MS = 100L

    fun snapshot(
        context: Context,
        prefs: SecurePreferences,
        unusedAppStatus: UnusedAppRestriction = UnusedAppRestriction.Unknown
    ): AccessibilityHealth {
        val appCtx = context.applicationContext
        return AccessibilityHealth(
            preferred = prefs.accessibilityPreferredOn,
            secureOn = AccessibilityHelper.isLipaBillListedInSecureSettings(appCtx),
            bound = UssdAccessibilityService.isConnected(),
            batteryIgnoring = isIgnoringBatteryOptimizations(appCtx),
            unusedAppStatus = unusedAppStatus.name.lowercase(),
            manufacturer = Build.MANUFACTURER.orEmpty().take(24),
            model = Build.MODEL.orEmpty().take(24),
            sdk = Build.VERSION.SDK_INT
        )
    }

    /**
     * Live health for a coach decision, logged as `a11y_health` with [trigger]. With
     * [awaitBind], a service still listed in Secure settings gets a short window to rebind
     * before it counts as unbound.
     */
    suspend fun checkAndLog(
        context: Context,
        prefs: SecurePreferences,
        trigger: String,
        awaitBind: Boolean
    ): AccessibilityHealth {
        val unused = unusedAppRestrictionOf(readUnusedAppRestrictions(context))
        var health = snapshot(context, prefs, unused)
        if (awaitBind && !health.bound && health.secureOn) {
            awaitBindIfListed(context)
            health = snapshot(context, prefs, unused)
        }
        AppMetrics.a11yHealth(
            preferred = health.preferred,
            secureOn = health.secureOn,
            bound = health.bound,
            batteryIgnoring = health.batteryIgnoring,
            unusedAppStatus = health.unusedAppStatus,
            manufacturer = health.manufacturer,
            model = health.model,
            sdk = health.sdk,
            oemFamily = OemSurvivalHelper.oemFamilyKey(),
            coach = health.coachKind.name.lowercase(),
            trigger = trigger
        )
        return health
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return runCatching { pm.isIgnoringBatteryOptimizations(context.packageName) }
            .getOrDefault(false)
    }

    /**
     * When Secure still lists LipaBill but the process was killed, wait briefly for rebind
     * before opening a survival coach (OEM AccessibilityManager lag).
     */
    suspend fun awaitBindIfListed(
        context: Context,
        timeoutMs: Long = BIND_WAIT_MS
    ): Boolean {
        if (UssdAccessibilityService.isConnected()) return true
        if (!AccessibilityHelper.isLipaBillListedInSecureSettings(context) &&
            !AccessibilityHelper.isLipaBillServiceEnabled(context)
        ) {
            return false
        }
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (UssdAccessibilityService.isConnected()) return true
            delay(BIND_POLL_MS)
        }
        return UssdAccessibilityService.isConnected()
    }

    /** Consent / Settings page for [kind]. */
    fun consentPageFor(kind: A11yCoachKind): FirstRunPage = when (kind) {
        A11yCoachKind.None -> FirstRunPage.Accessibility
        A11yCoachKind.GrantCleared -> FirstRunPage.Accessibility
        A11yCoachKind.Unbound -> FirstRunPage.OemSurvival
    }

    fun resumeNoteFor(kind: A11yCoachKind): String? = when (kind) {
        A11yCoachKind.GrantCleared ->
            "LipaBill’s Accessibility switch was turned off. Turn it on again to Send and Pay."
        A11yCoachKind.Unbound ->
            "Payments need LipaBill awake on this phone. Allow background / never sleeping, then try again."
        A11yCoachKind.None -> null
    }
}
