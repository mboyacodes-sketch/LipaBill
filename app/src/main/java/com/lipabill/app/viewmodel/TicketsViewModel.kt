package com.lipabill.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.BoardingLeg
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.tickets.BookingConfirmationParser
import com.lipabill.app.data.tickets.TicketDocumentImporter
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.data.tickets.TicketImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TicketsViewModel(
    private val app: LipaBillApp
) : ViewModel() {

    val tickets: StateFlow<List<Ticket>> = app.ticketRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** SGR: paste booking SMS → ticket awaiting boarding pass. */
    suspend fun addFromConfirmation(message: String): Long? = withContext(Dispatchers.IO) {
        val draft = BookingConfirmationParser.parse(message)
        app.ticketRepository.addImport(draft)
            ?: throw IllegalArgumentException("That booking ref is already saved.")
    }

    suspend fun addFromPaste(
        title: String,
        barcodeValue: String,
        venue: String?,
        startsAtMillis: Long? = null
    ): Long? =
        app.ticketRepository.add(
            title = title,
            barcodeValue = barcodeValue,
            source = TicketSource.MANUAL_PASTE,
            venue = venue,
            startsAtMillis = startsAtMillis,
            expectsBoardingPass = false,
            hasBoardingPass = true
        )

    /**
     * Import a file as an event ticket (one-shot) or flight e-ticket (awaits boarding).
     */
    suspend fun addFromDocument(
        context: Context,
        uri: Uri,
        kind: TicketDocumentKind
    ): Long? = withContext(Dispatchers.IO) {
        val draft = TicketDocumentImporter.import(context, uri, kind)
        when (kind) {
            TicketDocumentKind.EVENT, TicketDocumentKind.PKPASS -> {
                app.ticketRepository.addImport(
                    draft.copy(expectsBoardingPass = false, hasBoardingPass = true)
                ) ?: throw IllegalArgumentException("That ticket is already saved.")
            }
            TicketDocumentKind.FLIGHT_E_TICKET -> {
                app.ticketRepository.addImport(
                    draft.copy(expectsBoardingPass = true, hasBoardingPass = draft.hasBoardingPass)
                ) ?: throw IllegalArgumentException("That booking is already saved.")
            }
            else -> {
                app.ticketRepository.addImport(draft)
                    ?: throw IllegalArgumentException("That ticket is already saved.")
            }
        }
    }

}

class TicketDetailViewModel(
    private val app: LipaBillApp,
    ticketId: Long
) : ViewModel() {

    val ticket: StateFlow<Ticket?> = app.ticketRepository.observeById(ticketId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markUsed() {
        val id = ticket.value?.id ?: return
        viewModelScope.launch { app.ticketRepository.markUsed(id) }
    }

    fun setEventStartsAt(startsAtMillis: Long?) {
        val id = ticket.value?.id ?: return
        viewModelScope.launch { app.ticketRepository.setEventStartsAt(id, startsAtMillis) }
    }

    suspend fun attachBoardingPass(
        context: Context,
        uri: Uri,
        leg: BoardingLeg? = null
    ): Boolean =
        withContext(Dispatchers.IO) {
            val current = ticket.value ?: return@withContext false
            val kind = resolveAttachKind(current)
            var draft = TicketDocumentImporter.import(context, uri, kind)
                .copy(expectsBoardingPass = true, hasBoardingPass = true)
            draft = when (kind) {
                // Flight boarding keeps PDF417 / BCBP-style codes
                TicketDocumentKind.BOARDING_PASS ->
                    draft.copy(barcodeFormat = TicketBarcodeFormat.PDF_417)
                // SGR boarding is always a QR (never invent a flight PDF417)
                TicketDocumentKind.SGR_TICKET ->
                    draft.copy(barcodeFormat = TicketBarcodeFormat.QR_CODE)
                else -> draft
            }
            app.ticketRepository.attachBoardingPass(current.id, draft, leg) != null
        }

    suspend fun attachBoardingPassScan(
        barcodeValue: String,
        leg: BoardingLeg? = null
    ): Boolean =
        withContext(Dispatchers.IO) {
            val current = ticket.value ?: return@withContext false
            val payload = barcodeValue.trim()
            if (payload.isEmpty()) return@withContext false
            val isFlight = resolveAttachKind(current) == TicketDocumentKind.BOARDING_PASS
            val draft = TicketImport(
                title = "Boarding pass",
                barcodeValue = payload,
                barcodeFormat = if (isFlight) TicketBarcodeFormat.PDF_417 else TicketBarcodeFormat.QR_CODE,
                orderId = current.orderId,
                notes = if (isFlight) "Boarding pass" else null,
                source = TicketSource.MANUAL_SCAN,
                expectsBoardingPass = true,
                hasBoardingPass = true
            )
            app.ticketRepository.attachBoardingPass(current.id, draft, leg) != null
        }

    fun delete(onDone: () -> Unit) {
        val id = ticket.value?.id ?: return
        viewModelScope.launch {
            app.ticketRepository.delete(id)
            onDone()
        }
    }
}

private fun resolveAttachKind(current: Ticket): TicketDocumentKind {
    val notes = current.notes.orEmpty()
    val looksFlight = notes.contains("Electronic ticket", ignoreCase = true) ||
        notes.contains("Airline:", ignoreCase = true) ||
        current.title.contains(Regex("""(?i)\b([A-Z0-9]{2}\s?\d{2,4})\b""")) ||
        current.venue.orEmpty().contains("→") &&
        Regex("""\b[A-Z]{3}\b""").findAll(current.venue.orEmpty()).count() >= 2
    return when {
        looksFlight -> TicketDocumentKind.BOARDING_PASS
        current.source == TicketSource.BOOKING_CONFIRMATION -> TicketDocumentKind.SGR_TICKET
        else -> TicketDocumentKind.BOARDING_PASS
    }
}

fun TicketStatus.label(): String = when (this) {
    TicketStatus.ACTIVE -> "Active"
    TicketStatus.USED -> "Used"
    TicketStatus.PAST_DUE -> "Past due"
}

fun Ticket.phaseLabel(): String = when {
    !isTravelTicket -> "Event"
    isConfirmationOnly -> "Confirmation"
    else -> "Boarding pass"
}
