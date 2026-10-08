package com.lipabill.app.metrics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.lipabill.app.BuildConfig
import com.lipabill.app.ussd.RepeatOutcome

/** Closed values for [AppMetrics.permissionResult]. */
enum class PermissionKind(val key: String) {
    Sms("sms"),
    Contacts("contacts"),
    Camera("camera"),
    Notifications("notifications"),
    Phone("phone")
}

/** Closed values for [AppMetrics.setupBlocked]. */
enum class SetupBlocker(val key: String) {
    NoSms("no_sms"),
    NoAccessibility("no_accessibility"),
    NoSim("no_sim"),
    NoSafaricom("no_safaricom"),
    NotificationsOff("notifications_off")
}

/** Closed values for [AppMetrics.ticketImport]. */
enum class TicketImportResult(val key: String) {
    Saved("saved"),
    Duplicate("duplicate"),
    Unreadable("unreadable")
}

/**
 * Product metrics only. Params are the enums above — never SMS text, amounts,
 * phone numbers, PINs, receipt codes, names, or ticket barcodes.
 *
 * No-ops when [BuildConfig.FIREBASE_ANALYTICS] is false.
 */
object AppMetrics {

    @Volatile
    private var analytics: FirebaseAnalytics? = null

    @Volatile
    private var activePaymentFlow: String? = null

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

    fun challengeStarted(templateId: String) =
        log("challenge_started", bundleOf("template" to templateId.take(32)))

    fun challengeCompleted(templateId: String) =
        log("challenge_completed", bundleOf("template" to templateId.take(32)))

    fun checkInCompleted() = log("checkin_completed")

    fun smsPermission(granted: Boolean) {
        permissionResult(PermissionKind.Sms, granted)
        if (!granted) setupBlocked(SetupBlocker.NoSms)
    }

    fun contactsPermission(granted: Boolean) =
        permissionResult(PermissionKind.Contacts, granted)

    fun cameraPermission(granted: Boolean) =
        permissionResult(PermissionKind.Camera, granted)

    fun phonePermission(granted: Boolean) =
        permissionResult(PermissionKind.Phone, granted)

    fun notificationPermission(granted: Boolean) {
        permissionResult(PermissionKind.Notifications, granted)
        if (!granted) setupBlocked(SetupBlocker.NotificationsOff)
    }

    /** flow is send, pay, or repeat. */
    fun paymentStarted(flow: String) {
        val safe = flow.take(16)
        activePaymentFlow = safe
        log("payment_started", bundleOf("flow" to safe))
    }

    fun paymentFinished(outcome: RepeatOutcome) {
        val result = paymentFinishResult(outcome) ?: return
        log(
            "payment_finished",
            bundleOf(
                "flow" to (activePaymentFlow ?: "unknown"),
                "result" to result
            )
        )
    }

    fun setupBlocked(blocker: SetupBlocker) =
        log("setup_blocked", bundleOf("blocker" to blocker.key))

    fun permissionResult(kind: PermissionKind, granted: Boolean) =
        log("permission_result", bundleOf("kind" to kind.key, "granted" to granted))

    /** [source] is pkpass, pdf, share, or camera. */
    fun ticketImport(source: String, result: TicketImportResult, error: Throwable? = null) {
        log("ticket_import", bundleOf("source" to source.take(16), "result" to result.key))
        if (result == TicketImportResult.Unreadable && error != null) {
            AppCrashReporting.recordCaught(error)
        }
    }

    fun ticketImportFailure(source: String, error: Throwable) {
        val duplicate = error is IllegalArgumentException &&
            error.message.orEmpty().let { it.contains("already saved") || it.contains("already used") }
        ticketImport(
            source,
            if (duplicate) TicketImportResult.Duplicate else TicketImportResult.Unreadable,
            error
        )
    }

    /** Manual scans pass [includeUnchanged]. Background scans report new rows only. */
    fun ledgerRefresh(inserted: Int, includeUnchanged: Boolean = false) {
        if (inserted <= 0 && !includeUnchanged) return
        log("ledger_refresh", bundleOf("result" to ledgerRefreshResult(inserted)))
    }

    fun ledgerRefreshFailed(error: Throwable? = null) {
        log("ledger_refresh", bundleOf("result" to "error"))
        if (error != null && error !is SecurityException) AppCrashReporting.recordCaught(error)
    }

    fun paymentAborted(flow: String, reason: String) =
        log(
            "payment_aborted",
            bundleOf(
                "flow" to flow.take(16),
                "reason" to reason.take(32)
            )
        )

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

internal fun paymentFinishResult(outcome: RepeatOutcome): String? = when (outcome) {
    RepeatOutcome.COMPLETED_TO_PIN -> "completed"
    RepeatOutcome.USER_CANCELLED -> "cancelled"
    RepeatOutcome.ABORTED_MISMATCH -> "wrong_menu"
    RepeatOutcome.ABORTED_ERROR, RepeatOutcome.AUTH_FAILED -> "failed"
    RepeatOutcome.MANUAL_COPY -> null
}

internal fun ledgerRefreshResult(inserted: Int): String =
    if (inserted > 0) "added" else "unchanged"
