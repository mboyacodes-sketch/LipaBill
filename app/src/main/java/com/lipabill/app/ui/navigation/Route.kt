package com.lipabill.app.ui.navigation

import android.net.Uri

sealed class Route(val path: String) {
    data object List : Route("list")
    data object Settings : Route("settings")
    data object Permission : Route("permission")
    data object Metrics : Route("metrics")
    data object Tickets : Route("tickets")
    data class TicketDetail(val id: Long) : Route("ticket/$id") {
        companion object {
            const val pattern = "ticket/{id}"
        }
    }
    data class Detail(val id: Long) : Route("detail/$id") {
        companion object {
            const val pattern = "detail/{id}"
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
    data class RepeatOnboarding(val id: Long) : Route("repeat_onboarding/$id") {
        companion object {
            const val pattern = "repeat_onboarding/{id}"
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
}
