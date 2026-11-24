package com.lipabill.app.viewmodel

import com.lipabill.app.data.tickets.EventTicketSms
import android.content.Context
import android.graphics.Bitmap
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
import com.lipabill.app.data.tickets.PassDocumentReader
import com.lipabill.app.data.tickets.PassDraft
import com.lipabill.app.data.tickets.GroupTickets
import com.lipabill.app.metrics.AppLog
import com.lipabill.app.data.tickets.intelligence.PassFieldReader
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.data.tickets.BarcodeSpec
import com.lipabill.app.data.tickets.TicketImport
import kotlinx.coroutines.CancellationException
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

    private val reader = PassDocumentReader(app.passDocuments)

    /**
     * Reads a ticket file into a draft without saving (the review step edits it). The original
     * is kept, encrypted; a file already saved as a pass throws [AlreadySavedException].
     */
    suspend fun readDocument(
        context: Context,
        uri: Uri,
        kind: TicketDocumentKind
    ): PassDraft = withContext(Dispatchers.IO) {
        reader.read(context, uri, kind)
    }

    /** The original file, drawn small for the review step. */
    suspend fun preview(read: PassDraft, maxWidth: Int): Bitmap? =
        read.documentId?.let { app.passDocuments.thumbnail(it, maxWidth) }

    /** Review was cancelled: the stored original goes unless a pass already uses it. */
    fun discardDocument(read: PassDraft) {
        val id = read.documentId ?: return
        viewModelScope.launch { app.passDocuments.discardDraft(id) }
    }

    /**
     * Saves a reviewed draft. The pass, its original file and the reading (with the user's
     * corrections marked as theirs) are linked in one transaction.
     */
    suspend fun saveImport(
        draft: TicketImport,
        kind: TicketDocumentKind,
        read: PassDraft? = null,
        /** Also save the file's other tickets ([PassDraft.extraCodes]) as their own passes. */
        saveExtras: Boolean = false
    ): Long? = withContext(Dispatchers.IO) {
        val group = if (saveExtras) GroupTickets.drafts(draft, read?.extraCodes.orEmpty()) else listOf(draft)
        val main = group.first()
        val extraction = read?.let { PassFieldReader.withEdits(it.extraction, main) }
        val id = app.passDocuments.saveWithDocument(read?.documentId, extraction) {
            saveDraft(main, kind)
        }
        if (id != null) {
            // Never merged: group tickets share an order ref, and a merge would swap the
            // first pass's code for this one. A code already saved is skipped.
            val skipped = group.drop(1).count { extra ->
                try {
                    saveDraft(extra, kind, merge = false) == null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    true
                }
            }
            if (skipped > 0) AppLog.w(TAG, "group_extra_skipped=$skipped of ${group.size - 1}")
        }
        id
    }

    private suspend fun saveDraft(
        draft: TicketImport,
        kind: TicketDocumentKind,
        merge: Boolean = true
    ): Long? {
        suspend fun save(d: TicketImport): Long? =
            if (merge) app.ticketRepository.addOrMerge(d) else app.ticketRepository.addImport(d)
        return when (kind) {
            // Event tickets join a pass already saved for the same ticket (e.g. from its SMS).
            TicketDocumentKind.EVENT, TicketDocumentKind.PKPASS -> {
                save(draft.copy(expectsBoardingPass = false, hasBoardingPass = true))
                    ?: throw IllegalArgumentException("That ticket is already saved.")
            }
            // Picked as SGR in review: save a rail pass (booking + boarding on one code), marked the
            // way parsed SGR tickets are so the Passes list shows it with travel chrome.
            TicketDocumentKind.SGR_TICKET -> {
                val notes = draft.notes.orEmpty()
                val railNotes = if (notes.contains(SGR_MARKER, ignoreCase = true)) {
                    notes
                } else {
                    listOf(notes, SGR_MARKER).filter { it.isNotBlank() }.joinToString(" · ")
                }
                save(draft.copy(expectsBoardingPass = true, hasBoardingPass = true, notes = railNotes))
                    ?: throw IllegalArgumentException("That ticket is already saved.")
            }
            else -> {
                save(draft) ?: throw IllegalArgumentException("That ticket is already saved.")
            }
        }
    }

    private companion object {
        /** Same marker [com.lipabill.app.data.tickets.ETicketTextParser] writes on SGR tickets. */
        const val SGR_MARKER = "Imported SGR ticket"
        const val TAG = "LipaBill.Tickets"
    }

    /** From the Today card: a finished or live pass marked used without opening it. */
    fun markUsed(id: Long) {
        viewModelScope.launch { app.ticketRepository.markUsed(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { app.ticketRepository.delete(id) }
    }

}

class TicketDetailViewModel(
    private val app: LipaBillApp,
    private val ticketId: Long
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val ticket: StateFlow<Ticket?> = app.ticketRepository.observeById(ticketId)
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _hasOriginal = MutableStateFlow(false)
    val hasOriginal: StateFlow<Boolean> = _hasOriginal.asStateFlow()

    init {
        viewModelScope.launch {
            _hasOriginal.value = app.passDocuments.firstForTicket(ticketId) != null
        }
    }

    /** Opens the stored original via FileProvider; the temp copy is deleted in [onCleared]. */
    fun viewOriginal(onOpened: (android.net.Uri, String) -> Unit, onMissing: () -> Unit) {
        viewModelScope.launch {
            val opened = app.passDocuments.openOriginalForTicket(ticketId)
            if (opened == null) {
                onMissing()
                return@launch
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                app,
                "${app.packageName}.wrapped",
                opened.file
            )
            openedCopy?.takeIf { it != opened.file }?.delete()
            openedCopy = opened.file
            onOpened(uri, opened.mimeType)
        }
    }

    /**
     * Plaintext copy handed to the viewer app. Kept while this pass is on screen (the viewer reads
     * it after we pause), deleted once the user leaves the pass. App start sweeps any leftover.
     */
    private var openedCopy: java.io.File? = null

    override fun onCleared() {
        openedCopy?.delete()
        openedCopy = null
    }

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
            // Keep the original next to the pass (encrypted). A failed keep never blocks the attach.
            val stored = runCatching { app.passDocuments.store(uri) }.getOrNull()
            val attached = try {
                // Keep the detected symbology (QR / PDF417 / Aztec) and its encoding spec.
                val draft = TicketDocumentImporter.import(context, uri, TicketDocumentKind.SGR_TICKET)
                    .copy(
                        expectsBoardingPass = true,
                        hasBoardingPass = true
                    )
                app.ticketRepository.attachBoardingPass(current.id, draft, leg) != null
            } catch (t: Throwable) {
                stored?.takeUnless { it.duplicate }?.let { app.passDocuments.discardDraft(it.document.id) }
                throw t
            }
            if (stored != null && stored.document.ticketId == null) {
                if (attached) {
                    app.passDocuments.linkToTicket(stored.document.id, current.id)
                } else {
                    app.passDocuments.discardDraft(stored.document.id)
                }
            }
            attached
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
