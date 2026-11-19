package com.lipabill.app.viewmodel

import com.lipabill.app.data.tickets.EventTicketSms
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
import com.lipabill.app.data.tickets.BarcodeSpec
import com.lipabill.app.data.tickets.TicketImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TicketsViewModel(
    private val app: LipaBillApp
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val tickets: StateFlow<List<Ticket>> = app.ticketRepository.observeAll()
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** SGR: paste booking SMS → ticket awaiting boarding pass. */
    /**
     * A pasted ticket SMS: an SGR booking keeps the booking flow; any other ticket SMS becomes
     * an event pass (QR pending) or joins the pass already saved for the same ticket.
     */
    suspend fun addFromConfirmation(message: String): Long? = withContext(Dispatchers.IO) {
        if (EventTicketSms.looksLikeRailBooking(message)) {
            val draft = BookingConfirmationParser.parse(message)
            app.ticketRepository.addImport(draft)
                ?: throw IllegalArgumentException("That booking ref is already saved.")
        } else {
            app.ticketRepository.addOrMerge(EventTicketSms.parse(message))
                ?: throw IllegalArgumentException("Couldn't save that ticket.")
        }
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

    /** Reads a ticket file into a draft without saving (the review step edits it). */
    suspend fun readDocument(
        context: Context,
        uri: Uri,
        kind: TicketDocumentKind
    ): TicketImport = withContext(Dispatchers.IO) {
        TicketDocumentImporter.import(context, uri, kind)
    }

    /** Saves a draft read by [readDocument] (after review, for PDF / photo event tickets). */
    suspend fun saveImport(
        draft: TicketImport,
        kind: TicketDocumentKind
    ): Long? = withContext(Dispatchers.IO) {
        when (kind) {
            // Event tickets join a pass already saved for the same ticket (e.g. from its SMS).
            TicketDocumentKind.EVENT, TicketDocumentKind.PKPASS -> {
                app.ticketRepository.addOrMerge(
                    draft.copy(expectsBoardingPass = false, hasBoardingPass = true)
                ) ?: throw IllegalArgumentException("That ticket is already saved.")
            }
            else -> {
                app.ticketRepository.addOrMerge(draft)
                    ?: throw IllegalArgumentException("That ticket is already saved.")
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { app.ticketRepository.delete(id) }
    }

}

class TicketDetailViewModel(
    private val app: LipaBillApp,
    ticketId: Long
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val ticket: StateFlow<Ticket?> = app.ticketRepository.observeById(ticketId)
        .onEach { _isLoading.value = false }
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
            // Keep the detected symbology (QR / PDF417 / Aztec) and its encoding spec.
            val draft = TicketDocumentImporter.import(context, uri, TicketDocumentKind.SGR_TICKET)
                .copy(
                    expectsBoardingPass = true,
                    hasBoardingPass = true
                )
            app.ticketRepository.attachBoardingPass(current.id, draft, leg) != null
        }

    suspend fun attachBoardingPassScan(
        barcodeValue: String,
        leg: BoardingLeg? = null,
        spec: BarcodeSpec? = null
    ): Boolean =
        withContext(Dispatchers.IO) {
            val current = ticket.value ?: return@withContext false
            val payload = barcodeValue
            if (payload.isBlank()) return@withContext false
            val draft = TicketImport(
                title = "Boarding pass",
                barcodeValue = payload,
                barcodeFormat = spec?.format ?: TicketBarcodeFormat.QR_CODE,
                barcodeSpec = spec,
                orderId = current.orderId,
                source = TicketSource.MANUAL_SCAN,
                expectsBoardingPass = true,
                hasBoardingPass = true
            )
            app.ticketRepository.attachBoardingPass(current.id, draft, leg) != null
        }

    fun delete(onDone: () -> Unit) {
        val current = ticket.value ?: return
        viewModelScope.launch {
            app.ticketRepository.delete(current.id)
            onDone()
        }
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
