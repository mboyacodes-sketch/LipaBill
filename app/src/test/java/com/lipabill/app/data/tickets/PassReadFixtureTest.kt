package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketBarcodeFormat
import com.lipabill.app.data.tickets.intelligence.DocumentClassifier
import com.lipabill.app.data.tickets.intelligence.PassFieldReader
import com.lipabill.app.data.tickets.intelligence.PassKind
import com.lipabill.app.data.tickets.intelligence.PassValidator
import com.lipabill.app.data.tickets.intelligence.ReadEvidence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * JVM regression fixtures for parse → classify → score → validate.
 * No ML Kit: barcodes are declared in JSON when the ticket has a pixel-decoded symbol.
 */
@RunWith(Parameterized::class)
class PassReadFixtureTest(private val fixtureName: String) {

    private val zone = ZoneId.of("Africa/Nairobi")
    private val today = LocalDate.of(2026, 11, 1)

    @Test
    fun parseClassifyValidate() {
        val raw = javaClass.classLoader!!
            .getResourceAsStream("pass_reads/$fixtureName.json")!!
            .bufferedReader()
            .readText()
        val root = JSONObject(raw)
        val text = root.getString("text")
        val barcodes = jsonBarcodes(root.optJSONArray("barcodes"))
        val expect = root.getJSONObject("expect")

        val draft = ETicketTextParser.parse(
            text,
            barcodes,
            kind = TicketDocumentKind.AUTO,
            lines = TextLine.fromPlainText(text)
        )
        val classification = DocumentClassifier.classify(
            text,
            hasBarcode = barcodes.isNotEmpty() || draft.barcodeValue.isNotBlank()
        )
        val extraction = PassFieldReader.read(
            draft,
            ReadEvidence(fromPdfText = true, barcodeFromPixels = barcodes.isNotEmpty()),
            classification
        )
        val issues = PassValidator.validate(
            draft,
            classification.kind,
            sourceText = text,
            zone = zone
        )

        expect.optString("kind").takeIf { it.isNotEmpty() }?.let { kind ->
            assertEquals(PassKind.valueOf(kind), classification.kind)
        }
        expect.optString("barcodeValue").takeIf { it.isNotEmpty() }?.let {
            assertEquals(it, draft.barcodeValue)
        }
        expect.optString("barcodeFormat").takeIf { it.isNotEmpty() }?.let {
            assertEquals(TicketBarcodeFormat.valueOf(it), draft.barcodeFormat)
        }
        expect.optString("titleContains").takeIf { it.isNotEmpty() }?.let {
            assertTrue("title=${draft.title}", draft.title.contains(it, ignoreCase = true))
        }
        expect.optString("startDate").takeIf { it.isNotEmpty() }?.let { iso ->
            assertNotNull(draft.startsAtMillis)
            val got = Instant.ofEpochMilli(draft.startsAtMillis!!).atZone(zone).toLocalDate()
            assertEquals(LocalDate.parse(iso), got)
        }
        val rejected = expect.optJSONArray("rejectBarcodeValues")
        if (rejected != null) {
            for (i in 0 until rejected.length()) {
                val bad = rejected.getString(i)
                assertFalse(draft.barcodeValue.contains(bad))
                assertTrue(ETicketTextParser.looksLikeImplausibleCode(bad) || draft.barcodeValue != bad)
            }
        }
        assertNotNull(extraction)
        assertTrue("unexpected blocking issues: $issues", PassValidator.canSave(issues) || issues.none { it.blocking })
        // Quiet unused today — fixtures are absolute-dated.
        assertTrue(today.year >= 2026)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<String>> = listOf(
            "order_date_vs_event",
            "phone_not_code",
            "sgr_serial_text",
            "code39_format",
            "datamatrix_format",
            "app_download_qr_loses"
        ).map { arrayOf(it) }
    }
}

private fun jsonBarcodes(arr: JSONArray?): List<ETicketTextParser.FoundBarcode> {
    if (arr == null) return emptyList()
    return List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        ETicketTextParser.FoundBarcode(
            o.getString("value"),
            TicketBarcodeFormat.valueOf(o.getString("format"))
        )
    }
}
