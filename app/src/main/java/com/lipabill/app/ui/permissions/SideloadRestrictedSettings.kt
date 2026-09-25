package com.lipabill.app.ui.permissions

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.lipabill.app.ussd.AccessibilityHelper

/**
 * Firebase App Distribution / APK installs are not Play Store installs.
 * On Android 13+, Accessibility (needed for USSD dial) is blocked until the user
 * allows restricted settings on App info. SMS is similarly gated on Android 15+.
 * Both share the same App info unlock — defer requesting them until after unlock.
 */
object SideloadRestrictedSettings {

    private val PLAY_INSTALLERS = setOf(
        "com.android.vending",
        "com.google.android.feedback"
    )

    /** App op flipped when the user enables Allow restricted settings (API 34+). */
    private const val OPSTR_ACCESS_RESTRICTED_SETTINGS =
        "android:access_restricted_settings"

    fun isSideloaded(context: Context): Boolean {
        val installer = installerPackage(context) ?: return true
        return installer !in PLAY_INSTALLERS
    }

    /** Accessibility toggle is restricted for non-Play installs from Android 13. */
    fun accessibilityUnlockNeeded(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && isSideloaded(context)

    /**
     * Sideload installs that need "Allow restricted settings" before SMS and/or
     * Accessibility can be granted. Defer both until after the unlock step.
     */
    fun needsRestrictedSettingsFlow(context: Context): Boolean =
        accessibilityUnlockNeeded(context) ||
            (isSideloaded(context) && SmsRestrictedSettings.appliesToThisDevice())

    /**
     * True when App info → Allow restricted settings is already on.
     * Uses AppOps when available; on API 33 falls back to Accessibility enabled.
     */
    fun isRestrictedSettingsAllowed(context: Context): Boolean {
        if (!needsRestrictedSettingsFlow(context)) return true
        if (Build.VERSION.SDK_INT < 34) {
            return AccessibilityHelper.isLipaBillServiceEnabled(context)
        }
        return try {
            val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
            val mode = appOps.unsafeCheckOpNoThrow(
                OPSTR_ACCESS_RESTRICTED_SETTINGS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Show the "Allow restricted settings" CTA only while unlock is still required.
     */
    fun shouldShowUnlockButton(context: Context): Boolean =
        needsRestrictedSettingsFlow(context) && !isRestrictedSettingsAllowed(context)

    fun openAppInfo(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun installerPackage(context: Context): String? {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        } catch (_: Exception) {
            null
        }
    }
}
