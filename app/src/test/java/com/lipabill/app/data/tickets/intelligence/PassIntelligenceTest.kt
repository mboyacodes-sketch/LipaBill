package com.lipabill.app.data.tickets.intelligence

import com.lipabill.app.data.model.TicketSource
import com.lipabill.app.data.model.TicketStatus
import com.lipabill.app.data.tickets.EventTicketSms
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.data.tickets.TicketImport
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample text only — no real tickets, names or references. */
class PassIntelligenceTest {

    private val now = 1_800_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private val eventText = """
        SAMPLE FESTIVAL 2026
        Venue: Sample Grounds, Nairobi
        Gate opens 4:00 PM · Admission: Regular
        Section B Row 4
    """.trimIndent()

    private val railText = """
        Madaraka Express · SGR
        Nairobi Terminus to Mombasa Terminus
        Economy Class Coach 5
    """.trimIndent()

    private val flightText = """
        Sample Airways electronic ticket itinerary
        Flight SA 100 departure 08:30 arrival 09:45
        Airport: NBO · Cabin: Economy · Boarding pass at check-in · PNR SAMPLE
    """.trimIndent()

    private fun draft(
        code: String = "SAMPLE-CODE-1",
        title: String = "Sample Festival",
        venue: String? = "Sample Grounds",
        startsAt: Long? = now + 10 * day,
        notes: String? = null,
        source: TicketSource = TicketSource.E_TICKET
    ) = TicketImport(
        title = title,
        venue = venue,
        startsAtMillis = startsAt,
        barcodeValue = code,
        notes = notes,
        source = source
    )

    @Test
    fun event_text_classifies_as_event() {
        val result = DocumentClassifier.classify(eventText, hasBarcode = true)
        assertEquals(PassKind.EVENT, result.kind)
        assertTrue(result.confidence >= ConfidencePolicy.CLASSIFY_CONFIRM_BELOW)
        assertFalse(result.needsUserConfirmation)
    }

    @Test
    fun rail_text_classifies_as_rail() {
        assertEquals(PassKind.RAIL, DocumentClassifier.classify(railText, hasBarcode = true).kind)
    }

    @Test
    fun flights_are_recognised_and_turned_away() {
        val result = DocumentClassifier.classify(flightText, hasBarcode = true, hint = TicketDocumentKind.EVENT)
        assertEquals(PassKind.OTHER, result.kind)
        assertTrue(PassValidator.validate(draft(), result.kind).contains(PassIssue.UNSUPPORTED_KIND))
    }

    @Test
    fun the_word_ticket_alone_does_not_classify() {
        val result = DocumentClassifier.classify("Your ticket. Ticket number 12345. Thank you.", hasBarcode = false)
        assertEquals(PassKind.OTHER, result.kind)
        assertTrue(result.needsUserConfirmation)
    }

    @Test
    fun the_kind_picked_when_adding_wins_on_weak_text() {
        val result = DocumentClassifier.classify("Admit one", hasBarcode = true, hint = TicketDocumentKind.EVENT)
        assertEquals(PassKind.EVENT, result.kind)
        assertFalse(result.needsUserConfirmation)
        assertTrue("chosen when adding" in result.evidence)
    }

    @Test
    fun strong_text_that_disagrees_with_the_pick_asks_the_user() {
        val result = DocumentClassifier.classify(
            railText + "\nKenya Railways booking",
            hasBarcode = true,
            hint = TicketDocumentKind.EVENT
        )
        assertEquals(PassKind.RAIL, result.kind)
        assertTrue(result.needsUserConfirmation)
    }

    @Test
    fun evidence_never_carries_ticket_text() {
        val result = DocumentClassifier.classify(eventText, hasBarcode = true)
        result.evidence.forEach { assertFalse(it.contains("Sample", ignoreCase = true)) }
    }

    @Test
    fun pdf_text_beats_ocr_and_decoded_codes_are_exact() {
        val classification = DocumentClassifier.classify(eventText, hasBarcode = true)
        val pdf = PassFieldReader.read(draft(), ReadEvidence(fromPdfText = true, barcodeFromPixels = true), classification)
        val ocr = PassFieldReader.read(draft(), ReadEvidence(fromPdfText = false, barcodeFromPixels = true), classification)
        assertEquals(ExtractionSource.PDF_TEXT, pdf.fields.getValue(PassField.TITLE).source)
        assertEquals(ExtractionSource.OCR, ocr.fields.getValue(PassField.TITLE).source)
        assertTrue(pdf.fields.getValue(PassField.TITLE).confidence > ocr.fields.getValue(PassField.TITLE).confidence)
        assertEquals(ExtractionSource.BARCODE, pdf.fields.getValue(PassField.BARCODE).source)
        assertTrue(pdf.fields.getValue(PassField.BARCODE).confidence >= ConfidencePolicy.HIGH)
    }

    @Test
    fun a_code_read_from_text_is_flagged_for_review() {
        val classification = DocumentClassifier.classify(eventText, hasBarcode = false)
        val read = PassFieldReader.read(draft(), ReadEvidence(fromPdfText = false, barcodeFromPixels = false), classification)
        assertTrue(read.needsReview(PassField.BARCODE))
    }

    @Test
    fun missing_fields_stay_null_never_invented() {
        val classification = DocumentClassifier.classify(eventText, hasBarcode = true)
        val read = PassFieldReader.read(
            draft(venue = null, startsAt = null),
            ReadEvidence(fromPdfText = true, barcodeFromPixels = true),
            classification
        )
        assertNull(read.fields.getValue(PassField.VENUE).value)
        assertNull(read.fields.getValue(PassField.STARTS_AT).value)
        assertNull(read.fields.getValue(PassField.ORDER_ID).value)
        assertEquals(0f, read.fields.getValue(PassField.VENUE).confidence)
        assertFalse(read.needsReview(PassField.VENUE))
    }

    @Test
    fun pending_codes_are_not_treated_as_codes() {
        val classification = DocumentClassifier.classify(eventText, hasBarcode = false)
        val pending = draft(code = EventTicketSms.PENDING_PREFIX + "REF1")
        val read = PassFieldReader.read(pending, ReadEvidence(fromPdfText = true, barcodeFromPixels = false), classification)
        assertNull(read.fields.getValue(PassField.BARCODE).value)
        assertTrue(PassValidator.validate(pending, PassKind.EVENT).contains(PassIssue.PENDING_CODE))
    }

    @Test
    fun user_edits_become_user_input() {
        val classification = DocumentClassifier.classify(eventText, hasBarcode = true)
        val before = PassFieldReader.read(draft(), ReadEvidence(fromPdfText = false, barcodeFromPixels = true), classification)
        val after = PassFieldReader.withEdits(before, draft(venue = "Sample Arena"))
        val venue = after.fields.getValue(PassField.VENUE)
        assertEquals("Sample Arena", venue.value)
        assertEquals(ExtractionSource.USER_INPUT, venue.source)
        assertEquals(1f, venue.confidence)
        assertEquals(before.fields.getValue(PassField.TITLE), after.fields.getValue(PassField.TITLE))
    }

    @Test
    fun extraction_round_trips_through_json() {
        val classification = DocumentClassifier.classify(eventText, hasBarcode = true)
        val read = PassFieldReader.read(draft(), ReadEvidence(fromPdfText = true, barcodeFromPixels = true), classification)
        assertEquals(read, PassExtraction.fromJson(read.toJson()))
        assertNull(PassExtraction.fromJson("not json"))
    }

    @Test
    fun fromJson_skips_unknown_source_and_field_without_throwing() {
        val json = """
            {
              "version": 1,
              "kind": "EVENT",
              "kindConfidence": 0.9,
              "evidence": ["synthetic"],
              "confirmKind": false,
              "fields": {
                "TITLE": {
                  "value": "Sample Festival",
                  "confidence": 0.85,
                  "source": "PDF_TEXT",
                  "review": false
                },
                "VENUE": {
                  "value": "Should be dropped",
                  "confidence": 0.5,
                  "source": "LOCAL_MODEL",
                  "review": true
                },
                "NOT_A_FIELD": {
                  "value": "x",
                  "confidence": 0.5,
                  "source": "OCR",
                  "review": true
                },
                "BARCODE": {
                  "value": "SAMPLE-CODE-1",
                  "confidence": 0.99,
                  "source": "BARCODE",
                  "review": false
                }
              }
            }
        """.trimIndent()
        val parsed = PassExtraction.fromJson(json)!!
        assertEquals("Sample Festival", parsed.fields.getValue(PassField.TITLE).value)
        assertNull(parsed.fields[PassField.VENUE])
        assertEquals(ExtractionSource.BARCODE, parsed.fields.getValue(PassField.BARCODE).source)
        assertTrue(parsed.fields.getValue(PassField.BARCODE).source.isGateDecoded)
        assertFalse(ExtractionSource.OCR.isGateDecoded)
    }

    @Test
    fun fromJson_rejects_unknown_kind_and_missing_fields_object() {
        assertNull(
            PassExtraction.fromJson(
                """{"version":1,"kind":"FLIGHT","kindConfidence":0.1,"evidence":[],"confirmKind":true,"fields":{}}"""
            )
        )
        val emptyFields = PassExtraction.fromJson(
            """{"version":1,"kind":"EVENT","kindConfidence":0.5,"evidence":[],"confirmKind":false}"""
        )!!
        assertTrue(emptyFields.fields.isEmpty())
    }

    @Test
    fun validator_blocks_only_what_a_pass_cannot_work_without() {
        assertTrue(PassValidator.canSave(PassValidator.validate(draft(), PassKind.EVENT, now)))
        val noCode = PassValidator.validate(draft(code = ""), PassKind.EVENT, now)
        assertTrue(PassIssue.MISSING_CODE in noCode)
        assertFalse(PassValidator.canSave(noCode))
        assertTrue(PassIssue.MISSING_DATE in PassValidator.validate(draft(startsAt = null), PassKind.EVENT, now))
        assertTrue(
            PassIssue.MISSING_NAME in PassValidator.validate(draft(title = "", venue = null), PassKind.EVENT, now)
        )
        // Optional fields never block.
        assertTrue(PassValidator.canSave(PassValidator.validate(draft(venue = null), PassKind.EVENT, now)))
    }

    @Test
    fun validator_warns_on_odd_dates_without_blocking() {
        val past = PassValidator.validate(draft(startsAt = now - 60 * day), PassKind.EVENT, now)
        assertTrue(PassIssue.LONG_PAST in past)
        assertTrue(PassValidator.canSave(past))
        assertTrue(PassIssue.FAR_FUTURE in PassValidator.validate(draft(startsAt = now + 900 * day), PassKind.EVENT, now))
        val start = now + 10 * day
        val ends = PassValidator.validate(draft(startsAt = start, notes = "Ends: ${start - day}"), PassKind.EVENT, now)
        assertTrue(PassIssue.ENDS_BEFORE_START in ends)
        // An event with no end time is fine.
        assertFalse(PassIssue.ENDS_BEFORE_START in PassValidator.validate(draft(), PassKind.EVENT, now))
    }

    @Test
    fun lifecycle_marks_cancelled_as_used() {
        val life = PassLifecycleResolver.resolve(
            "SAMPLE EVENT PASS\nStatus: CANCELLED\nVenue: Test Hall",
            startsAtMillis = now + day,
            nowMillis = now
        )
        assertEquals(TicketStatus.USED, life.status)
        assertEquals(PassIssue.CANCELLED, life.issue)
    }

    @Test
    fun lifecycle_keeps_started_today_active_and_past_days_past_due() {
        val zone = ZoneOffset.UTC
        // Started an hour ago, same day: still the pass at the gate.
        val started = PassLifecycleResolver.resolve(
            "SAMPLE EVENT PASS",
            startsAtMillis = now - 3_600_000L,
            nowMillis = now,
            zone = zone
        )
        assertEquals(TicketStatus.ACTIVE, started.status)
        assertEquals(PassIssue.ALREADY_STARTED, started.issue)

        val yesterday = PassLifecycleResolver.resolve(
            "SAMPLE EVENT PASS",
            startsAtMillis = now - 24L * 3_600_000L,
            nowMillis = now,
            zone = zone
        )
        assertEquals(TicketStatus.PAST_DUE, yesterday.status)
        assertEquals(PassIssue.DAY_PASSED, yesterday.issue)

        // Same calendar day, still ahead → active + due-today note.
        val laterToday = now + 2L * 3_600_000L
        val today = PassLifecycleResolver.resolve(
            "SAMPLE EVENT PASS",
            startsAtMillis = laterToday,
            nowMillis = now,
            zone = zone
        )
        assertEquals(TicketStatus.ACTIVE, today.status)
        assertTrue(today.isToday)
        assertEquals(PassIssue.DUE_TODAY, today.issue)
    }

    @Test
    fun file_type_comes_from_bytes() {
        assertEquals(DocumentRules.PDF, DocumentRules.sniffMime("%PDF-1.7".toByteArray()))
        assertEquals("image/jpeg", DocumentRules.sniffMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals(
            "image/png",
            DocumentRules.sniffMime(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A))
        )
        assertEquals("image/webp", DocumentRules.sniffMime("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray()))
        assertEquals("image/heic", DocumentRules.sniffMime("\u0000\u0000\u0000\u0018ftypheic".toByteArray()))
        // A zip renamed to .pdf, or plain text, is refused.
        assertNull(DocumentRules.sniffMime(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertNull(DocumentRules.sniffMime("hello".toByteArray()))
        assertNull(DocumentRules.sniffMime(ByteArray(0)))
    }
}
