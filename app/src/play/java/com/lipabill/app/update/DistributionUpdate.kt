package com.lipabill.app.update

import android.app.Activity
import android.widget.Toast
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.lipabill.app.R

internal object DistributionUpdate {

    private const val REQUEST = 8411

    fun check(activity: Activity, manual: Boolean) {
        val manager = AppUpdateManagerFactory.create(activity)
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (activity.isFinishing) return@addOnSuccessListener
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> manager.completeUpdate()
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        manager.startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                            REQUEST
                        )
                    }
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                        manager.startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            REQUEST
                        )
                    }
                    manual -> Toast.makeText(
                        activity,
                        activity.getString(R.string.update_latest),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener {
                if (!manual || activity.isFinishing) return@addOnFailureListener
                Toast.makeText(
                    activity,
                    activity.getString(R.string.update_check_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
