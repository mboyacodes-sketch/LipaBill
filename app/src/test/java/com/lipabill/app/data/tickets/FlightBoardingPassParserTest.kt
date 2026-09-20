package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FlightBoardingPassParserTest {

    /**
     * OCR-ish text matching the Kenya Airways physical boarding pass sample
     * (MATHENGE/LILIAN MU · KQ491 · ZNZ→NBO · seat 14J).
     */
    private val kqBoardingPass = """
        Kenya Airways The Pride of Africa
        BOARDING PASS
        Flight No. KQ491
        Boarding Time 09:25
        GATE 5
        SEAT 14J
        ZONE: B
        MATHENGE/LILIAN MU
        FROM ZANZIBAR/ZNZ
        TO NAIROBI/NBO
        CHECK MONITORS FOR YOUR GATE NUMBER. GATE CLOSES 30 MINUTES TO DEPARTURE.
        Class N
        Date 23JUN
        Agent ID S917169
        ETKT 706971292272501
        Security No. 0069
        YOU ARE FLYING WITH : KENYA AIRWAYS

        ECONOMY Cabin M
        SEAT 14J
        MATHENGE/LILIAN MU
        Flight KQ 491
        Date 23JUN
        FROM ZANZIBAR/ZNZ
        TO NAIROBI/NBO
        10:10
        Security No 0069
        ETKT 706971292272501
    """.trimIndent()

    // Minimal BCBP-like payload (IATA M1…) — length padded for parser
    private val bcbpPayload =
        "M1MATHENGE/LILIAN MU   ABCDEF ZNZNBOKQ 0491 174N014J0001 100"

    @Test
    fun detectsBoardingPass() {
        assertTrue(
            FlightBoardingPassParser.looksLikeBoardingPass(
                kqBoardingPass,
                ETicketTextParser.FoundBarcode(bcbpPayload, TicketBarcodeFormat.PDF_417)
            )
        )
        assertFalse(
            FlightBoardingPassParser.looksLikeBoardingPass(
                "Madaraka Express Coach 5 Seat 42",
                null
            )
        )
    }

    @Test
    fun parsesKenyaAirwaysBoardingPass() {
        val barcode = ETicketTextParser.FoundBarcode(
            bcbpPayload,
            TicketBarcodeFormat.PDF_417
        )
        val draft = FlightBoardingPassParser.parse(kqBoardingPass, barcode)

        assertEquals("KQ491 · ZNZ → NBO", draft.title)
        assertEquals("ZNZ → NBO", draft.venue)
        assertEquals(bcbpPayload.trim().uppercase(), draft.barcodeValue.trim().uppercase())
        assertEquals(TicketBarcodeFormat.PDF_417, draft.barcodeFormat)
        assertTrue(draft.hasBoardingPass)
        assertTrue(draft.expectsBoardingPass)
        assertEquals(TicketSource.E_TICKET, draft.source)
        assertEquals("706971292272501", draft.orderId)

        assertTrue(draft.seatOrTier!!.contains("Seat 14J"))
        assertTrue(draft.seatOrTier!!.contains("Gate 5"))
        assertTrue(draft.seatOrTier!!.contains("Zone B"))

        assertTrue(draft.notes!!.contains("Airline: Kenya Airways"))
        assertTrue(
            draft.notes!!.contains("Passenger: MATHENGE/LILIAN MU") ||
                draft.notes!!.contains("Passenger: MATHENGE, LILIAN MU")
        )
        assertTrue(draft.notes!!.contains("Origin: ZANZIBAR (ZNZ)"))
        assertTrue(draft.notes!!.contains("Destination: NAIROBI (NBO)"))
        assertTrue(draft.notes!!.contains("Agent: S917169"))
        assertTrue(draft.notes!!.contains("Boarding: 09:25"))
        assertTrue(draft.notes!!.contains("Departure time: 10:10"))
        assertTrue(draft.notes!!.contains("Security: 0069"))
        assertTrue(draft.notes!!.contains("Ticket no: 706971292272501"))

        val cal = Calendar.getInstance().apply { timeInMillis = draft.startsAtMillis!! }
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH))
        assertEquals(23, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(10, cal.get(Calendar.MINUTE))
    }

    @Test
    fun ignoresToDepartureInstructionAndBadAgent() {
        // Realistic OCR noise: instruction line before destination, bare "Agent" noise
        val noisy = """
            BOARDING PASS
            Flight KQ491
            FROM ZANZIBAR/ZNZ
            CHECK MONITORS FOR YOUR GATE NUMBER. GATE CLOSES 30 MINUTES TO DEPARTURE.
            TO NAIROBI/NBO
            Class N
            Date 23JUN
            Agent ID S917169
            ETKT 706971292272501
            YOU ARE FLYING WITH : KENYA AIRWAYS
        """.trimIndent()
        val draft = FlightBoardingPassParser.parse(
            noisy,
            ETicketTextParser.FoundBarcode("GATE", TicketBarcodeFormat.PDF_417)
        )
        assertEquals("ZNZ → NBO", draft.venue)
        assertTrue(draft.notes!!.contains("Destination: NAIROBI (NBO)"))
        assertFalse(draft.notes!!.contains("DEP"))
        assertFalse(draft.notes!!.contains("DEPARTURE"))
        assertTrue(draft.notes!!.contains("Agent: S917169"))
        assertFalse(draft.notes!!.contains("Agent: KENYA"))
        assertFalse(draft.notes!!.contains("Agent: ETKT"))
    }

    @Test
    fun parsesHassanMombasaBoardingPass() {
        // Layout from WhatsApp Image 2026-09-19 at 14.52.51 — AGENT (not Agent ID),
        // spaces around slash, stub may use parentheses.
        val ocr = """
            Kenya Airways
            BOARDING PASS
            Flight No. KQ609
            Boarding Time 20:25
            GATE 04
            SEAT 22A
            ZONE: C
            NAME HASSAN / A
            FROM: MOMBASA / MBA
            CHECK MONITORS FOR YOUR GATE NUMBER, GATE CLOSES 30 MINUTES TO DEPARTURE.
            TO: NAIROBI / NBO
            Booked Class: U
            Date: 02OCT
            AGENT: C2797
            YOU ARE FLYING WITH : KENYA AIRWAYS
            ETKT 706101257613002
            Security No. 0092

            SEAT 22A
            M ECONOMY
            HASSAN / A
            Flight KQ 609
            Date 02OCT
            FROM MOMBASA(MBA)
            TO NAIROBI(NBO)
            Departure Time 20:55
            Security No 0092
            ETKT 706101257613002
        """.trimIndent()

        val draft = FlightBoardingPassParser.parse(
            ocr,
            ETicketTextParser.FoundBarcode("PDF417-KQ609", TicketBarcodeFormat.PDF_417)
        )

        assertEquals("KQ609 · MBA → NBO", draft.title)
        assertEquals("MBA → NBO", draft.venue)
        assertEquals(TicketBarcodeFormat.PDF_417, draft.barcodeFormat)
        assertTrue(draft.barcodeValue.startsWith("M1"))
        assertTrue(BcbpParser.parse(draft.barcodeValue) != null)
        assertTrue(draft.notes!!.contains("Passenger: HASSAN/A") || draft.notes!!.contains("HASSAN / A"))
        assertTrue(draft.notes!!.contains("Origin: MOMBASA (MBA)"))
        assertTrue(draft.notes!!.contains("Destination: NAIROBI (NBO)"))
        assertTrue(draft.notes!!.contains("Agent: C2797"))
        assertFalse(draft.notes!!.contains("DEP"))
        assertTrue(draft.seatOrTier!!.contains("Seat 22A"))
        assertTrue(draft.seatOrTier!!.contains("Gate 04") || draft.seatOrTier!!.contains("Gate 4"))
        assertTrue(draft.notes!!.contains("Boarding: 20:25"))
        assertTrue(draft.notes!!.contains("Departure time: 20:55"))
        assertEquals("706101257613002", draft.orderId)
    }

    @Test
    fun buildsAztecBcbpWithoutScannedBarcode() {
        val draft = FlightBoardingPassParser.parse(
            """
                BOARDING PASS
                Flight No. KQ609
                SEAT 22A
                FROM MOMBASA/MBA
                TO NAIROBI/NBO
                Date 02OCT
                NAME HASSAN/A
                ETKT 706101257613002
            """.trimIndent(),
            barcode = null
        )
        assertEquals(TicketBarcodeFormat.PDF_417, draft.barcodeFormat)
        assertTrue(draft.barcodeValue.startsWith("M1"))
        val parsed = BcbpParser.parse(draft.barcodeValue)!!
        assertEquals("MBA", parsed.from)
        assertEquals("NBO", parsed.to)
        assertEquals("KQ609", parsed.flightNumber)
        assertTrue(parsed.seat!!.contains("22A") || parsed.seat == "22A")
    }

    @Test
    fun prefersPdf417BcbpBarcode() {
        val barcodes = listOf(
            ETicketTextParser.FoundBarcode("https://noise.example", TicketBarcodeFormat.QR_CODE),
            ETicketTextParser.FoundBarcode(bcbpPayload, TicketBarcodeFormat.PDF_417)
        )
        val pick = FlightBoardingPassParser.pickBoardingBarcode(barcodes)!!
        assertEquals(TicketBarcodeFormat.PDF_417, pick.format)
        assertTrue(pick.value.startsWith("M1"))
    }

    @Test
    fun eticketTextParserRoutesBoardingPass() {
        val draft = ETicketTextParser.parse(
            kqBoardingPass,
            listOf(
                ETicketTextParser.FoundBarcode(
                    "GATE-PDF417-PAYLOAD",
                    TicketBarcodeFormat.PDF_417
                )
            )
        )
        assertTrue(draft.hasBoardingPass)
        assertTrue(draft.title.contains("KQ491"))
        assertEquals("ZNZ → NBO", draft.venue)
    }

    @Test
    fun forcedBoardingKindWorksWithoutBarcode() {
        val draft = ETicketTextParser.parse(
            ocrText = """
                BOARDING PASS
                Flight No. KQ609
                Boarding Time 20:25
                GATE 04
                SEAT 22A
                ZONE: C
                NAME HASSAN / A
                FROM: MOMBASA / MBA
                TO: NAIROBI / NBO
                Booked Class: U
                Date: 02OCT
                AGENT: C2797
                ETKT 706101257613002
                Security No. 0092
                Departure Time 20:55
            """.trimIndent(),
            barcodes = emptyList(),
            kind = TicketDocumentKind.BOARDING_PASS
        )
        assertTrue(draft.hasBoardingPass)
        assertEquals("KQ609 · MBA → NBO", draft.title)
        assertEquals(TicketBarcodeFormat.PDF_417, draft.barcodeFormat)
        assertTrue(draft.barcodeValue.startsWith("M1"))
        assertTrue(draft.notes!!.contains("Destination: NAIROBI (NBO)"))
        assertTrue(draft.notes!!.contains("Agent: C2797"))
        assertTrue(draft.seatOrTier!!.contains("Gate 04") || draft.seatOrTier!!.contains("Gate 4"))
    }
}
