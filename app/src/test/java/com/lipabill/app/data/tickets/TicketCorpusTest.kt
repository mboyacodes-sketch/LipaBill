package com.lipabill.app.data.tickets

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Ticket corpus: real PDF bytes through the same PDFBox → positioned lines → extractor path the
 * app uses, checked field by field.
 *
 * Built-in fixtures are drawn here as PDFs with fictional people and codes, modelled on issuer
 * layouts seen in the wild. To check real tickets privately (never commit them):
 *
 *   TICKET_CORPUS=/path/to/dir ./gradlew :app:testPlayDebugUnitTest --tests '*TicketCorpusTest*'
 *
 * The folder holds `name.pdf` + `name.json`, where the JSON lists the fields you expect, e.g.
 * {"title":"Lakeside Culture Fest","venue":"Arboretum Gardens","start":"2026-11-14T14:00","end":"2026-11-14T23:00",
 *  "tier":"VVIP Offer","attendee":"Jane Doe","ticketNumber":"4821","organizer":"Tikiti Demo"}
 * Keys you leave out are not checked. `"flight": true` expects the file to be refused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TicketCorpusTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private val today = LocalDate.of(2026, 3, 1)

    @Before
    fun init() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    /** One text run on a fixture page: x / y from the top-left in points. */
    private data class Run(val text: String, val x: Float, val y: Float, val size: Float, val bold: Boolean = false)

    private class Fixture(val name: String, val runs: List<Run>, val expected: Map<String, Any?>)

    private fun pdfOf(runs: List<Run>): ByteArray {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A5)
            doc.addPage(page)
            val h = page.mediaBox.height
            PDPageContentStream(doc, page).use { cs ->
                for (r in runs) {
                    cs.beginText()
                    cs.setFont(if (r.bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, r.size)
                    cs.newLineAtOffset(r.x, h - r.y - r.size)
                    cs.showText(r.text)
                    cs.endText()
                }
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }

    /** The app's path: PDF text layer → positioned lines → extractor (or refusal). */
    private fun read(pdf: ByteArray): Pair<EventTicketFields?, Boolean> {
        PDDocument.load(pdf).use { doc ->
            val lines = PdfTextLines.read(doc)
            val text = lines.joinToString("\n") { it.text }
            if (EventTicketExtractor.looksLikeFlight(text)) return null to true
            return EventTicketExtractor.extract(lines, today, zone) to false
        }
    }

    private fun time(iso: String): Long = LocalDateTime.parse(iso).atZone(zone).toInstant().toEpochMilli()

    /** Field-by-field comparison; returns human-readable mismatches. */
    private fun compare(f: EventTicketFields?, refused: Boolean, expected: Map<String, Any?>): List<String> {
        if (expected["flight"] == true) return if (refused) emptyList() else listOf("expected a flight refusal")
        if (refused || f == null) return listOf("refused as a flight")
        val actual: Map<String, Any?> = mapOf(
            "title" to f.title, "venue" to f.venue, "start" to f.startsAtMillis, "end" to f.endsAtMillis,
            "tier" to f.tier, "attendee" to f.attendee, "price" to f.price, "admits" to f.admits,
            "ticketNumber" to f.ticketNumber, "orderRef" to f.orderRef, "organizer" to f.organizer,
            "doors" to f.doors, "gate" to f.gate, "section" to f.section, "row" to f.row, "seat" to f.seat
        )
        return expected.mapNotNull { (key, want) ->
            val wantValue = if ((key == "start" || key == "end") && want is String) time(want) else want
            val got = actual[key]
            if (got?.toString() == wantValue?.toString()) null else "$key: expected <$want> got <$got>"
        }
    }

    // ── Built-in fixtures ────────────────────────────────────────────────

    private val fixtures = listOf(
        // Storefront ticket: big title, header line wrapping the venue, `LABEL value`, an
        // indented value column, a big ticket number further down.
        Fixture(
            "storefront",
            listOf(
                Run("Lakeside Culture Fest", 29f, 9f, 24f, bold = true),
                Run("Sat 14 Nov 2026 · 02:00 PM · Arboretum", 29f, 33f, 12f),
                Run("Gardens", 29f, 46f, 12f),
                Run("ATTENDEE Jane Doe", 29f, 97f, 12f),
                Run("PHONE NUMBER +254700000000", 29f, 116f, 12f),
                Run("START DATE Sat 14 Nov 2026", 29f, 135f, 12f),
                Run("02:00 PM", 85f, 150f, 12f),
                Run("END DATE", 29f, 168f, 9f),
                Run("11:00 PM", 85f, 180f, 12f),
                Run("STOREFRONT Tikiti Demo", 29f, 197f, 12f),
                Run("TICKET VVIP Offer", 29f, 216f, 12f),
                Run("Ticket: 4821", 86f, 376f, 24f, bold = true),
                Run("S90021", 115f, 404f, 12f),
                Run("Entry: QR scan at the gate. Tikiti Demo", 86f, 480f, 11f)
            ),
            mapOf(
                "title" to "Lakeside Culture Fest", "venue" to "Arboretum Gardens",
                "start" to "2026-11-14T14:00", "end" to "2026-11-14T23:00",
                "tier" to "VVIP Offer", "attendee" to "Jane Doe", "ticketNumber" to "4821",
                "organizer" to "Tikiti Demo", "gate" to null
            )
        ),
        // Column layout: labels with values underneath, a date range, an overnight time.
        Fixture(
            "columns",
            listOf(
                Run("SUNSET SESSIONS", 30f, 20f, 26f, bold = true),
                Run("• 04-05 Apr 2026", 30f, 70f, 14f),
                Run("Time:", 170f, 100f, 9f),
                Run("Amount Paid:", 300f, 100f, 9f),
                Run("2:00 PM - 2:00 AM", 170f, 114f, 12f),
                Run("KES 4,500 | 2 tickets", 300f, 114f, 12f),
                Run("Ticket Type:", 30f, 150f, 9f),
                Run("Location:", 170f, 150f, 9f),
                // Columns sit on a realistic gutter (~30 pt) between left text and the next column.
                Run("Long Weekend Pass -", 30f, 164f, 10f),
                Run("Lakeview Grounds", 170f, 164f, 10f),
                Run("Access to the Hangout", 30f, 177f, 10f),
                Run("TICKET: TKT00DEMO7QX", 170f, 230f, 12f, bold = true),
                Run("ZAWADI ATIENO", 30f, 232f, 10f)
            ),
            mapOf(
                "title" to "Sunset Sessions", "venue" to "Lakeview Grounds",
                "start" to "2026-04-04T14:00", "end" to "2026-04-05T02:00",
                "tier" to "Long Weekend Pass", "price" to "KES 4,500", "admits" to 2,
                "ticketNumber" to "TKT00DEMO7QX", "attendee" to "Zawadi Atieno"
            )
        ),
        // Classic `Label: value` confirmation with a heading above the title.
        Fixture(
            "label-colon",
            listOf(
                Run("ORDER CONFIRMATION", 30f, 20f, 10f),
                Run("Blankets & Wine", 30f, 40f, 22f, bold = true),
                Run("Date: 7 Dec 2026", 30f, 80f, 11f),
                Run("Time: 1pm", 30f, 96f, 11f),
                Run("Venue: Laureate Gardens, Nairobi", 30f, 112f, 11f),
                Run("Ticket Type: Early Bird", 30f, 128f, 11f),
                Run("Name: John Otieno", 30f, 144f, 11f),
                Run("Order #: BW-99812", 30f, 160f, 11f),
                Run("Total: Ksh 3,500", 30f, 176f, 11f),
                Run("Doors: 12:00 PM", 30f, 192f, 11f),
                Run("Gate: B", 30f, 208f, 11f),
                Run("Seat: 12", 30f, 224f, 11f)
            ),
            mapOf(
                "title" to "Blankets & Wine", "venue" to "Laureate Gardens, Nairobi",
                "start" to "2026-12-07T13:00", "tier" to "Early Bird", "attendee" to "John Otieno",
                "orderRef" to "BW-99812", "price" to "KES 3,500", "doors" to "12:00 PM",
                "gate" to "B", "seat" to "12"
            )
        ),
        // Month-first international layout with a separate Location block.
        Fixture(
            "month-first",
            listOf(
                Run("Nairobi Tech Summit 2026", 30f, 24f, 20f, bold = true),
                Run("Date and time", 30f, 70f, 9f),
                Run("Thu, Dec 3, 2026, 9:00 AM – 5:00 PM", 30f, 82f, 11f),
                Run("Location", 30f, 108f, 9f),
                Run("Sarit Expo Centre", 30f, 120f, 11f),
                Run("Ticket type", 30f, 146f, 9f),
                Run("General Admission", 30f, 158f, 11f),
                Run("Order #3871204455", 30f, 184f, 11f),
                Run("Organizer: Tech Hub Africa", 30f, 200f, 11f)
            ),
            mapOf(
                "title" to "Nairobi Tech Summit 2026", "venue" to "Sarit Expo Centre",
                "start" to "2026-12-03T09:00", "end" to "2026-12-03T17:00",
                "tier" to "General Admission", "orderRef" to "3871204455",
                "organizer" to "Tech Hub Africa"
            )
        ),
        // Airline itinerary: must be refused, not saved as an event or SGR trip.
        Fixture(
            "flight-itinerary",
            listOf(
                Run("Itinerary", 30f, 20f, 18f, bold = true),
                Run("Booking Reference: ABC123", 30f, 50f, 11f),
                Run("Passenger Contact Information Flight Ticket", 30f, 66f, 11f),
                Run("DEPARTING: 25 Sep 2026 Nairobi Int 06:30 Mombasa 07:40", 30f, 82f, 11f),
                Run("Baggage allowance per person is 20kg.", 30f, 98f, 11f),
                Run("Airport check-in closes 30 mins before departure.", 30f, 114f, 11f)
            ),
            mapOf("flight" to true)
        )
    )

    @Test
    fun builtInFixtures() {
        val report = fixtures.associate { fx ->
            val (fields, refused) = read(pdfOf(fx.runs))
            fx.name to compare(fields, refused, fx.expected)
        }
        val failures = report.filterValues { it.isNotEmpty() }
        assertTrue(
            failures.entries.joinToString("\n") { (name, misses) -> "$name:\n  " + misses.joinToString("\n  ") },
            failures.isEmpty()
        )
    }

    @Test
    fun pdfTextLayerLoadsWithShippedAssets() {
        // Fails loudly if PDFBox can't initialise its text engine (missing glyph lists etc.).
        val (fields, refused) = read(pdfOf(listOf(Run("Asset Check Night", 30f, 30f, 20f, bold = true))))
        assertEquals(false, refused)
        assertEquals("Asset Check Night", fields?.title)
    }

    @Test
    fun privateCorpus() {
        val dir = System.getenv("TICKET_CORPUS")?.takeIf { it.isNotBlank() }?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val pdfs = dir!!.listFiles { f -> f.name.endsWith(".pdf", ignoreCase = true) }.orEmpty().sorted()
        var checked = 0
        var correct = 0
        val lines = mutableListOf<String>()
        for (pdf in pdfs) {
            val spec = File(pdf.parentFile, pdf.nameWithoutExtension + ".json")
            val (fields, refused) = read(pdf.readBytes())
            if (!spec.exists()) {
                // No expectations yet: print what was read so you can write the JSON.
                lines += "${pdf.name}: (no .json) read ${if (refused) "FLIGHT" else fields}"
                continue
            }
            val json = JSONObject(spec.readText())
            val expected = json.keys().asSequence().associateWith { k -> json.opt(k).takeUnless { it == JSONObject.NULL } }
            val misses = compare(fields, refused, expected)
            checked += expected.size
            correct += expected.size - misses.size
            lines += if (misses.isEmpty()) "${pdf.name}: OK" else "${pdf.name}:\n  " + misses.joinToString("\n  ")
        }
        val accuracy = if (checked == 0) 100.0 else correct * 100.0 / checked
        val summary = "Corpus: ${pdfs.size} files, $correct/$checked fields correct (%.1f%%)".format(accuracy)
        println((lines + summary).joinToString("\n"))
        assertTrue(summary + "\n" + lines.joinToString("\n"), correct == checked)
    }
}
