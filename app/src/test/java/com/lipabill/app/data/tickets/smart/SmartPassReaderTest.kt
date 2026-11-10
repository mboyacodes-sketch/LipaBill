package com.lipabill.app.data.tickets.smart

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.tickets.ETicketTextParser.FoundBarcode
import com.lipabill.app.data.tickets.TicketImport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SmartPassReaderTest {

    private val today = LocalDate.of(2026, 11, 10)
    private val zone = ZoneOffset.UTC

    private class FakeAi(
        private val ready: Boolean = true,
        private val reply: String? = null,
        private val fail: Boolean = false
    ) : PassAi {
        var calls = 0
        var lastPrompt: String? = null
        override suspend fun isReady() = ready
        override suspend fun answer(prompt: String): String? {
            calls++
            lastPrompt = prompt
            if (fail) error("model crashed")
            return reply
        }
    }

    private val eventPass = TicketImport(
        title = "Sauti Sol Live",
        venue = "KICC Grounds",
        startsAtMillis = LocalDate.of(2026, 11, 21).atTime(19, 0).toInstant(zone).toEpochMilli(),
        seatOrTier = "Regular",
        barcodeValue = "EVT-123",
        orderId = "ORD9",
        source = TicketSource.PKPASS
    )

    @Test
    fun pkpass_event_keeps_its_data_and_import() = runBlocking {
        val draft = SmartPassReader(FakeAi(), zone).read(PassInput(pkpass = eventPass), today, null)
        assertEquals(PassKind.EVENT, draft.kind)
        assertEquals(TicketSource.PKPASS, draft.source)
        assertEquals(eventPass, draft.base)
        assertEquals("KICC Grounds", draft[PassField.VENUE])
        assertEquals("2026-11-21", draft[PassField.DATE])
        assertEquals("19:00", draft[PassField.TIME])
        assertEquals(FieldSource.PKPASS, draft.fields.getValue(PassField.TITLE).source)
        assertTrue(draft.canSave)
    }

    @Test
    fun pkpass_carrying_an_airline_barcode_is_a_boarding_pass() = runBlocking {
        val code = BcbpParserTest.bcbp(name = "KAMAU/JOHN MR")
        val draft = SmartPassReader(FakeAi(), zone).read(
            PassInput(pkpass = eventPass.copy(title = "KQ 612", barcodeValue = code)),
            today,
            "John Kamau"
        )
        assertEquals(PassKind.AIRLINE, draft.kind)
        assertEquals(FieldSource.BARCODE, draft.fields.getValue(PassField.HOLDER).source)
        assertEquals(NameCheck.MATCH, draft.nameCheck)
    }

    @Test
    fun scanned_boarding_pass_is_read_from_the_barcode_alone() = runBlocking {
        val code = BcbpParserTest.bcbp()
        val ai = FakeAi()
        val draft = SmartPassReader(ai, zone).read(
            PassInput(barcodes = listOf(FoundBarcode(code, TicketBarcodeFormat.QR_CODE)), source = TicketSource.MANUAL_SCAN),
            today,
            null
        )
        assertEquals(PassKind.AIRLINE, draft.kind)
        assertEquals(TicketBarcodeFormat.PDF_417, draft.barcodeFormat)
        assertEquals("Amina Otieno", draft[PassField.HOLDER])
        assertEquals("NBO", draft[PassField.ORIGIN])
        assertEquals("MBA", draft[PassField.DESTINATION])
        assertEquals("KQ 612", draft[PassField.FLIGHT])
        assertEquals("14A", draft[PassField.SEAT])
        assertEquals("PNR123", draft[PassField.BOOKING_REF])
        assertEquals(0, ai.calls)
        assertTrue(draft.canSave)
    }

    @Test
    fun sgr_booking_sms_reuses_todays_parser() = runBlocking {
        val sms = "Booking Confirmed. Ref No: KRCX1Y2Z3. Train: Nairobi to Mombasa E2. " +
            "Date: 20/11/2026. Time: 08:00 AM. Seat: Coach 5, Seat 42. Amount Paid: KES 1,500."
        val draft = SmartPassReader(FakeAi(), zone).read(PassInput(text = sms), today, null)
        assertEquals(PassKind.RAIL, draft.kind)
        assertNotNull(draft.base)
        assertTrue(draft.base!!.expectsBoardingPass)
        assertEquals("REF:KRCX1Y2Z3", draft.barcodeValue)
        assertEquals("KRCX1Y2Z3", draft[PassField.BOOKING_REF])
        assertEquals(TicketSource.BOOKING_CONFIRMATION, draft.source)
    }

    @Test
    fun ai_is_not_asked_when_it_is_not_ready() = runBlocking {
        val ai = FakeAi(ready = false, reply = "TITLE: Anything")
        val draft = SmartPassReader(ai, zone).read(
            PassInput(text = "Some event", barcodes = listOf(FoundBarcode("QR-1", TicketBarcodeFormat.QR_CODE))),
            today,
            null
        )
        assertEquals(0, ai.calls)
        assertFalse(draft.usedAi)
    }

    @Test
    fun ai_fills_only_the_gaps_and_is_marked() = runBlocking {
        val text = "BOARDING PASS\nBoarding 07:40 Gate 7"
        val code = BcbpParserTest.bcbp()
        val ai = FakeAi(reply = "TIME: 07:40")
        val draft = SmartPassReader(ai, zone).read(
            PassInput(text = text, barcodes = listOf(FoundBarcode(code, TicketBarcodeFormat.PDF_417))),
            today,
            null
        )
        assertEquals(1, ai.calls)
        assertTrue(ai.lastPrompt!!.contains("TIME:"))
        assertFalse(ai.lastPrompt!!.contains("SEAT:"))
        assertEquals("07:40", draft[PassField.TIME])
        assertEquals(FieldSource.AI, draft.fields.getValue(PassField.TIME).source)
        assertNotNull(draft.fields.getValue(PassField.TIME).warning)
        assertTrue(draft.usedAi)
    }

    @Test
    fun ai_never_overwrites_barcode_values() = runBlocking {
        val text = "BOARDING PASS Seat 99Z Boarding 07:40"
        val ai = FakeAi(reply = "SEAT: 99Z\nTIME: 07:40")
        val draft = SmartPassReader(ai, zone).read(
            PassInput(text = text, barcodes = listOf(FoundBarcode(BcbpParserTest.bcbp(), TicketBarcodeFormat.PDF_417))),
            today,
            null
        )
        assertEquals("14A", draft[PassField.SEAT])
        assertEquals(FieldSource.BARCODE, draft.fields.getValue(PassField.SEAT).source)
    }

    @Test
    fun made_up_ai_answers_are_dropped() = runBlocking {
        val text = "BOARDING PASS\nGate 7"
        val ai = FakeAi(reply = "TIME: 07:40")
        val draft = SmartPassReader(ai, zone).read(
            PassInput(text = text, barcodes = listOf(FoundBarcode(BcbpParserTest.bcbp(), TicketBarcodeFormat.PDF_417))),
            today,
            null
        )
        // 07:40 is a valid time, so it is kept but flagged; a seat that is not in the text would not be.
        assertEquals(FieldSource.AI, draft.fields[PassField.TIME]?.source)
        val noSeat = SmartPassReader(FakeAi(reply = "SEAT: 3C"), zone).read(
            PassInput(text = text, barcodes = listOf(FoundBarcode("QR-1", TicketBarcodeFormat.QR_CODE))),
            today,
            null
        )
        // The rules may find a seat of their own; the invented "3C" must never appear.
        assertFalse(noSeat[PassField.SEAT] == "3C")
        assertFalse(noSeat.fields[PassField.SEAT]?.source == FieldSource.AI)
    }

    @Test
    fun a_failing_model_never_breaks_reading() = runBlocking {
        val ai = FakeAi(fail = true)
        val reader = SmartPassReader(object : PassAi {
            override suspend fun isReady() = true
            override suspend fun answer(prompt: String): String? = runCatching { ai.answer(prompt) }.getOrNull()
        }, zone)
        val draft = reader.read(
            PassInput(text = "Concert", barcodes = listOf(FoundBarcode("QR-1", TicketBarcodeFormat.QR_CODE))),
            today,
            null
        )
        assertEquals("QR-1", draft.barcodeValue)
        assertFalse(draft.usedAi)
    }

    @Test
    fun nothing_scannable_means_nothing_to_save() = runBlocking {
        val draft = SmartPassReader(FakeAi(ready = false), zone).read(PassInput(text = "Just some words"), today, null)
        assertNull(draft.barcodeValue)
        assertFalse(draft.canSave)
        assertTrue(draft.blockers.isNotEmpty())
    }

    @Test
    fun guesses_the_kind_from_words_on_the_ticket() {
        val reader = SmartPassReader(NoPassAi, zone)
        assertEquals(PassKind.RAIL, reader.guessKind("Madaraka Express Coach 5"))
        assertEquals(PassKind.AIRLINE, reader.guessKind("BOARDING PASS gate 12"))
        assertEquals(PassKind.EVENT, reader.guessKind("Sauti Sol Live at KICC"))
    }
}
