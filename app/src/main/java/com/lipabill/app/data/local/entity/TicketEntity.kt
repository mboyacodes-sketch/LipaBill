package com.lipabill.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus

@Entity(
    tableName = "tickets",
    indices = [
        Index(value = ["barcodeValue"], unique = true),
        Index(value = ["boardingBarcodeValue"], unique = true),
        Index(value = ["returnBoardingBarcodeValue"], unique = true),
        Index(value = ["orderId"]),
        Index(value = ["createdAtMillis"])
    ]
)
data class TicketEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val expectsBoardingPass: Boolean = false,
    val hasBoardingPass: Boolean = false,
    val boardingBarcodeValue: String? = null,
    val boardingBarcodeFormat: TicketBarcodeFormat? = null,
    val boardingTitle: String? = null,
    val boardingVenue: String? = null,
    val boardingStartsAtMillis: Long? = null,
    val boardingSeatOrTier: String? = null,
    val boardingNotes: String? = null,
    val hasReturnBoardingPass: Boolean = false,
    val returnBoardingBarcodeValue: String? = null,
    val returnBoardingBarcodeFormat: TicketBarcodeFormat? = null,
    val returnBoardingTitle: String? = null,
    val returnBoardingVenue: String? = null,
    val returnBoardingStartsAtMillis: Long? = null,
    val returnBoardingSeatOrTier: String? = null,
    val returnBoardingNotes: String? = null
) {
    fun toDomain(): Ticket = Ticket(
        id = id,
        title = title,
        venue = venue,
        startsAtMillis = startsAtMillis,
        seatOrTier = seatOrTier,
        barcodeFormat = barcodeFormat,
        barcodeValue = barcodeValue,
        orderId = orderId,
        source = source,
        status = status,
        createdAtMillis = createdAtMillis,
        notes = notes,
        expectsBoardingPass = expectsBoardingPass,
        hasBoardingPass = hasBoardingPass,
        boardingBarcodeValue = boardingBarcodeValue,
        boardingBarcodeFormat = boardingBarcodeFormat,
        boardingTitle = boardingTitle,
        boardingVenue = boardingVenue,
        boardingStartsAtMillis = boardingStartsAtMillis,
        boardingSeatOrTier = boardingSeatOrTier,
        boardingNotes = boardingNotes,
        hasReturnBoardingPass = hasReturnBoardingPass,
        returnBoardingBarcodeValue = returnBoardingBarcodeValue,
        returnBoardingBarcodeFormat = returnBoardingBarcodeFormat,
        returnBoardingTitle = returnBoardingTitle,
        returnBoardingVenue = returnBoardingVenue,
        returnBoardingStartsAtMillis = returnBoardingStartsAtMillis,
        returnBoardingSeatOrTier = returnBoardingSeatOrTier,
        returnBoardingNotes = returnBoardingNotes
    )

    companion object {
        fun fromDomain(ticket: Ticket): TicketEntity = TicketEntity(
            id = ticket.id,
            title = ticket.title,
            venue = ticket.venue,
            startsAtMillis = ticket.startsAtMillis,
            seatOrTier = ticket.seatOrTier,
            barcodeFormat = ticket.barcodeFormat,
            barcodeValue = ticket.barcodeValue,
            orderId = ticket.orderId,
            source = ticket.source,
            status = ticket.status,
            createdAtMillis = ticket.createdAtMillis,
            notes = ticket.notes,
            expectsBoardingPass = ticket.expectsBoardingPass,
            hasBoardingPass = ticket.hasBoardingPass,
            boardingBarcodeValue = ticket.boardingBarcodeValue,
            boardingBarcodeFormat = ticket.boardingBarcodeFormat,
            boardingTitle = ticket.boardingTitle,
            boardingVenue = ticket.boardingVenue,
            boardingStartsAtMillis = ticket.boardingStartsAtMillis,
            boardingSeatOrTier = ticket.boardingSeatOrTier,
            boardingNotes = ticket.boardingNotes,
            hasReturnBoardingPass = ticket.hasReturnBoardingPass,
            returnBoardingBarcodeValue = ticket.returnBoardingBarcodeValue,
            returnBoardingBarcodeFormat = ticket.returnBoardingBarcodeFormat,
            returnBoardingTitle = ticket.returnBoardingTitle,
            returnBoardingVenue = ticket.returnBoardingVenue,
            returnBoardingStartsAtMillis = ticket.returnBoardingStartsAtMillis,
            returnBoardingSeatOrTier = ticket.returnBoardingSeatOrTier,
            returnBoardingNotes = ticket.returnBoardingNotes
        )
    }
}
