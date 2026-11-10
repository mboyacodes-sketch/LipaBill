package com.lipabill.app.viewmodel

import com.lipabill.app.data.tickets.ETicketAnalyzer
import com.lipabill.app.data.tickets.PkPassParser
import com.lipabill.app.data.tickets.smart.GeminiNanoPassAi
import com.lipabill.app.data.tickets.smart.PassDraft
import com.lipabill.app.data.tickets.smart.PassInput
import com.lipabill.app.data.tickets.smart.SmartPassReader
import com.lipabill.app.data.tickets.smart.toTicketImport
import com.lipabill.app.data.tickets.toTicketImport
import java.time.LocalDate
import java.time.ZoneId
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

    private val smartReader = SmartPassReader(GeminiNanoPassAi())

    var ticketHolderName: String?
        get() = app.securePreferences.ticketHolderName
        set(value) {
            app.securePreferences.ticketHolderName = value
        }

    /** Smart add: read pasted text or a scanned code into a draft. Nothing is saved. */
    suspend fun readPass(input: PassInput): PassDraft = withContext(Dispatchers.Default) {
        smartReader.read(input, LocalDate.now(), ticketHolderName)
    }

    /** Smart add: read a .pkpass, PDF or photo into a draft. Nothing is saved. */
    suspend fun readPassDocument(context: Context, uri: Uri): PassDraft = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)
        val isPkPass = PkPassParser.isPkPassUri(uri, mime, uri.lastPathSegment)
        val input = if (isPkPass) {
            PassInput(pkpass = PkPassParser.parse(context, uri).toTicketImport(), source = TicketSource.PKPASS)
        } else {
            val read = ETicketAnalyzer.extract(context, uri)
            PassInput(text = read.text, barcodes = read.barcodes, source = TicketSource.E_TICKET)
        }
        smartReader.read(input, LocalDate.now(), ticketHolderName)
    }

    /** Saves a reviewed draft the same way today's imports are saved. */
    suspend fun saveDraft(draft: PassDraft): Long? = withContext(Dispatchers.IO) {
        val import = draft.toTicketImport(ZoneId.systemDefault())
            ?: throw IllegalArgumentException("This pass has no scannable code yet.")
        app.ticketRepository.addImport(import)
            ?: throw IllegalArgumentException("That ticket is already saved.")
    }

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

    /** Import a file as an event ticket (one-shot) or SGR e-ticket. */
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
            val draft = TicketDocumentImporter.import(context, uri, TicketDocumentKind.SGR_TICKET)
                .copy(
                    expectsBoardingPass = true,
                    hasBoardingPass = true,
                    barcodeFormat = TicketBarcodeFormat.QR_CODE
                )
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
            val draft = TicketImport(
                title = "Boarding pass",
                barcodeValue = payload,
                barcodeFormat = TicketBarcodeFormat.QR_CODE,
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
