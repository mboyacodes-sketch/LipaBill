package com.lipabill.app.data.tickets.smart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BcbpParserTest {

    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun decodes_the_iata_spec_example() {
        // IATA Resolution 792 example, mandatory items only.
        val pass = BcbpParser.parse("M1DESMARAIS/LUC       EABC123 YULFRAAC 0834 326J001A0025 100", today)!!
        assertEquals("Luc Desmarais", pass.passengerName)
        assertEquals("ABC123", pass.bookingRef)
        assertEquals("YUL", pass.origin)
        assertEquals("FRA", pass.destination)
        assertEquals("AC 834", pass.flight)
        assertEquals(LocalDate.of(2026, 11, 22), pass.date)
        assertEquals("Business", pass.cabin)
        assertEquals("1A", pass.seat)
        assertEquals("25", pass.checkInSequence)
    }

    @Test
    fun decodes_a_kenya_airways_pass_and_drops_the_title() {
        val pass = BcbpParser.parse(
            bcbp(name = "KAMAU/JOHN MR", pnr = "XYZ789", from = "NBO", to = "LHR", carrier = "KQ", flight = "0100", julian = "315", cabin = 'Y', seat = "012C"),
            today
        )!!
        assertEquals("John Kamau", pass.passengerName)
        assertEquals("KQ 100", pass.flight)
        assertEquals("NBO", pass.origin)
        assertEquals("LHR", pass.destination)
        assertEquals("12C", pass.seat)
        assertEquals("Economy", pass.cabin)
        assertEquals(LocalDate.of(2026, 11, 11), pass.date)
    }

    @Test
    fun keeps_hyphenated_and_multi_part_given_names() {
        assertEquals("Mary-Anne Wanjiku Otieno", BcbpParser.displayName("OTIENO/MARY-ANNE WANJIKU MRS"))
        assertEquals("Kamau", BcbpParser.displayName("KAMAU"))
    }

    @Test
    fun julian_day_picks_the_year_nearest_today() {
        // 5 Jan seen in late December belongs to next year.
        assertEquals(LocalDate.of(2027, 1, 5), BcbpParser.julianDate(5, LocalDate.of(2026, 12, 28)))
        // 28 Dec seen in early January belongs to last year.
        assertEquals(LocalDate.of(2025, 12, 28), BcbpParser.julianDate(362, LocalDate.of(2026, 1, 3)))
    }

    @Test
    fun day_366_only_exists_in_a_leap_year() {
        assertEquals(LocalDate.of(2028, 12, 31), BcbpParser.julianDate(366, LocalDate.of(2027, 12, 20)))
    }

    @Test
    fun cabin_codes_map_to_plain_names() {
        assertEquals("First", BcbpParser.cabinName('F'))
        assertEquals("Business", BcbpParser.cabinName('C'))
        assertEquals("Premium economy", BcbpParser.cabinName('W'))
        assertEquals("Economy", BcbpParser.cabinName('M'))
    }

    @Test
    fun blank_seat_means_not_yet_assigned() {
        val pass = BcbpParser.parse(bcbp(seat = "    "), today)!!
        assertNull(pass.seat)
    }

    @Test
    fun multi_leg_passes_still_decode_the_first_leg() {
        val raw = bcbp().replaceRange(1, 2, "2")
        assertTrue(BcbpParser.looksLikeBcbp(raw))
        assertNotNull(BcbpParser.parse(raw, today))
    }

    @Test
    fun rejects_things_that_are_not_boarding_passes() {
        assertNull(BcbpParser.parse("https://tickets.example/qr/123", today))
        assertNull(BcbpParser.parse("M1TOO SHORT", today))
        assertNull(BcbpParser.parse(bcbp().replaceRange(0, 1, "X"), today))
        assertFalse(BcbpParser.looksLikeBcbp(null))
    }

    @Test
    fun rejects_bad_airports_dates_and_flights() {
        assertNull(BcbpParser.parse(bcbp(from = "N1O"), today))
        assertNull(BcbpParser.parse(bcbp(julian = "ABC"), today))
        assertNull(BcbpParser.parse(bcbp(julian = "400"), today))
        assertNull(BcbpParser.parse(bcbp(flight = "0000"), today))
        assertNull(BcbpParser.parse(bcbp(pnr = ""), today))
    }

    companion object {
        /** Builds a mandatory-items BCBP string with the exact IATA field widths. */
        fun bcbp(
            name: String = "OTIENO/AMINA MS",
            pnr: String = "PNR123",
            from: String = "NBO",
            to: String = "MBA",
            carrier: String = "KQ",
            flight: String = "0612",
            julian: String = "320",
            cabin: Char = 'Y',
            seat: String = "014A",
            sequence: String = "0042"
        ): String = buildString {
            append("M1")
            append(name.padEnd(20).take(20))
            append('E')
            append(pnr.padEnd(7).take(7))
            append(from.padEnd(3).take(3))
            append(to.padEnd(3).take(3))
            append(carrier.padEnd(3).take(3))
            append(flight.padEnd(5).take(5))
            append(julian.padStart(3, '0').take(3))
            append(cabin)
            append(seat.padEnd(4).take(4))
            append(sequence.padEnd(5).take(5))
            append('1')
            append("00")
        }
    }
}
