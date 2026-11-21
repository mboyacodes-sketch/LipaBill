package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus

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
    /** SGR — show booking + boarding UI. False for one-shot event tickets. */
    val expectsBoardingPass: Boolean = false,
    val hasBoardingPass: Boolean = true,
    /** How the source code was encoded, when it was read from pixels. */
    val barcodeSpec: BarcodeSpec? = null,
    /** Serialized [PassColors] from a Wallet pass. */
    val passColors: String? = null,
    /**
     * Landing status from Pass Intelligence (cancelled → used, start already gone → past due).
     * Defaults to active for paste / scan paths that don't run lifecycle.
     */
    val status: TicketStatus = TicketStatus.ACTIVE
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
    passColors = colors?.serialize(),
    source = TicketSource.PKPASS,
    expectsBoardingPass = false,
    hasBoardingPass = true
)
