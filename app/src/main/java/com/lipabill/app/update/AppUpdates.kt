package com.lipabill.app.update

import android.app.Activity
import android.widget.Toast
import com.lipabill.app.BuildConfig
import com.lipabill.app.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offers an update from the channel that installed this build.
 *
 * Play builds use Play in-app updates. Internal builds use Firebase App
 * Distribution. Debug installs are left alone, because neither channel can
 * replace an app that was pushed from the computer.
 */
object AppUpdates {

    private val automaticChecked = AtomicBoolean(false)

    fun check(activity: Activity, manual: Boolean) {
        if (activity.isFinishing) return
        if (BuildConfig.DEBUG) {
            if (manual) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.update_debug_unavailable),
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }
        if (!manual && !automaticChecked.compareAndSet(false, true)) return
        DistributionUpdate.check(activity, manual)
    }
}
