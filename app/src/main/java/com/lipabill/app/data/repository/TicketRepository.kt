package com.lipabill.app.data.repository

import com.lipabill.app.data.local.dao.TicketDao
import com.lipabill.app.data.local.entity.TicketEntity
import com.lipabill.app.data.model.BoardingLeg
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
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

    suspend fun getById(id: Long): Ticket? = withContext(Dispatchers.IO) {
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
        boardingNotes: String? = null
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
                boardingNotes = boardingNotes?.trim()?.ifBlank { null }
            )
        )
    }

    suspend fun addImport(draft: TicketImport): Long? {
        return if (draft.expectsBoardingPass && !draft.hasBoardingPass) {
            add(
                title = draft.title,
                barcodeValue = draft.barcodeValue,
                source = draft.source,
                venue = draft.venue,
                startsAtMillis = draft.startsAtMillis,
                seatOrTier = draft.seatOrTier,
                orderId = draft.orderId,
                notes = draft.notes,
                barcodeFormat = draft.barcodeFormat,
                expectsBoardingPass = true,
                hasBoardingPass = false
            )
        } else if (draft.expectsBoardingPass && draft.hasBoardingPass) {
            add(
                title = draft.title,
                barcodeValue = draft.barcodeValue,
                source = draft.source,
                venue = draft.venue,
                startsAtMillis = draft.startsAtMillis,
                seatOrTier = draft.seatOrTier,
                orderId = draft.orderId,
                notes = draft.notes,
                barcodeFormat = draft.barcodeFormat,
                expectsBoardingPass = true,
                hasBoardingPass = true,
                boardingBarcodeValue = draft.barcodeValue,
                boardingBarcodeFormat = draft.barcodeFormat,
                boardingTitle = draft.title,
                boardingVenue = draft.venue,
                boardingStartsAtMillis = draft.startsAtMillis,
                boardingSeatOrTier = draft.seatOrTier,
                boardingNotes = draft.notes
            )
        } else {
            add(
                title = draft.title,
                barcodeValue = draft.barcodeValue,
                source = draft.source,
                venue = draft.venue,
                startsAtMillis = draft.startsAtMillis,
                seatOrTier = draft.seatOrTier,
                orderId = draft.orderId,
                notes = draft.notes,
                barcodeFormat = draft.barcodeFormat,
                expectsBoardingPass = false,
                hasBoardingPass = true
            )
        }
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
        val payload = draft.barcodeValue.trim()
        if (payload.isEmpty()) return@withContext null
        if (codeInUse(payload, exceptId = ticketId)) return@withContext null

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
                boardingNotes = draft.notes?.trim()?.ifBlank { null }
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
                returnBoardingNotes = draft.notes?.trim()?.ifBlank { null }
            )
        }
        dao.update(updated)
        ticketId
    }

    /**
     * Finds an open confirmation that likely matches this boarding pass
     * (same booking ref, or same route + departure day).
     */
    suspend fun findMatchingConfirmation(draft: TicketImport): Ticket? = withContext(Dispatchers.IO) {
        val ref = draft.orderId?.trim()?.ifBlank { null }
        if (ref != null) {
            dao.getByOrderId(ref)?.takeIf { !it.hasBoardingPass }?.toDomain()?.let { return@withContext it }
            dao.getAwaitingBoardingPass().firstOrNull { open ->
                val openRef = open.orderId ?: return@firstOrNull false
                ref.contains(openRef, ignoreCase = true) || openRef.contains(ref, ignoreCase = true)
            }?.toDomain()?.let { return@withContext it }
        }

        val awaiting = dao.getAwaitingBoardingPass()
        if (awaiting.isEmpty()) return@withContext null
        val draftDay = draft.startsAtMillis?.let { it / 86_400_000L }
        awaiting.firstOrNull { open ->
            val sameVenue = !draft.venue.isNullOrBlank() &&
                !open.venue.isNullOrBlank() &&
                venuesCompatible(open.venue!!, draft.venue!!)
            val sameDay = draftDay != null &&
                open.startsAtMillis != null &&
                open.startsAtMillis / 86_400_000L == draftDay
            sameVenue && (sameDay || draftDay == null || open.startsAtMillis == null)
        }?.toDomain()
    }

    suspend fun markUsed(id: Long) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, TicketStatus.USED)
    }

    suspend fun markActive(id: Long) = withContext(Dispatchers.IO) {
        dao.updateStatus(id, TicketStatus.ACTIVE)
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
        val returnNote = notesValue(ticket.notes, "Return").orEmpty()
        val origin = notesValue(ticket.notes, "Origin")
        val destination = notesValue(ticket.notes, "Destination")
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
        if (needle.isBlank()) return false
        val h = haystack.uppercase(Locale.US)
        val codes = Regex("""\b([A-Z]{3})\b""").findAll(needle.uppercase(Locale.US))
            .map { it.groupValues[1] }
            .filter { it in KNOWN_IATA }
            .toList()
        if (codes.size >= 2) return codes.all { h.contains(it) }
        return needle.length >= 4 && h.contains(needle.uppercase(Locale.US).take(24))
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

    private fun notesValue(notes: String?, label: String): String? {
        val prefix = "$label:"
        return notes.orEmpty()
            .split(" · ")
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun venuesCompatible(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase()
            .replace("terminus", "")
            .replace("→", "to")
            .replace(Regex("\\s+"), " ")
            .trim()
        val na = norm(a)
        val nb = norm(b)
        return na.contains(nb) || nb.contains(na) ||
            (na.contains("nairobi") && nb.contains("nairobi") &&
                na.contains("mombasa") && nb.contains("mombasa")) ||
            iataOverlap(na, nb)
    }

    private fun iataOverlap(a: String, b: String): Boolean {
        val codes = listOf("nbo", "mba", "znz", "kis", "edl")
        val shared = codes.filter { a.contains(it) && b.contains(it) }
        return shared.size >= 2 ||
            (a.contains("znz") && b.contains("znz") && a.contains("nbo") && b.contains("nbo"))
    }

    companion object {
        private val KNOWN_IATA = setOf(
            "NBO", "MBA", "KIS", "EDL", "MYD", "ZNZ", "JRO", "DAR", "EBB", "KGL", "WIL"
        )
    }
}
