package com.lipabill.app.data.tickets.smart

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.tickets.BookingConfirmationParser
import com.lipabill.app.data.tickets.ETicketTextParser
import com.lipabill.app.data.tickets.ETicketTextParser.FoundBarcode
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.data.tickets.TicketImport
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * The smart path must save exactly what today's imports save when the person
 * changes nothing on the review screen. Same samples as the existing parser tests.
 */
class SmartPassParityTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 10)
    private val reader = SmartPassReader(NoPassAi, zone)

    private fun smartImport(input: PassInput): TicketImport =
        reader.rulesDraft(input, today).toTicketImport(zone)!!

    /** What the "Event ticket" button saves for the same file today. */
    private fun eventButton(text: String, barcodes: List<FoundBarcode>): TicketImport =
        ETicketTextParser.parse(text, barcodes, kind = TicketDocumentKind.EVENT)

    @Test
    fun madaraka_e_ticket_matches_the_event_button() {
        val ocr = """
            MADARAKA EXPRESS
            Kenya Railways
            Passenger: JANE WAIRIMU
            From: Nairobi Terminus
            To: Mombasa Terminus
            Date: 15 Oct 2026
            Departure: 08:00
            Class: Economy
            Coach: 5
            Seat: 12A
            Booking Ref: KRC9X2P1
        """.trimIndent()
        val codes = listOf(FoundBarcode("KRC9X2P1-QR", TicketBarcodeFormat.QR_CODE))
        assertEquals(eventButton(ocr, codes), smartImport(PassInput(text = ocr, barcodes = codes)))
    }

    @Test
    fun physical_madaraka_ticket_matches_the_event_button() {
        val ocr = """
            0299823
            Sold at Nairobi Terminus
            Nairobi Terminus
            E2 →
            Mombasa Terminus
            09:00
            the 13th of September, 2017
            KSH 700.00
            Seat47
            Coach7
            SecondClass
            Only valid for carriage on the date
            NOT TRANSFERABLE
            Name: JANE DOE
            ID/P NO: 12345678
            SerialN 0908100299823
        """.trimIndent()
        val codes = listOf(FoundBarcode("QR-PAYLOAD-0299823", TicketBarcodeFormat.QR_CODE))
        assertEquals(eventButton(ocr, codes), smartImport(PassInput(text = ocr, barcodes = codes)))
    }

    @Test
    fun booking_ref_fallback_matches_the_event_button() {
        val ocr = """
            Madaraka Express
            Nairobi Terminus to Voi
            Booking reference: AB12CD34
            Date: 01 Dec 2026
            Time: 09:15
        """.trimIndent()
        assertEquals(eventButton(ocr, emptyList()), smartImport(PassInput(text = ocr)))
    }

    @Test
    fun booking_sms_matches_the_sgr_sms_button() {
        val sms = "Booking Confirmed. Ref No: KRCX1Y2Z3. Train: Nairobi to Mombasa E2. " +
            "Date: 20/11/2026. Time: 08:00 AM. Seat: Coach 5, Seat 42. Amount Paid: KES 1,500."
        assertEquals(BookingConfirmationParser.parse(sms), smartImport(PassInput(text = sms)))
    }

    @Test
    fun pkpass_matches_a_shared_pass() {
        val pass = TicketImport(
            title = "Sauti Sol Live",
            venue = "KICC Grounds",
            startsAtMillis = LocalDate.of(2026, 11, 21).atTime(19, 0).atZone(zone).toInstant().toEpochMilli(),
            seatOrTier = "Regular",
            barcodeValue = "EVT-123",
            orderId = "ORD9",
            notes = "Gate B",
            source = TicketSource.PKPASS
        )
        assertEquals(pass, smartImport(PassInput(pkpass = pass)))
    }

    @Test
    fun pkpass_at_midnight_keeps_its_time() {
        val pass = TicketImport(
            title = "All-day pass",
            startsAtMillis = LocalDate.of(2026, 12, 1).atStartOfDay(zone).toInstant().toEpochMilli(),
            barcodeValue = "DAY-1",
            source = TicketSource.PKPASS
        )
        assertEquals(pass, smartImport(PassInput(pkpass = pass)))
    }
}
