package com.lipabill.app.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

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
