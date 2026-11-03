package com.lipabill.app.ui.navigation

import android.net.Uri

sealed class Route(val path: String) {
    data object List : Route("list")
    data object Profile : Route("profile")
    data object Settings : Route("settings")
    data object FirstRunSetup : Route("first_run_setup")
    /** Later SMS / Phone / Accessibility consent — same pages as first-run. */
    data object Consent : Route("consent/{page}") {
        const val pattern = "consent/{page}"
        fun of(page: String) = "consent/$page"
        const val SMS = "sms"
        const val PHONE = "phone"
        const val ACCESSIBILITY = "accessibility"
    }
    data object Metrics : Route("metrics")
    data object Tickets : Route("tickets")
    data object Habits : Route("habits")
    data class TicketDetail(val id: Long) : Route("ticket/$id") {
        companion object {
            const val pattern = "ticket/{id}"
        }
    }
    data class RepeatConfirm(val id: Long, val amount: String = "") : Route(
        if (amount.isBlank()) "repeat/$id"
        else "repeat/$id?amount=${Uri.encode(amount)}"
    ) {
        companion object {
            const val pattern = "repeat/{id}?amount={amount}"
        }
    }
    data class RepeatManual(val id: Long, val amount: String = "") : Route(
        if (amount.isBlank()) "repeat_manual/$id"
        else "repeat_manual/$id?amount=${Uri.encode(amount)}"
    ) {
        companion object {
            const val pattern = "repeat_manual/{id}?amount={amount}"
        }
    }
    data class Processing(val auditId: Long) : Route("processing/$auditId") {
        companion object {
            const val pattern = "processing/{auditId}"
        }
    }

    /**
     * Debug / promo only. Mirrors USSD steps → confirm → PIN without dialing.
     * Query carries display labels; the pending receipt holds the payment.
     */
    data class DemoPayment(
        val auditId: Long,
        val amountLabel: String,
        val detail: String
    ) : Route(
        "demo_payment/$auditId?amount=${Uri.encode(amountLabel)}&detail=${Uri.encode(detail)}"
    ) {
        companion object {
            const val pattern = "demo_payment/{auditId}?amount={amount}&detail={detail}"
        }
    }
}
