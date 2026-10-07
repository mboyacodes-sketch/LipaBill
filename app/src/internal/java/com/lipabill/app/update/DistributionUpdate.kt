package com.lipabill.app.update

import android.app.Activity
import android.widget.Toast
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.FirebaseAppDistributionException
import com.lipabill.app.BuildConfig
import com.lipabill.app.R

internal object DistributionUpdate {

    fun check(activity: Activity, manual: Boolean) {
        if (!BuildConfig.FIREBASE_ANALYTICS) {
            if (manual && !activity.isFinishing) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.update_check_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }
        FirebaseAppDistribution.getInstance()
            .updateIfNewReleaseAvailable()
            .addOnFailureListener { error ->
                if (!manual || activity.isFinishing) return@addOnFailureListener
                val status = (error as? FirebaseAppDistributionException)?.errorCode
                if (status == null || status == FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE ||
                    status == FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED ||
                    status == FirebaseAppDistributionException.Status.INSTALLATION_CANCELED ||
                    status == FirebaseAppDistributionException.Status.NOT_IMPLEMENTED
                ) {
                    if (status == FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.update_latest),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@addOnFailureListener
                }
                Toast.makeText(
                    activity,
                    activity.getString(R.string.update_check_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
}
