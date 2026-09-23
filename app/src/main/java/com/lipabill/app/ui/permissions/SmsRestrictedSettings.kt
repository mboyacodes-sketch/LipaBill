package com.lipabill.app.ui.permissions

import android.os.Build

/**
 * Android 15+ hard-restricts SMS for sideloaded apps until the user enables
 * "Allow restricted settings". We only guide that flow on Android 15–24.
 * Android 25+ keeps the pre-change permission / settings route.
 */
object SmsRestrictedSettings {
    private const val MIN_RELEASE = 15
    private const val MAX_RELEASE = 24
    /** Fallback when [Build.VERSION.RELEASE] is not a numeric major (OEM quirks). */
    private const val MIN_SDK = 35 // Android 15
    private const val MAX_SDK = 44 // Android 24 (release ≈ sdk − 20 for API 34+)

    fun appliesToThisDevice(): Boolean {
        val major = Build.VERSION.RELEASE
            .substringBefore('.')
            .toIntOrNull()
        if (major != null) return major in MIN_RELEASE..MAX_RELEASE
        return Build.VERSION.SDK_INT in MIN_SDK..MAX_SDK
    }
}
