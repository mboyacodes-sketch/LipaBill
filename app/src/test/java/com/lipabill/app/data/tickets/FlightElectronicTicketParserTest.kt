package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FlightElectronicTicketParserTest {

    /**
     * OCR-ish text matching the Kenya Airways Electronic Ticket Receipt sample
     * (Mathenge Lilian Muthoni · NBO↔MBA · Z4H6YX).
     */
    private val kqReceipt = """
        Kenya Airways The Pride of Africa
        ELECTRONIC TICKET RECEIPT
        Passenger Name: Mathenge Lilian Muthoni (ADT)
        Booking Ref: Z4H6YX
        Ticket Number: 706 2308027991
        Issuing Office: KENYA AIRWAYS JOMO KENYATTA APT SALES, JKIA TERMINAL 1D, NAIROBI
        Telephone: +2540711024747 / +2540734104747
        Issuing Date: 20Mar2026

        ITINERARY
        From: NAIROBI JOMO KENYATTA INTL Terminal: 1D
        To: MOMBASA MOI INTL Terminal: 1
        Flight: KQ602
        Departure: 07:40 20Mar2026
        Arrival: 08:40 20Mar2026
        Class: K Status: OK
        Baggage: 1PC
        Fare Basis: KSFKE
        NVA: 20Aug2026
        Duration: 01:00
        Operated by: KENYA AIRWAYS

        From: MOMBASA MOI INTL Terminal: 1
        To: NAIROBI JOMO KENYATTA INTL Terminal: 1D
        Flight: KQ619
        Departure: 22:35 22Mar2026
        Arrival: 23:35 22Mar2026
        Class: H Status: OK
        Baggage: 1PC
        Fare Basis: HSFKE
        Duration: 01:00
        Operated by: KENYA AIRWAYS

        Fare Calculation: NBO KQ MBA Q5.00 96.00KQ NBO Q5.00 90.50USD196.50END
        Fare: USD 197.00
        Equiv Fare Amount: KES 25,710
        Tax: KES 1200KE KES 700OE
        Carrier Imposed Fees: KES 7050YR
        Total Amount: KES 34,660
        Form of Payment: CC VI XXXXXXXXXXXX0156
        Endorsements: NONENDO/NON TRANSFERABLE RESTRICTION APPLY
    """.trimIndent()

    @Test
    fun detectsElectronicTicketReceipt() {
        assertTrue(FlightElectronicTicketParser.looksLikeElectronicTicketReceipt(kqReceipt))
        assertFalse(
            FlightElectronicTicketParser.looksLikeElectronicTicketReceipt(
                "Madaraka Express Nairobi Terminus Seat 42"
            )
        )
    }

    @Test
    fun parsesKenyaAirwaysReceipt() {
        val draft = FlightElectronicTicketParser.parse(kqReceipt)

        assertEquals("Z4H6YX", draft.orderId)
        assertEquals("REF:Z4H6YX", draft.barcodeValue)
        assertEquals(TicketSource.BOOKING_CONFIRMATION, draft.source)
        assertFalse(draft.hasBoardingPass)

        assertTrue(draft.title.contains("KQ602"))
        assertTrue(draft.title.contains("NBO") || draft.venue!!.contains("NBO"))
        assertEquals("NBO → MBA", draft.venue)

        assertTrue(draft.seatOrTier!!.contains("Class K"))
        assertTrue(draft.seatOrTier!!.contains("1PC"))

        assertTrue(draft.notes!!.contains("Passenger: Mathenge Lilian Muthoni"))
        assertTrue(draft.notes!!.contains("Ticket no: 706 2308027991"))
        assertTrue(draft.notes!!.contains("Airline: Kenya Airways"))
        assertTrue(draft.notes!!.contains("Doc: Receipt"))
        assertTrue(draft.notes!!.contains("Trip: Return"))
        assertTrue(draft.notes!!.contains("Return:") && draft.notes!!.contains("KQ619"))
        assertTrue(draft.notes!!.contains("Total: KES 34,660"))
        assertTrue(draft.notes!!.contains("Base fare: USD 197.00"))
        assertTrue(draft.notes!!.contains("Dep terminal: 1D"))
        assertFalse(draft.notes!!.contains("Leg 2:"))
        assertTrue(draft.notes!!.contains("KQ619"))

        assertEquals(
            FlightElectronicTicketParser.DocumentKind.RECEIPT,
            FlightElectronicTicketParser.detectDocumentKind(kqReceipt)
        )
        assertEquals(
            FlightElectronicTicketParser.TripType.RETURN,
            FlightElectronicTicketParser.detectTripType(
                kqReceipt,
                FlightElectronicTicketParser.parseSegments(kqReceipt)
            )
        )

        val cal = Calendar.getInstance().apply { timeInMillis = draft.startsAtMillis!! }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(40, cal.get(Calendar.MINUTE))
    }

    @Test
    fun eticketTextParserRoutesReceiptWithoutBarcode() {
        val draft = ETicketTextParser.parse(kqReceipt, emptyList())
        assertEquals("Z4H6YX", draft.orderId)
        assertFalse(draft.hasBoardingPass)
    }

    @Test
    fun parsesTwoSegments() {
        val segments = FlightElectronicTicketParser.parseSegments(kqReceipt)
        assertEquals(2, segments.size)
        assertEquals("KQ602", segments[0].flightNumber)
        assertEquals("KQ619", segments[1].flightNumber)
        assertEquals("K", segments[0].bookingClass)
        assertEquals("H", segments[1].bookingClass)
    }

    private val fly540Itinerary = """
        540 Your Local Airline
        Itinerary
        Booking Reference ADPCC2

        Passenger Email Phone Flight Ticket Numbers Seat
        MR BERRY MBOYA MBOYABERRY@GMAIL.COM 254101490941 540 2302443546/01 540 2302443546/02 5A

        Departing
        Date Flight Depart Arrive Cabin Status
        25 Sep 2020 5H0417 Nairobi Int [JKIA] 06:30 Mombasa 07:40 Economy Confirmed

        Rules Flight 1
        This ticket is valid for 6 months. Non-refundable. Baggage 20kg hold 5kg hand.

        Returning
        Date Flight Depart Arrive Cabin Status
        27 Sep 2020 5H0406 Mombasa 18:20 Nairobi Int [JKIA] 19:30 Economy Confirmed

        Rules Flight 2
        Change fee K.Shs. 1,000.

        Fare 9,880.00 KES
        Total Taxes 1,200.00 KES
        Total Fare 11,080.00 KES
        Payment Paid Invoice
    """.trimIndent()

    @Test
    fun detectsFly540Itinerary() {
        assertTrue(FlightElectronicTicketParser.looksLikeElectronicTicketReceipt(fly540Itinerary))
    }

    @Test
    fun parsesFly540Itinerary() {
        val draft = FlightElectronicTicketParser.parse(fly540Itinerary)

        assertEquals("ADPCC2", draft.orderId)
        assertEquals("REF:ADPCC2", draft.barcodeValue)
        assertFalse(draft.hasBoardingPass)
        assertEquals(TicketSource.BOOKING_CONFIRMATION, draft.source)

        assertTrue(draft.title.contains("5H0417"))
        assertEquals("NBO → MBA", draft.venue)
        assertTrue(draft.seatOrTier!!.contains("Seat 5A"))
        assertTrue(draft.seatOrTier!!.contains("Economy"))

        assertTrue(draft.notes!!.contains("Airline: Fly540"))
        assertTrue(draft.notes!!.contains("Doc: Itinerary"))
        assertTrue(draft.notes!!.contains("Trip: Return"))
        assertTrue(draft.notes!!.contains("Return:") && draft.notes!!.contains("5H0406"))
        assertTrue(draft.notes!!.contains("Passenger: MR BERRY MBOYA"))
        assertFalse(draft.notes!!.contains("Passenger: MR BERRY MBOYA MBOYABERRY"))
        assertFalse(draft.notes!!.orEmpty().substringAfter("Passenger:").substringBefore(" · ")
            .contains("@", ignoreCase = true))
        assertTrue(draft.notes!!.contains("Ticket no: 540 2302443546/01"))
        assertTrue(draft.notes!!.contains("Total: KES 11,080.00") || draft.notes!!.contains("11,080"))
        assertFalse(draft.notes!!.contains("Leg 2:"))
        assertTrue(draft.notes!!.contains("Email: MBOYABERRY@GMAIL.COM"))

        assertEquals(
            FlightElectronicTicketParser.DocumentKind.ITINERARY,
            FlightElectronicTicketParser.detectDocumentKind(fly540Itinerary)
        )

        val cal = Calendar.getInstance().apply { timeInMillis = draft.startsAtMillis!! }
        assertEquals(2020, cal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, cal.get(Calendar.MONTH))
        assertEquals(25, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(6, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun eticketTextParserRoutesFly540WithoutBarcode() {
        val draft = ETicketTextParser.parse(fly540Itinerary, emptyList())
        assertEquals("ADPCC2", draft.orderId)
        assertFalse(draft.hasBoardingPass)
    }

    /**
     * Labels/fields in a scrambled order (OCR column shuffle) — still extract core fields.
     */
    @Test
    fun parsesScrambledLabelOrder() {
        val scrambled = """
            Total Amount KES 12,500
            Class Y
            Email jane.doe@example.com
            Flight KQ100
            To: MOMBASA MOI INTL
            Booking Ref ABC123
            Passenger Name: Jane Doe (ADT)
            From: NAIROBI JOMO KENYATTA INTL
            Ticket Number: 706 1111222233
            Departure: 09:15 15Jun2026
            Kenya Airways
            Seat 12C
            Terminal: 1D
        """.trimIndent()

        val draft = FlightElectronicTicketParser.parse(scrambled)
        assertEquals("ABC123", draft.orderId)
        assertTrue(draft.title.contains("KQ100"))
        assertEquals("NBO → MBA", draft.venue)
        assertTrue(draft.notes!!.contains("Passenger: Jane Doe"))
        assertTrue(draft.notes!!.contains("Ticket no: 706 1111222233"))
        assertTrue(draft.notes!!.contains("Total: KES 12,500"))
        assertTrue(draft.seatOrTier!!.contains("Seat 12C"))
        assertTrue(draft.notes!!.contains("Email: jane.doe@example.com"))
        assertTrue(draft.notes!!.contains("Doc: Receipt") || draft.notes!!.contains("Doc: Itinerary"))
        assertTrue(draft.notes!!.contains("Trip: One-way"))
    }
}
