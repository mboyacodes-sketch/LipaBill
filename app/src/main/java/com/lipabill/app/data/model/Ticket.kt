package com.lipabill.app.data.model

import java.time.Instant
import java.time.ZoneId

enum class TicketBarcodeFormat {
    QR_CODE,
    CODE_128,
    PDF_417,
    AZTEC,
    OTHER
}

enum class TicketStatus {
    ACTIVE,
    USED,
    PAST_DUE
}

enum class TicketSource {
    MANUAL_PASTE,
    MANUAL_SCAN,
    PKPASS,
    E_TICKET,
    BOOKING_CONFIRMATION,
    DEEP_LINK,
    PARTNER
}

/**
 * Offline ticket.
 *
 * **Event** ([expectsBoardingPass] = false): one QR / barcode — no boarding-pass step.
 *
 * **Travel** ([expectsBoardingPass] = true): booking first (SMS / e-ticket), then optional
 * outbound boarding in [boardingBarcodeValue] and, for return trips, return boarding in
 * [returnBoardingBarcodeValue] — without overwriting booking fields.
 */
data class Ticket(
    val id: Long = 0,
    val title: String,
    val venue: String? = null,
    val startsAtMillis: Long? = null,
    val seatOrTier: String? = null,
    val barcodeFormat: TicketBarcodeFormat = TicketBarcodeFormat.QR_CODE,
    val barcodeValue: String,
    val orderId: String? = null,
    val source: TicketSource = TicketSource.MANUAL_PASTE,
    val status: TicketStatus = TicketStatus.ACTIVE,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val notes: String? = null,
    /** True for flight / SGR — UI shows booking + boarding sections. */
    val expectsBoardingPass: Boolean = false,
    val hasBoardingPass: Boolean = false,
    /** Outbound / single-leg gate barcode. Independent of booking [barcodeValue]. */
    val boardingBarcodeValue: String? = null,
    val boardingBarcodeFormat: TicketBarcodeFormat? = null,
    val boardingTitle: String? = null,
    val boardingVenue: String? = null,
    val boardingStartsAtMillis: Long? = null,
    val boardingSeatOrTier: String? = null,
    val boardingNotes: String? = null,
    /** Return-leg boarding (round-trip bookings only). */
    val hasReturnBoardingPass: Boolean = false,
    val returnBoardingBarcodeValue: String? = null,
    val returnBoardingBarcodeFormat: TicketBarcodeFormat? = null,
    val returnBoardingTitle: String? = null,
    val returnBoardingVenue: String? = null,
    val returnBoardingStartsAtMillis: Long? = null,
    val returnBoardingSeatOrTier: String? = null,
    val returnBoardingNotes: String? = null,
    /** Banner from a .pkpass (strip, else background, else thumbnail). Relative to app files. */
    val passHeroPath: String? = null,
    /** Organizer logo, or the pass icon when there is no logo. */
    val passLogoPath: String? = null,
    /** Artwork Apple Wallet draws above the barcode. */
    val passFooterPath: String? = null
) {
    /** Booking time, else outbound boarding, else the return leg. */
    val occasionMillis: Long?
        get() = startsAtMillis ?: boardingStartsAtMillis ?: returnBoardingStartsAtMillis

    fun effectiveStatus(nowMillis: Long = System.currentTimeMillis()): TicketStatus {
        if (status == TicketStatus.USED) return TicketStatus.USED
        val start = occasionMillis
        if (start != null && start < nowMillis) return TicketStatus.PAST_DUE
        return when (status) {
            TicketStatus.PAST_DUE -> TicketStatus.PAST_DUE
            else -> TicketStatus.ACTIVE
        }
    }

    val isTravelTicket: Boolean get() = expectsBoardingPass

    /**
     * "Mark as used" is for the event's local calendar day and after.
     * A pass later the same day can still be marked used. Undated passes stay available.
     */
    fun canMarkUsedFromEventDay(
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val start = occasionMillis ?: return true
        val eventDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return !today.isBefore(eventDay)
    }

    /** Booking saved, outbound boarding not attached yet. */
    val isConfirmationOnly: Boolean get() = expectsBoardingPass && !hasBoardingPass

    /**
     * SGR / Madaraka rail travel — booking + boarding use QR codes.
     * Flight e-tickets / boarding passes keep PDF417 (or Aztec) as today.
     */
    val isRailTravel: Boolean
        get() {
            if (!expectsBoardingPass) return false
            if (source == TicketSource.BOOKING_CONFIRMATION) return true
            val notes = notes.orEmpty()
            if (title.contains("SGR", ignoreCase = true)) return true
            if (notes.contains("SGR", ignoreCase = true)) return true
            if (notes.contains("Imported SGR", ignoreCase = true)) return true
            if (notes.contains("Madaraka", ignoreCase = true)) return true
            // Flight markers win over ambiguous “→” venues
            if (notes.contains("Electronic ticket", ignoreCase = true)) return false
            if (notes.contains("Airline:", ignoreCase = true)) return false
            if (notes.contains("Boarding pass", ignoreCase = true) &&
                !notes.contains("Imported SGR", ignoreCase = true)
            ) {
                return false
            }
            return false
        }

    /** Round-trip from e-ticket notes (`Trip: Return`). */
    val isReturnTrip: Boolean
        get() = notes.notesValue("Trip").equals("Return", ignoreCase = true)

    /** Code shown at the gate — outbound boarding when attached; else event QR. */
    val gateBarcodeValue: String?
        get() = when {
            expectsBoardingPass -> boardingBarcodeValue?.takeIf { it.isNotBlank() }
            else -> barcodeValue.takeIf { it.isNotBlank() }
        }

    val gateBarcodeFormat: TicketBarcodeFormat
        get() = when {
            expectsBoardingPass -> boardingBarcodeFormat ?: barcodeFormat
            else -> barcodeFormat
        }

    fun boardingSnapshot(leg: BoardingLeg): BoardingPassSnapshot? = when (leg) {
        BoardingLeg.OUTBOUND -> legSnapshot(
            attached = hasBoardingPass,
            barcodeValue = boardingBarcodeValue,
            barcodeFormat = boardingBarcodeFormat,
            title = boardingTitle,
            venue = boardingVenue,
            startsAtMillis = boardingStartsAtMillis,
            seatOrTier = boardingSeatOrTier,
            notes = boardingNotes
        )
        BoardingLeg.RETURN -> legSnapshot(
            attached = hasReturnBoardingPass,
            barcodeValue = returnBoardingBarcodeValue,
            barcodeFormat = returnBoardingBarcodeFormat,
            title = returnBoardingTitle,
            venue = returnBoardingVenue,
            startsAtMillis = returnBoardingStartsAtMillis,
            seatOrTier = returnBoardingSeatOrTier,
            notes = returnBoardingNotes
        )
    }

    private fun legSnapshot(
        attached: Boolean,
        barcodeValue: String?,
        barcodeFormat: TicketBarcodeFormat?,
        title: String?,
        venue: String?,
        startsAtMillis: Long?,
        seatOrTier: String?,
        notes: String?
    ): BoardingPassSnapshot? {
        if (!attached) return null
        return BoardingPassSnapshot(
            barcodeValue = barcodeValue.orEmpty(),
            barcodeFormat = barcodeFormat,
            title = title,
            venue = venue,
            startsAtMillis = startsAtMillis,
            seatOrTier = seatOrTier,
            notes = notes
        )
    }
}

/** First matching `Label: value` segment, in the order given. */
fun String?.firstNotesValue(vararg labels: String): String? {
    for (label in labels) {
        notesValue(label)?.let { return it }
    }
    return null
}

/** A `Prefix …` piece inside a " · "-joined field, with the prefix removed. */
fun String?.labeledPiece(prefix: String): String? =
    this?.split("·")
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
        ?.removePrefix(prefix)
        ?.trim()

/** Value of a `Label: value` segment in ticket notes joined by " · ". */
fun String?.notesValue(label: String): String? {
    val prefix = "$label:"
    return orEmpty()
        .split(" · ")
        .map { it.trim() }
        .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

/** Fields for one attached boarding pass (outbound or return). */
data class BoardingPassSnapshot(
    val barcodeValue: String,
    val barcodeFormat: TicketBarcodeFormat?,
    val title: String?,
    val venue: String?,
    val startsAtMillis: Long?,
    val seatOrTier: String?,
    val notes: String?
)
