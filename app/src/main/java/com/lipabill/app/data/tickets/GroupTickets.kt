package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.withNotesValue

/**
 * One file, several tickets (a group order prints one per page). Every pass shares the event,
 * venue, date and order; each gets its own gate code and a "Ticket: n of N" counter.
 */
object GroupTickets {

    /** Read from the page the main code came from, so they would be wrong on the others. */
    private val PER_HOLDER_NOTES = listOf("Guest", "Attendee", "Name", "Holder", "Passenger", "Seat", "Row")

    /** [main] first, then one draft per extra code. Empty [extras] returns [main] unchanged. */
    fun drafts(main: TicketImport, extras: List<ETicketTextParser.FoundBarcode>): List<TicketImport> {
        if (extras.isEmpty()) return listOf(main)
        val total = extras.size + 1
        val first = main.copy(notes = main.notes.withNotesValue("Ticket", "1 of $total"))
        val others = extras.mapIndexed { i, code ->
            val notes = PER_HOLDER_NOTES.fold(main.notes) { acc, label -> acc.withNotesValue(label, null) }
            main.copy(
                barcodeValue = code.value,
                barcodeFormat = code.format,
                barcodeSpec = code.spec,
                seatOrTier = null,
                notes = notes.withNotesValue("Ticket", "${i + 2} of $total")
            )
        }
        return listOf(first) + others
    }
}
