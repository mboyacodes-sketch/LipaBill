package com.lipabill.app.data.repository

import com.lipabill.app.data.model.withNotesValue
import com.lipabill.app.data.tickets.EventPresets
import com.lipabill.app.data.tickets.EventTicketSms
import com.lipabill.app.BuildConfig
import com.lipabill.app.data.DemoLedger
import com.lipabill.app.data.local.dao.TicketDao
import com.lipabill.app.data.local.entity.TicketEntity
import com.lipabill.app.data.model.BoardingLeg
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.model.notesValue
import com.lipabill.app.data.tickets.BookingConfirmationParser
import com.lipabill.app.data.tickets.TicketImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

class TicketRepository(
    private val dao: TicketDao
) {

    fun observeAll(): Flow<List<Ticket>> =
        dao.observeAll(System.currentTimeMillis()).map { list -> list.map { it.toDomain() } }

    fun observeById(id: Long): Flow<Ticket?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun get(id: Long): Ticket? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    /**
     * Inserts a ticket. Returns id, or null if the barcode / booking ref already exists.
     */
    suspend fun add(
        title: String,
        barcodeValue: String,
        source: TicketSource,
        venue: String? = null,
        startsAtMillis: Long? = null,
        seatOrTier: String? = null,
        orderId: String? = null,
        notes: String? = null,
        barcodeFormat: TicketBarcodeFormat = TicketBarcodeFormat.QR_CODE,
        expectsBoardingPass: Boolean = false,
        hasBoardingPass: Boolean = false,
        boardingBarcodeValue: String? = null,
        boardingBarcodeFormat: TicketBarcodeFormat? = null,
        boardingTitle: String? = null,
        boardingVenue: String? = null,
        boardingStartsAtMillis: Long? = null,
        boardingSeatOrTier: String? = null,
        boardingNotes: String? = null,
        passColors: String? = null
    ): Long? = withContext(Dispatchers.IO) {
        val payload = barcodeValue.trim()
        if (payload.isEmpty()) return@withContext null
        if (codeInUse(payload)) return@withContext null
        val boardingPayload = boardingBarcodeValue?.trim()?.ifBlank { null }
        if (boardingPayload != null &&
            boardingPayload != payload &&
            codeInUse(boardingPayload)
        ) {
            return@withContext null
        }
        val ref = orderId?.trim()?.ifBlank { null }
        if (ref != null && dao.getByOrderId(ref) != null) return@withContext null
        val label = title.trim().ifBlank { "Ticket" }
        dao.insert(
            TicketEntity(
                title = label,
                venue = venue?.trim()?.ifBlank { null },
                startsAtMillis = startsAtMillis,
                seatOrTier = seatOrTier?.trim()?.ifBlank { null },
                barcodeFormat = barcodeFormat,
                barcodeValue = payload,
                orderId = ref,
                source = source,
                status = TicketStatus.ACTIVE,
                notes = notes?.trim()?.ifBlank { null },
                expectsBoardingPass = expectsBoardingPass,
                hasBoardingPass = hasBoardingPass,
                boardingBarcodeValue = boardingPayload,
                boardingBarcodeFormat = boardingBarcodeFormat,
                boardingTitle = boardingTitle?.trim()?.ifBlank { null },
                boardingVenue = boardingVenue?.trim()?.ifBlank { null },
                boardingStartsAtMillis = boardingStartsAtMillis,
                boardingSeatOrTier = boardingSeatOrTier?.trim()?.ifBlank { null },
                boardingNotes = boardingNotes?.trim()?.ifBlank { null },
                passColors = passColors
            )
        )
    }

    suspend fun existingTicketId(barcodeValue: String, orderId: String?): Long? = withContext(Dispatchers.IO) {
        val payload = barcodeValue.trim()
        if (payload.isNotEmpty()) {
            dao.getByBarcodeValue(payload)?.id?.let { return@withContext it }
            dao.getByBoardingBarcodeValue(payload)?.id?.let { return@withContext it }
            dao.getByReturnBoardingBarcodeValue(payload)?.id?.let { return@withContext it }
        }
        val ref = orderId?.trim()?.ifBlank { null } ?: return@withContext null
        dao.getByOrderId(ref)?.id
    }

    /** Debug captures only — sample SGR booking so Passes isn’t empty for Maestro. */
    suspend fun seedDemoPassIfEmpty() {
        if (!BuildConfig.DEBUG) return
        withContext(Dispatchers.IO) {
            if (dao.getByOrderId("KRCX1Y2Z3") != null) return@withContext
            val draft = BookingConfirmationParser.parse(DemoLedger.SGR_BOOKING_SMS)
            addImport(draft)
        }
    }

    /**
     * Saves an event ticket, or folds it into the pass already saved for the same ticket (an SMS
     * and the PDF / screenshot of one ticket, in either order). Matching is by order number,
     * printed ticket number, or the same entry code. Returns the pass id, or null when nothing
     * could be saved.
     */
    suspend fun addOrMerge(draft: TicketImport): Long? {
        if (draft.expectsBoardingPass) return addImport(draft)
        val existing = withContext(Dispatchers.IO) {
            val refs = ticketRefs(draft.orderId, draft.notes, draft.barcodeValue)
            if (refs.isEmpty()) null else dao.eventTickets().firstOrNull { e ->
                ticketRefs(e.orderId, e.notes, e.barcodeValue).any { it in refs }
            }
        } ?: return addImport(draft)
        return withContext(Dispatchers.IO) { merge(existing, draft) }
    }

    /** Fills the saved pass's blanks from [draft]; a real entry code replaces a pending one. */
    private suspend fun merge(existing: TicketEntity, draft: TicketImport): Long? {
        val newCode = draft.barcodeValue.trim()
        val newIsReal = newCode.isNotEmpty() && !EventTicketSms.isPending(newCode)
        // A real code replaces a pending one, and a newer real one (a guest transfer issues a
        // fresh QR and kills the old) replaces the old, unless another pass already holds it.
        val takeCode = newIsReal && newCode != existing.barcodeValue && !codeInUse(newCode, exceptId = existing.id)
        val genericTitle = EventPresets.isGenericTitle(existing.title)
        dao.update(
            existing.copy(
                title = if (genericTitle && !EventPresets.isGenericTitle(draft.title)) draft.title.trim() else existing.title,
                venue = existing.venue ?: draft.venue?.trim()?.ifBlank { null },
                startsAtMillis = existing.startsAtMillis ?: draft.startsAtMillis,
                seatOrTier = existing.seatOrTier ?: draft.seatOrTier?.trim()?.ifBlank { null },
                orderId = existing.orderId ?: draft.orderId?.trim()?.ifBlank { null },
                notes = mergeNotes(existing.notes, draft.notes),
                barcodeValue = if (takeCode) newCode else existing.barcodeValue,
                barcodeFormat = if (takeCode) draft.barcodeFormat else existing.barcodeFormat,
                passColors = existing.passColors ?: draft.passColors
            )
        )
        return existing.id
    }

    suspend fun addImport(draft: TicketImport): Long? {
        val boarding = draft.expectsBoardingPass && draft.hasBoardingPass
        return add(
            title = draft.title,
            barcodeValue = draft.barcodeValue,
            source = draft.source,
            venue = draft.venue,
            startsAtMillis = draft.startsAtMillis,
            seatOrTier = draft.seatOrTier,
            orderId = draft.orderId,
            notes = draft.notes,
            barcodeFormat = draft.barcodeFormat,
            expectsBoardingPass = draft.expectsBoardingPass,
            // Event passes are one barcode. Travel bookings keep the draft's boarding flag.
            hasBoardingPass = if (draft.expectsBoardingPass) draft.hasBoardingPass else true,
            boardingBarcodeValue = draft.barcodeValue.takeIf { boarding },
            boardingBarcodeFormat = draft.barcodeFormat.takeIf { boarding },
            boardingTitle = draft.title.takeIf { boarding },
            boardingVenue = draft.venue.takeIf { boarding },
            boardingStartsAtMillis = draft.startsAtMillis.takeIf { boarding },
            boardingSeatOrTier = draft.seatOrTier.takeIf { boarding },
            boardingNotes = draft.notes.takeIf { boarding },
            passColors = draft.passColors
        )
    }

    /**
     * Attaches boarding-pass details without changing booking confirmation fields.
     * For return trips, [leg] selects outbound vs return slot (auto-detected when null).
     */
    suspend fun attachBoardingPass(
        ticketId: Long,
        draft: TicketImport,
        leg: BoardingLeg? = null
    ): Long? = withContext(Dispatchers.IO) {
        val existing = dao.getById(ticketId) ?: return@withContext null
        val ticket = existing.toDomain()
        // Keep the gate payload byte-for-byte: fixed-width codes can carry edge spaces.
        val payload = draft.barcodeValue
        if (payload.isBlank()) return@withContext null
        if (codeInUse(payload, exceptId = ticketId)) return@withContext null
        val spec = draft.barcodeSpec?.serialize()

        val resolved = leg ?: resolveBoardingLeg(ticket, draft)
        val updated = when (resolved) {
            BoardingLeg.OUTBOUND -> existing.copy(
                expectsBoardingPass = true,
                hasBoardingPass = true,
                boardingBarcodeValue = payload,
                boardingBarcodeFormat = draft.barcodeFormat,
                boardingTitle = draft.title.trim().ifBlank { null },
                boardingVenue = draft.venue?.trim()?.ifBlank { null },
                boardingStartsAtMillis = draft.startsAtMillis,
                boardingSeatOrTier = draft.seatOrTier?.trim()?.ifBlank { null },
                boardingNotes = draft.notes?.trim()?.ifBlank { null },
                boardingBarcodeSpec = spec
            )
            BoardingLeg.RETURN -> existing.copy(
                expectsBoardingPass = true,
                hasReturnBoardingPass = true,
                returnBoardingBarcodeValue = payload,
                returnBoardingBarcodeFormat = draft.barcodeFormat,
                returnBoardingTitle = draft.title.trim().ifBlank { null },
                returnBoardingVenue = draft.venue?.trim()?.ifBlank { null },
                returnBoardingStartsAtMillis = draft.startsAtMillis,
                returnBoardingSeatOrTier = draft.seatOrTier?.trim()?.ifBlank { null },
                returnBoardingNotes = draft.notes?.trim()?.ifBlank { null },
                returnBoardingBarcodeSpec = spec
            )
        }
        dao.update(updated)
        ticketId
    }

    suspend fun markUsed(id: Long) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, TicketStatus.USED)
    }

    suspend fun setEventStartsAt(id: Long, startsAtMillis: Long?) = withContext(Dispatchers.IO) {
        dao.updateStartsAt(id, startsAtMillis)
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    private suspend fun codeInUse(payload: String, exceptId: Long? = null): Boolean {
        val byBooking = dao.getByBarcodeValue(payload)
        if (byBooking != null && byBooking.id != exceptId) return true
        val byBoarding = dao.getByBoardingBarcodeValue(payload)
        if (byBoarding != null && byBoarding.id != exceptId) return true
        val byReturn = dao.getByReturnBoardingBarcodeValue(payload)
        if (byReturn != null && byReturn.id != exceptId) return true
        return false
    }

    /**
     * Prefer empty outbound slot, then return slot for round-trips;
     * otherwise match draft route to booking Origin/Return notes.
     */
    internal fun resolveBoardingLeg(ticket: Ticket, draft: TicketImport): BoardingLeg {
        if (!ticket.isReturnTrip) return BoardingLeg.OUTBOUND
        if (!ticket.hasBoardingPass) return BoardingLeg.OUTBOUND
        if (!ticket.hasReturnBoardingPass) return BoardingLeg.RETURN

        val draftRoute = draft.venue.orEmpty() + " " + draft.notes.orEmpty() + " " + draft.title
        val returnNote = ticket.notes.notesValue("Return").orEmpty()
        val origin = ticket.notes.notesValue("Origin")
        val destination = ticket.notes.notesValue("Destination")
        val outboundHint = listOfNotNull(ticket.venue, origin, destination).joinToString(" ")

        val looksReturn = routeHintMatch(draftRoute, returnNote) ||
            mirroredRouteMatch(draftRoute, ticket.venue)
        val looksOutbound = routeHintMatch(draftRoute, outboundHint)

        return when {
            looksReturn && !looksOutbound -> BoardingLeg.RETURN
            looksOutbound && !looksReturn -> BoardingLeg.OUTBOUND
            else -> BoardingLeg.OUTBOUND // replace outbound by default when both filled
        }
    }

    private fun routeHintMatch(haystack: String, needle: String): Boolean {
        if (needle.isBlank() || needle.length < 4) return false
        return haystack.uppercase(Locale.US).contains(needle.uppercase(Locale.US).take(24))
    }

    private fun mirroredRouteMatch(draft: String, outboundVenue: String?): Boolean {
        if (outboundVenue.isNullOrBlank() || !outboundVenue.contains("→")) return false
        val parts = outboundVenue.split("→", limit = 2).map { it.trim().uppercase(Locale.US) }
        if (parts.size < 2) return false
        val h = draft.uppercase(Locale.US)
        // Return is reverse of outbound
        return h.contains(parts[1].take(3)) && h.contains(parts[0].take(3)) &&
            h.indexOf(parts[1].take(3)) < h.indexOf(parts[0].take(3))
    }


    private companion object {
        /** Order number, printed ticket number and real (or pending) entry code, normalised. */
        fun ticketRefs(orderId: String?, notes: String?, code: String?): Set<String> =
            listOfNotNull(
                orderId,
                notes.notesValue("Ticket no"),
                code?.removePrefix(EventTicketSms.PENDING_PREFIX)
            )
                .map { it.uppercase().filter(Char::isLetterOrDigit) }
                .filter { it.length >= 4 }
                .toSet()

        /** Adds `Label: value` segments the saved notes lack; saved values win. */
        fun mergeNotes(saved: String?, incoming: String?): String? {
            var out = saved
            incoming.orEmpty().split(" · ").map { it.trim() }.forEach { segment ->
                val label = segment.substringBefore(':', "").trim()
                if (label.isEmpty()) return@forEach
                if (out.notesValue(label) == null) out = out.withNotesValue(label, segment.substringAfter(':').trim())
            }
            return out
        }
    }
}
