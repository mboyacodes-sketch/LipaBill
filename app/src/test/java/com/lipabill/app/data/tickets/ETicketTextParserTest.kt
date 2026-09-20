package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketBarcodeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ETicketTextParserTest {

    @Test
    fun parsesSgrMadarakaTicket() {
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
        val draft = ETicketTextParser.parse(
            ocr,
            listOf(ETicketTextParser.FoundBarcode("KRC9X2P1-QR", TicketBarcodeFormat.QR_CODE))
        )
        assertTrue(draft.title.contains("SGR"))
        assertTrue(draft.venue!!.contains("Nairobi"))
        assertTrue(draft.venue!!.contains("Mombasa"))
        assertEquals("KRC9X2P1-QR", draft.barcodeValue)
        assertNotNull(draft.startsAtMillis)
        assertTrue(draft.seatOrTier!!.contains("Coach 5"))
        assertTrue(draft.seatOrTier!!.contains("Seat 12A"))
    }

    @Test
    fun parsesPhysicalMadarakaTicketLayout() {
        // OCR-ish dump of the blue cardboard Madaraka Express ticket sample
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
        val draft = ETicketTextParser.parse(
            ocr,
            listOf(ETicketTextParser.FoundBarcode("QR-PAYLOAD-0299823", TicketBarcodeFormat.QR_CODE))
        )
        assertTrue(draft.title.contains("SGR"))
        assertTrue(draft.title.contains("E2"))
        assertEquals("Nairobi Terminus → Mombasa Terminus", draft.venue)
        assertEquals("0299823", draft.orderId)
        assertTrue(draft.seatOrTier!!.contains("Second Class"))
        assertTrue(draft.seatOrTier!!.contains("Coach 7"))
        assertTrue(draft.seatOrTier!!.contains("Seat 47"))
        assertTrue(draft.notes!!.contains("KSH 700"))
        assertTrue(draft.notes!!.contains("Serial: 0908100299823"))
        assertTrue(draft.notes!!.contains("Passenger: JANE DOE"))

        val zoned = Instant.ofEpochMilli(draft.startsAtMillis!!)
            .atZone(ZoneId.systemDefault())
        assertEquals(LocalDate.of(2017, 9, 13), zoned.toLocalDate())
        assertEquals(LocalTime.of(9, 0), zoned.toLocalTime())
    }

    @Test
    fun parsesFlightFromOcrAndPdf417() {
        val ocr = """
            BOARDING PASS
            Passenger Name: DOE/JOHN
            Flight KQ101
            From NBO
            To MBA
            Date 20 Nov 2026
            Departure 14:30
            Seat 14C
            Gate A5
            PNR ABCDEF
        """.trimIndent()
        val draft = ETicketTextParser.parse(
            ocr,
            listOf(
                ETicketTextParser.FoundBarcode(
                    "PDF417PAYLOAD-FLIGHT",
                    TicketBarcodeFormat.PDF_417
                )
            )
        )
        assertTrue(draft.title.contains("KQ101") || draft.title.contains("Flight"))
        assertTrue(draft.hasBoardingPass)
        assertEquals(TicketBarcodeFormat.PDF_417, draft.barcodeFormat)
        assertTrue(draft.barcodeValue.startsWith("M1"))
        assertTrue(BcbpParser.parse(draft.barcodeValue) != null)
    }

    @Test
    fun fallsBackToBookingRefWhenNoBarcode() {
        val ocr = """
            Madaraka Express
            Nairobi Terminus to Voi
            Booking reference: AB12CD34
            Date: 01 Dec 2026
            Time: 09:15
        """.trimIndent()
        val draft = ETicketTextParser.parse(ocr, emptyList())
        assertEquals("AB12CD34", draft.barcodeValue)
        assertTrue(draft.title.contains("SGR"))
    }

    @Test
    fun parsesRealVisionOcrFromPhysicalTicket() {
        // Exact OCR from the sample Madaraka Express photo (Vision / ML Kit style)
        val ocr = """
            Ticket No
            0299823
            Nairobi
            E2
            Terminus
            DepartureTime: 09:00
            the 13th of September, 2017
            KSH 700. 00
            Only valid for carriage on the date
            NOT TRANSFERABLE
            Name:
            ID/P NO:
            Serial
            Sold at Nairobi Terminus
            Mombasa
            Terminus
            Seat47 Coach7
            SecondClass
            0908100299823
        """.trimIndent()
        val draft = ETicketTextParser.parse(
            ocr,
            listOf(ETicketTextParser.FoundBarcode("QR-0299823", TicketBarcodeFormat.QR_CODE))
        )
        assertTrue(draft.title.contains("SGR"))
        assertTrue(draft.title.contains("E2"))
        assertEquals("Nairobi Terminus → Mombasa Terminus", draft.venue)
        assertEquals("0299823", draft.orderId)
        assertTrue(draft.seatOrTier!!.contains("Coach 7"))
        assertTrue(draft.seatOrTier!!.contains("Seat 47"))
        assertTrue(draft.seatOrTier!!.contains("Second Class"))
        assertNotNull(draft.startsAtMillis)
        val zoned = Instant.ofEpochMilli(draft.startsAtMillis!!)
            .atZone(ZoneId.systemDefault())
        assertEquals(LocalDate.of(2017, 9, 13), zoned.toLocalDate())
        assertEquals(LocalTime.of(9, 0), zoned.toLocalTime())
        assertTrue(draft.notes!!.contains("700"))
        assertTrue(draft.notes!!.contains("0908100299823"))
    }

    @Test
    fun soldAtStationWinsOverReversedOcrOrder() {
        // OCR listed Mombasa first, but ticket was sold at Nairobi (departure).
        val ocr = """
            Ticket No: 0299823
            Mombasa Terminus
            E2
            Nairobi Terminus
            Departure: 09:00
            the 13th of September, 2017
            KSH 700.00
            Seat47 Coach7 SecondClass
            Sold at Nairobi Terminus
            NOT TRANSFERABLE
        """.trimIndent()
        val draft = ETicketTextParser.parse(
            ocr,
            listOf(ETicketTextParser.FoundBarcode("QR", TicketBarcodeFormat.QR_CODE))
        )
        assertEquals("Nairobi Terminus → Mombasa Terminus", draft.venue)
        assertTrue(draft.notes!!.contains("Origin: Nairobi Terminus"))
        assertTrue(draft.notes!!.contains("Destination: Mombasa Terminus"))
    }

    @Test
    fun spatialHintsPreferLeftAsOrigin() {
        val ocr = """
            Ticket No: 0299823
            Mombasa Terminus
            Nairobi Terminus
            E2
            Departure: 09:00
            the 13th of September, 2017
            Seat47 Coach7 SecondClass
            NOT TRANSFERABLE
        """.trimIndent()
        val draft = ETicketTextParser.parse(
            ocr,
            listOf(ETicketTextParser.FoundBarcode("QR", TicketBarcodeFormat.QR_CODE)),
            stationHints = listOf(
                ETicketTextParser.StationHint("Mombasa Terminus", centerX = 800),
                ETicketTextParser.StationHint("Nairobi Terminus", centerX = 120)
            )
        )
        assertEquals("Nairobi Terminus → Mombasa Terminus", draft.venue)
    }
}