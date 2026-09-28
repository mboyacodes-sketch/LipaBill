package com.lipabill.app.ui.permissions

import android.content.Context
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.ussd.AccessibilityHelper

/**
 * Tracks whether the user wants LipaBill Accessibility on.
 *
 * On some devices / update paths the OS Accessibility toggle ends up off.
 * Apps cannot flip it back — we remember intent and coach on release builds.
 * Debug installs should restore the toggle with scripts/install-play-debug.sh
 * instead of nagging on every push.
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
