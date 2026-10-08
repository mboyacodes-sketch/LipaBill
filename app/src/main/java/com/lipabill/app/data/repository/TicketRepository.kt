package com.lipabill.app.data.repository

import com.lipabill.app.data.local.dao.TicketDao
import com.lipabill.app.data.local.entity.TicketEntity
import com.lipabill.app.data.model.BoardingLeg
import com.lipabill.app.data.model.Ticket
import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.model.notesValue
import com.lipabill.app.data.tickets.KNOWN_IATA
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
        passHeroPath: String? = null,
        passLogoPath: String? = null,
        passFooterPath: String? = null
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
                passHeroPath = passHeroPath?.trim()?.ifBlank { null },
                passLogoPath = passLogoPath?.trim()?.ifBlank { null },
                passFooterPath = passFooterPath?.trim()?.ifBlank { null }
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
            passHeroPath = draft.passHeroPath,
            passLogoPath = draft.passLogoPath,
            passFooterPath = draft.passFooterPath
        )
    }

    /**
     * Inserts [draft]. If that barcode is already saved and this import has pass
     * artwork, stores the artwork on the existing ticket and returns its id.
     */
    suspend fun addImportOrUpdateArt(draft: TicketImport): Long? {
        addImport(draft)?.let { return it }
        if (!draft.hasPassArt()) return null
        val existing = existingTicketId(draft.barcodeValue, draft.orderId) ?: return null
        setPassArtwork(existing, draft.passHeroPath, draft.passLogoPath, draft.passFooterPath)
        return existing
    }

    private suspend fun setPassArtwork(id: Long, hero: String?, logo: String?, footer: String?) =
        withContext(Dispatchers.IO) {
            val heroPath = hero?.trim()?.ifBlank { null }
            val logoPath = logo?.trim()?.ifBlank { null }
            val footerPath = footer?.trim()?.ifBlank { null }
            if (heroPath == null && logoPath == null && footerPath == null) return@withContext
            dao.updatePassArtwork(id, heroPath, logoPath, footerPath)
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
        dao.update(updated.fillingPassArt(draft))
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
        if (needle.isBlank()) return false
        val h = haystack.uppercase(Locale.US)
        val codes = Regex("""\b([A-Z]{3})\b""").findAll(needle.uppercase(Locale.US))
            .map { it.groupValues[1] }
            .filter { it in KNOWN_IATA }
            .toList()
        if (codes.size >= 2) return codes.all { h.contains(it) }
        return needle.length >= 4 && h.contains(needle.uppercase(Locale.US).take(24))
    }

    /** Keeps artwork already on the ticket. Fills a slot only when it is empty. */
    private fun TicketEntity.fillingPassArt(draft: TicketImport) = copy(
        passHeroPath = passHeroPath ?: draft.passHeroPath?.trim()?.ifBlank { null },
        passLogoPath = passLogoPath ?: draft.passLogoPath?.trim()?.ifBlank { null },
        passFooterPath = passFooterPath ?: draft.passFooterPath?.trim()?.ifBlank { null }
    )

    private fun mirroredRouteMatch(draft: String, outboundVenue: String?): Boolean {
        if (outboundVenue.isNullOrBlank() || !outboundVenue.contains("→")) return false
        val parts = outboundVenue.split("→", limit = 2).map { it.trim().uppercase(Locale.US) }
        if (parts.size < 2) return false
        val h = draft.uppercase(Locale.US)
        // Return is reverse of outbound
        return h.contains(parts[1].take(3)) && h.contains(parts[0].take(3)) &&
            h.indexOf(parts[1].take(3)) < h.indexOf(parts[0].take(3))
    }

}
