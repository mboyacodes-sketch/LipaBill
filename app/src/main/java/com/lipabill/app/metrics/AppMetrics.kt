package com.lipabill.app.metrics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.lipabill.app.BuildConfig
import com.lipabill.app.ussd.RepeatOutcome

/** Closed values for [AppMetrics.permissionResult]. */
enum class PermissionKind(val key: String) {
    Sms("sms"),
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
            fa.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
                )
            )
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
        AppCrashReporting.breadcrumb("screen:${name.take(24)}")
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
        AppCrashReporting.setKey("payment_flow", safe)
        AppCrashReporting.setKey("payment_result", "started")
        AppCrashReporting.breadcrumb("pay:start:$safe")
        log("payment_started", bundleOf("flow" to safe))
    }

    /**
     * @param reason closed code only — never dialog text / amounts / phones.
     * Examples: pin_injected, accessibility_off, other_a11y, call_permission,
     * dial_failed, inject_failed, menu_mismatch, service_interrupted,
     * driver_error, user_cancelled, confirm_no, finished_notice.
     */
    fun paymentFinished(outcome: RepeatOutcome, reason: String? = null) {
        val result = paymentFinishResult(outcome) ?: return
        val safeReason = reason?.let { paymentReasonCode(it) }
        AppCrashReporting.setKey("payment_result", result)
        if (safeReason != null) AppCrashReporting.setKey("payment_reason", safeReason)
        AppCrashReporting.breadcrumb("pay:finish:$result:${safeReason ?: "none"}")
        log(
            "payment_finished",
            bundleOf(
                "flow" to (activePaymentFlow ?: "unknown"),
                "result" to result,
                "reason" to safeReason
            )
        )
        activePaymentFlow = null
    }

    fun setupBlocked(blocker: SetupBlocker) {
        AppCrashReporting.breadcrumb("setup:${blocker.key}")
        log("setup_blocked", bundleOf("blocker" to blocker.key))
    }

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
        AppCrashReporting.breadcrumb("ledger:error")
        log("ledger_refresh", bundleOf("result" to "error"))
        if (error != null && error !is SecurityException) AppCrashReporting.recordCaught(error)
    }

    fun paymentAborted(flow: String, reason: String) {
        val safeFlow = flow.take(16)
        val safeReason = paymentReasonCode(reason)
        AppCrashReporting.setKey("payment_flow", safeFlow)
        AppCrashReporting.setKey("payment_reason", safeReason)
        AppCrashReporting.breadcrumb("pay:abort:$safeReason")
        log(
            "payment_aborted",
            bundleOf(
                "flow" to safeFlow,
                "reason" to safeReason
            )
        )
    }

    /** result: success, cancel, error, no_lock. */
    fun authResult(result: String) {
        val safe = paymentReasonCode(result)
        AppCrashReporting.breadcrumb("auth:$safe")
        log("auth_result", bundleOf("result" to safe))
    }

    /** result: matched, parse_error, error. Never address/body. */
    fun smsIngest(result: String) {
        val safe = paymentReasonCode(result)
        log("sms_ingest", bundleOf("result" to safe))
        if (result == "error") AppCrashReporting.breadcrumb("sms:error")
    }

    /** kind: challenge / checkin / pass_reminder. result: ok / error. */
    fun engagementWorker(kind: String, result: String) {
        log(
            "engagement_worker",
            bundleOf(
                "kind" to kind.take(16),
                "result" to paymentReasonCode(result)
            )
        )
    }

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

/** Collapse free text to a closed analytics token. Unknown → other. */
internal fun paymentReasonCode(raw: String): String {
    val key = raw.trim().lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .take(32)
    return when (key) {
        "pin_injected",
        "finished_notice",
        "accessibility_off",
        "other_accessibility_service",
        "other_a11y",
        "call_permission",
        "call_phone_denied",
        "dial_failed",
        "inject_failed",
        "menu_mismatch",
        "service_interrupted",
        "driver_error",
        "user_cancelled",
        "confirm_no",
        "feature_disabled",
        "prepare_failed",
        "success",
        "cancel",
        "error",
        "no_lock",
        "matched",
        "parse_error",
        "ok",
        "aborted" -> when (key) {
            "other_accessibility_service" -> "other_a11y"
            "call_phone_denied" -> "call_permission"
            else -> key
        }
        else -> when {
            key.contains("call_phone") -> "call_permission"
            key.contains("other_accessibility") -> "other_a11y"
            key.contains("could_not_type") || key.contains("inject") -> "inject_failed"
            else -> "other"
        }
    }
}
