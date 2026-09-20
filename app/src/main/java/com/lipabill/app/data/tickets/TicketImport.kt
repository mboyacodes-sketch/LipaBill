package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource

/**
 * Normalized draft ready to insert / attach on a [com.lipabill.app.data.model.Ticket].
 */
data class TicketImport(
    val title: String,
    val venue: String? = null,
    val startsAtMillis: Long? = null,
    val seatOrTier: String? = null,
    val barcodeValue: String,
    val barcodeFormat: TicketBarcodeFormat = TicketBarcodeFormat.QR_CODE,
    val orderId: String? = null,
    val notes: String? = null,
    val source: TicketSource,
    /** Flight / SGR — show booking + boarding UI. False for one-shot event tickets. */
    val expectsBoardingPass: Boolean = false,
    val hasBoardingPass: Boolean = true
)

fun ParsedPkPass.toTicketImport(): TicketImport = TicketImport(
    title = title,
    venue = venue,
    startsAtMillis = startsAtMillis,
    seatOrTier = seatOrTier,
    barcodeValue = barcodeValue,
    barcodeFormat = barcodeFormat,
    orderId = orderId,
    notes = notes,
    source = TicketSource.PKPASS,
    expectsBoardingPass = false,
    hasBoardingPass = true
)
