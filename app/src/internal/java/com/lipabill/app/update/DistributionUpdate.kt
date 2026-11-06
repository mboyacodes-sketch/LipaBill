package com.lipabill.app.update

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lipabill.app.BuildConfig
import com.lipabill.app.R
import com.lipabill.app.metrics.AppCrashReporting
import com.lipabill.app.metrics.AppMetrics
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Self-hosted OTA for sideload / Firebase-distributed installs.
 * Fetches [BuildConfig.OTA_MANIFEST_URL], downloads the APK, verifies SHA-256,
 * then opens the system package installer.
 *
 * Play builds never use this path — they stay on Play in-app updates.
 */
internal object DistributionUpdate {

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val downloading = AtomicBoolean(false)

    fun check(activity: Activity, manual: Boolean) {
        val manifestUrl = BuildConfig.OTA_MANIFEST_URL
        if (manifestUrl.isBlank()) {
            if (manual) toast(activity, R.string.update_check_failed)
            return
        }
        io.execute {
            try {
                val manifest = OtaDownloader.fetchManifest(manifestUrl)
                main.post {
                    if (activity.isFinishing) return@post
                    onManifest(activity, manifest, manual)
                }
            } catch (t: Throwable) {
                AppCrashReporting.breadcrumb("ota:manifest_fail")
                if (manual) {
                    main.post {
                        if (!activity.isFinishing) {
                            toast(activity, R.string.update_check_failed)
                        }
                    }
                }
            }
        }
    }

    private fun onManifest(activity: Activity, manifest: OtaManifest, manual: Boolean) {
        if (!manifest.isNewerThan(BuildConfig.VERSION_CODE)) {
            AppMetrics.log("ota_check", android.os.Bundle().apply {
                putString("result", "latest")
            })
            if (manual) toast(activity, R.string.update_latest)
            return
        }
        AppMetrics.log("ota_check", android.os.Bundle().apply {
            putString("result", "available")
            putLong("to_code", manifest.versionCode.toLong())
        })
        AppCrashReporting.setKey("ota_available", manifest.versionCode.toString())
        showOffer(activity, manifest, manual)
    }

    private fun showOffer(activity: Activity, manifest: OtaManifest, manual: Boolean) {
        val message = buildString {
            append(
                activity.getString(
                    R.string.update_available_body,
                    manifest.versionName,
                    manifest.versionCode
                )
            )
            if (manifest.notes.isNotBlank()) {
                append("\n\n")
                append(manifest.notes)
            }
        }
        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(message)
            .setCancelable(!manifest.mandatory)
            .setPositiveButton(R.string.update_download) { _, _ ->
                startDownload(activity, manifest)
            }
        if (!manifest.mandatory) {
            builder.setNegativeButton(R.string.update_later, null)
            if (!manual) {
                // Auto-check: only prompt once per process unless mandatory.
            }
        }
        builder.show()
    }

    private fun startDownload(activity: Activity, manifest: OtaManifest) {
        if (!OtaInstaller.canRequestPackageInstalls(activity)) {
            toast(activity, R.string.update_allow_installs)
            OtaInstaller.openUnknownAppSources(activity)
            return
        }
        if (!downloading.compareAndSet(false, true)) {
            toast(activity, R.string.update_download_in_progress)
            return
        }
        toast(activity, R.string.update_downloading)
        AppCrashReporting.breadcrumb("ota:download")
        io.execute {
            try {
                val apk = OtaDownloader.downloadApk(activity.applicationContext, manifest)
                main.post {
                    downloading.set(false)
                    if (activity.isFinishing) return@post
                    AppMetrics.log("ota_install", android.os.Bundle().apply {
                        putString("result", "prompt")
                        putLong("to_code", manifest.versionCode.toLong())
                    })
                    runCatching { OtaInstaller.promptInstall(activity, apk) }
                        .onFailure { error ->
                            AppCrashReporting.recordCaught(error)
                            val msg = when (error.message) {
                                "apk_signer_mismatch",
                                "apk_package_mismatch",
                                "apk_no_installed_signer",
                                "apk_unreadable" -> R.string.update_untrusted_apk
                                else -> R.string.update_install_failed
                            }
                            toast(activity, msg)
                        }
                }
            } catch (t: Throwable) {
                downloading.set(false)
                AppCrashReporting.breadcrumb("ota:download_fail")
                AppCrashReporting.recordCaught(t)
                main.post {
                    if (!activity.isFinishing) toast(activity, R.string.update_download_failed)
                }
            }
        }
    }

    private fun toast(activity: Activity, res: Int) {
        Toast.makeText(activity, activity.getString(res), Toast.LENGTH_LONG).show()
    }
}
