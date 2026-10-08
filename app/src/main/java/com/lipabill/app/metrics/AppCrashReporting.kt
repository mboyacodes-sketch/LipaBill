package com.lipabill.app.metrics

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lipabill.app.BuildConfig
import kotlin.coroutines.cancellation.CancellationException

/**
 * Firebase Crashlytics — crash / non-fatal reporting only.
 *
 * Custom keys are limited to build metadata. Do **not** record SMS bodies,
 * amounts, phones, PINs, receipt codes, or USSD dialog text here.
 *
 * No-ops when [BuildConfig.FIREBASE_CRASHLYTICS] is false (missing google-services.json).
 */
object AppCrashReporting {

    @Volatile
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context) {
        if (!BuildConfig.FIREBASE_CRASHLYTICS) return
        runCatching {
            val cx = FirebaseCrashlytics.getInstance()
            cx.setCrashlyticsCollectionEnabled(true)
            cx.setCustomKey("build_flavor", if (BuildConfig.SIDELOAD_DISTRIBUTION) "internal" else "play")
            cx.setCustomKey("app_version", BuildConfig.VERSION_NAME)
            cx.setCustomKey("version_code", BuildConfig.VERSION_CODE)
            // Never setUserId to a phone number or SIM id.
            crashlytics = cx
        }
    }

    /** Type and stack only. The original message can contain a phone or amount. */
    fun recordCaught(error: Throwable) {
        if (error is CancellationException) return
        val cx = crashlytics ?: return
        val safe = Exception(error.javaClass.simpleName.ifBlank { "Exception" })
        safe.stackTrace = error.stackTrace.take(16).toTypedArray()
        runCatching { cx.recordException(safe) }
    }
}
