package com.lipabill.app.metrics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.lipabill.app.BuildConfig

/**
 * Thin Firebase Analytics wrapper — **product metrics only**.
 *
 * Never log: SMS bodies, amounts, phone numbers, PINs, receipt codes,
 * counterparties, ticket barcodes, or raw USSD dialog text.
 *
 * No-ops when [BuildConfig.FIREBASE_ANALYTICS] is false (missing google-services.json).
 */
object AppMetrics {

    @Volatile
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (!BuildConfig.FIREBASE_ANALYTICS) return
        runCatching {
            val fa = FirebaseAnalytics.getInstance(context.applicationContext)
            fa.setAnalyticsCollectionEnabled(true)
            fa.setSessionTimeoutDuration(30 * 60 * 1000L)
            analytics = fa
            setUserProperty("build_flavor", if (BuildConfig.SIDELOAD_DISTRIBUTION) "internal" else "play")
            setUserProperty("app_version", BuildConfig.VERSION_NAME)
            log("app_metrics_ready")
        }
    }

    fun screen(name: String) {
        log(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            bundleOf(
                FirebaseAnalytics.Param.SCREEN_NAME to name.take(36),
                FirebaseAnalytics.Param.SCREEN_CLASS to name.take(36)
            )
        )
    }

    fun firstRunCompleted() = log("first_run_completed")

    fun smsPermission(granted: Boolean) =
        log("sms_permission", bundleOf("granted" to granted))

    fun accessibilityEnabled(enabled: Boolean) =
        log("accessibility_enabled", bundleOf("enabled" to enabled))

    /** flow: send | pay | repeat — no amounts or recipients. */
    fun paymentStarted(flow: String) =
        log("payment_started", bundleOf("flow" to flow.take(16)))

    fun paymentReachedPin(flow: String) =
        log("payment_reached_pin", bundleOf("flow" to flow.take(16)))

    fun paymentAborted(flow: String, reason: String) =
        log(
            "payment_aborted",
            bundleOf(
                "flow" to flow.take(16),
                "reason" to reason.take(32)
            )
        )

    fun ticketImport(source: String) =
        log("ticket_import", bundleOf("source" to source.take(16)))

    fun log(event: String, params: Bundle = Bundle()) {
        val fa = analytics ?: return
        runCatching { fa.logEvent(event.take(40), params) }
    }

    private fun setUserProperty(name: String, value: String) {
        val fa = analytics ?: return
        runCatching { fa.setUserProperty(name.take(24), value.take(36)) }
    }

    private fun bundleOf(vararg pairs: Pair<String, Any?>): Bundle =
        Bundle().apply {
            pairs.forEach { (k, v) ->
                when (v) {
                    null -> Unit
                    is String -> putString(k, v)
                    is Boolean -> putString(k, v.toString())
                    is Int -> putLong(k, v.toLong())
                    is Long -> putLong(k, v)
                    is Double -> putDouble(k, v)
                    else -> putString(k, v.toString())
                }
            }
        }
}
