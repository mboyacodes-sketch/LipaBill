package com.lipabill.app.security

import android.app.Activity
import android.app.Dialog
import android.view.Window
import android.view.WindowManager
import com.lipabill.app.BuildConfig

/**
 * Release-oriented UI / process hardening helpers.
 * Not a substitute for device encryption + lock screen.
 */
object AppSecurity {

    /**
     * Block screen capture / recents thumbnails of transaction UI.
     * Skipped on debug and sideload builds so QA / Play review videos can be recorded.
     * Still enforced on Play release.
     */
    fun lockScreenCapture(activity: Activity) {
        lockScreenCapture(activity.window)
    }

    fun lockScreenCapture(dialog: Dialog) {
        dialog.window?.let { lockScreenCapture(it) }
    }

    fun lockScreenCapture(window: Window) {
        if (BuildConfig.DEBUG || BuildConfig.SIDELOAD_DISTRIBUTION) return
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
}
