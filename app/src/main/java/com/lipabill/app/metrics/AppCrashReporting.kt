package com.lipabill.app.metrics

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lipabill.app.BuildConfig

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

    /** Safe breadcrumb — short code only, no PII. */
    fun breadcrumb(message: String) {
        val cx = crashlytics ?: return
        runCatching { cx.log(message.take(100)) }
    }

    fun setKey(name: String, value: String) {
        val cx = crashlytics ?: return
        runCatching { cx.setCustomKey(name.take(40), value.take(64)) }
    }

    fun recordNonFatal(t: Throwable, message: String? = null) {
        val cx = crashlytics ?: return
        runCatching {
            message?.take(100)?.let { cx.log(it) }
            cx.recordException(t)
        }
    }
}
