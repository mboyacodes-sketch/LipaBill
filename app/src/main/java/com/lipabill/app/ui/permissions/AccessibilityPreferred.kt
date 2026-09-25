package com.lipabill.app.ui.permissions

import android.content.Context
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.ussd.AccessibilityHelper

/**
 * Tracks whether the user wants LipaBill Accessibility on.
 *
 * Android disables Accessibility services on every package update / reinstall.
 * Apps cannot flip the system switch back on — we only remember intent and
 * prompt (or restore via adb during local installs).
 */
object AccessibilityPreferred {

    /** Call whenever we observe the live OS toggle. */
    fun syncFromSystem(context: Context, prefs: SecurePreferences): Boolean {
        val enabled = AccessibilityHelper.isLipaBillServiceEnabled(context)
        if (enabled) {
            prefs.accessibilityPreferredOn = true
        }
        return needsReenable(context, prefs)
    }

    fun needsReenable(context: Context, prefs: SecurePreferences): Boolean =
        prefs.accessibilityPreferredOn &&
            !AccessibilityHelper.isLipaBillServiceEnabled(context)

    /** User confirmed the Turn off coach — don't nag after they leave Settings. */
    fun markUserTurningOff(prefs: SecurePreferences) {
        prefs.accessibilityPreferredOn = false
    }

    /** User confirmed the Turn on / re-enable coach. */
    fun markUserTurningOn(prefs: SecurePreferences) {
        prefs.accessibilityPreferredOn = true
    }
}
