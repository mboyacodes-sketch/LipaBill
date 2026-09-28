package com.lipabill.app.device

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * LipaBill runs on phones and tablets. TVs, XR headsets, watches, cars,
 * Chrome OS, and other non-handheld devices are refused even if the APK
 * is sideloaded. Play filtering uses the matching uses-feature declarations.
 */
object HandheldDeviceGate {

    private val blockedFeatures = setOf(
        PackageManager.FEATURE_LEANBACK,
        "android.software.leanback_only",
        "android.hardware.type.television",
        PackageManager.FEATURE_WATCH,
        PackageManager.FEATURE_AUTOMOTIVE,
        "android.hardware.type.pc",
        PackageManager.FEATURE_EMBEDDED,
        "android.hardware.type.xr",
        "android.software.xr.api.spatial",
        "android.hardware.vr.headtracking"
    )

    private val blockedUiModes = setOf(
        Configuration.UI_MODE_TYPE_TELEVISION,
        Configuration.UI_MODE_TYPE_WATCH,
        Configuration.UI_MODE_TYPE_CAR,
        Configuration.UI_MODE_TYPE_APPLIANCE,
        Configuration.UI_MODE_TYPE_VR_HEADSET
    )

    fun isPhoneOrTablet(context: Context): Boolean {
        val pm = context.packageManager
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return allows(
            hasTouchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
            uiModeType = uiMode,
            presentFeatures = blockedFeatures.filter { pm.hasSystemFeature(it) }.toSet()
        )
    }

    internal fun allows(
        hasTouchscreen: Boolean,
        uiModeType: Int,
        presentFeatures: Set<String>
    ): Boolean {
        if (!hasTouchscreen) return false
        if (uiModeType in blockedUiModes) return false
        if (presentFeatures.any { it in blockedFeatures }) return false
        return true
    }
}
