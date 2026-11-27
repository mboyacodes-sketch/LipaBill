package com.lipabill.app.ui.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.lipabill.app.data.prefs.SecurePreferences

/**
 * Deep-links that keep Accessibility alive on aggressive OEMs (Samsung Device Care,
 * Tecno/Infinix/Xiaomi battery savers). Never flips Accessibility itself.
 */
object OemSurvivalHelper {

    /** 14 days between survival prompts unless health fails again sooner via payment gate. */
    const val PROMPT_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000

    fun isSamsung(): Boolean {
        val mfr = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        return mfr.contains("samsung", ignoreCase = true) ||
            brand.contains("samsung", ignoreCase = true)
    }

    fun isTranssion(): Boolean {
        val mfr = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val blob = "$mfr $brand"
        return listOf("tecno", "infinix", "itel", "transsion").any {
            blob.contains(it, ignoreCase = true)
        }
    }

    fun isXiaomiFamily(): Boolean {
        val mfr = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val blob = "$mfr $brand"
        return listOf("xiaomi", "redmi", "poco", "blackshark").any {
            blob.contains(it, ignoreCase = true)
        }
    }

    fun oemFamilyKey(): String = when {
        isSamsung() -> "samsung"
        isTranssion() -> "transsion"
        isXiaomiFamily() -> "xiaomi"
        else -> "other"
    }

    fun coachBody(): String = when {
        isSamsung() ->
            "Samsung can put LipaBill to sleep, which stops payments halfway. " +
                "Add LipaBill to Never sleeping apps (or turn off Sleeping apps), " +
                "and allow unrestricted battery use."
        isTranssion() ->
            "This phone’s battery saver can stop LipaBill in the background. " +
                "Allow Auto-start and turn off battery restriction for LipaBill."
        isXiaomiFamily() ->
            "MIUI can restrict LipaBill in the background. " +
                "Allow Autostart and set Battery saver to No restrictions."
        else ->
            "Some phones pause LipaBill in the background, which stops payments halfway. " +
                "Allow unrestricted battery use, then return here."
    }

    fun shouldPromptSurvival(
        health: AccessibilityHealth,
        prefs: SecurePreferences,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!health.preferred) return false
        // Grant on but service unbound — always coach (payment / Device Care sleep).
        if (health.secureOn && !health.bound) return true
        // After first enable: once (or after cooldown) if battery is still restricted.
        if (health.batteryIgnoring) return false
        if (!prefs.oemSurvivalPromptShown) return true
        val last = prefs.oemSurvivalLastPromptAt
        return last > 0L && nowMs - last >= PROMPT_COOLDOWN_MS
    }

    fun markPromptShown(prefs: SecurePreferences, nowMs: Long = System.currentTimeMillis()) {
        prefs.oemSurvivalLastPromptAt = nowMs
        prefs.oemSurvivalPromptShown = true
    }

    /**
     * Opens the best OEM surface: battery exemption request, Device Care sleep list,
     * or app details. Returns true if an activity started.
     */
    fun openSurvivalSettings(context: Context): Boolean {
        if (requestIgnoreBatteryOptimizations(context)) return true
        if (isSamsung() && openSamsungDeviceCare(context)) return true
        if (isTranssion() && openTranssionBattery(context)) return true
        if (isXiaomiFamily() && openXiaomiAutostart(context)) return true
        return openAppDetails(context)
    }

    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return false
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return safeStart(context, intent)
    }

    fun openBatteryOptimizationList(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return safeStart(context, intent)
    }

    private fun openSamsungDeviceCare(context: Context): Boolean {
        // Never sleeping apps / sleeping apps — OEM activity names vary by One UI.
        val candidates = listOf(
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.battery.ui.BatteryActivity"
            ),
            ComponentName(
                "com.samsung.android.sm",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            ),
            ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        )
        for (component in candidates) {
            val intent = Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (safeStart(context, intent)) return true
        }
        // Search “sleeping” in Settings as a softer landing.
        val search = Intent("com.android.settings.action.SETTINGS_SEARCH")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setPackage("com.android.settings.intelligence")
            .putExtra("query", "Never sleeping apps")
        if (safeStart(context, search)) return true
        return openAppDetails(context)
    }

    private fun openTranssionBattery(context: Context): Boolean {
        val candidates = listOf(
            ComponentName(
                "com.transsion.phonemanager",
                "com.transsion.phonemanager.appcompat.AutoStartAppManagerActivity"
            ),
            ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
        )
        for (component in candidates) {
            val intent = Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (safeStart(context, intent)) return true
        }
        return false
    }

    private fun openXiaomiAutostart(context: Context): Boolean {
        val candidates = listOf(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            ),
            ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            )
        )
        for (component in candidates) {
            val intent = Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (safeStart(context, intent)) return true
        }
        return false
    }

    fun openAppDetails(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return safeStart(context, intent)
    }

    private fun safeStart(context: Context, intent: Intent): Boolean =
        try {
            if (intent.resolveActivity(context.packageManager) == null) {
                false
            } else {
                context.startActivity(intent)
                true
            }
        } catch (_: Exception) {
            false
        }
}
