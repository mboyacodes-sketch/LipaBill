package com.lipabill.app.metrics

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.lipabill.app.BuildConfig
import kotlin.coroutines.cancellation.CancellationException

/**
 * Firebase Crashlytics — crash / non-fatal reporting only.
 *
 * Custom keys and breadcrumbs are closed tokens only. Do **not** record SMS
 * bodies, amounts, phones, PINs, receipt codes, or USSD dialog text here.
 *
 * No-ops when [BuildConfig.FIREBASE_CRASHLYTICS] is false (missing google-services.json).
 */
object AppCrashReporting {

    @Volatile
    private var crashlytics: FirebaseCrashlytics? = null

    fun init(context: Context) {
        if (BuildConfig.FIREBASE_CRASHLYTICS) {
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
        installSafeUncaughtHandler()
    }

    /**
     * Crashlytics otherwise uploads the original exception message, which can
     * contain a phone number or an amount. Scrub messages to type names only,
     * but keep stacks and the cause chain so launch failures stay diagnosable.
     */
    private fun installSafeUncaughtHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val safe = scrubThrowableForReporting(error)
            if (previous != null) {
                previous.uncaughtException(thread, safe)
            } else {
                crashlytics?.let { runCatching { it.recordException(safe) } }
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }

    /** Fixed tokens only — e.g. `pay:start:send`, `ussd:mismatch`. */
    fun breadcrumb(token: String) {
        val safe = sanitizeToken(token) ?: return
        runCatching { crashlytics?.log(safe) }
    }

    /** Closed values only (flow / result / reason codes). */
    fun setKey(name: String, value: String) {
        val key = sanitizeToken(name, max = 24) ?: return
        val v = sanitizeToken(value, max = 36) ?: return
        runCatching { crashlytics?.setCustomKey(key, v) }
    }

    /** Type names + stacks only. Original messages can contain a phone or amount. */
    fun recordCaught(error: Throwable) {
        if (error is CancellationException) return
        val cx = crashlytics ?: return
        runCatching { cx.recordException(scrubThrowableForReporting(error)) }
    }
}

/**
 * Rebuilds [error] with messages replaced by the exception class name.
 * Preserves full stacks and causes (Play was only showing ActivityThread
 * frames when the cause chain was dropped).
 */
internal fun scrubThrowableForReporting(error: Throwable, depth: Int = 0): Exception {
    val safe = Exception(error.javaClass.name.ifBlank { "java.lang.Exception" })
    safe.stackTrace = error.stackTrace
    if (depth >= 8) return safe
    val cause = error.cause
    if (cause != null && cause !== error) {
        runCatching { safe.initCause(scrubThrowableForReporting(cause, depth + 1)) }
    }
    return safe
}

/**
 * Letters and `:_-.` only. Digits are stripped so a leaked phone/amount cannot
 * ride along in a breadcrumb or custom key.
 */
internal fun sanitizeToken(raw: String, max: Int = 64): String? {
    val cleaned = raw.trim()
        .map { ch ->
            when {
                ch.isLetter() || ch in ":_-." -> ch
                else -> '_'
            }
        }
        .joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')
        .take(max)
    return cleaned.takeIf { it.isNotEmpty() }
}
