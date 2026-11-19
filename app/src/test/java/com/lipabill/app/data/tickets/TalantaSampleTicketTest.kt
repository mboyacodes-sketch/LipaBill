package com.lipabill.app.data.tickets

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import java.time.ZoneId
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A fictional Talanta Stadium ticket (no real holder, order or entry code), laid out like a
 * typical stadium e-ticket: match line, venue, date / kick-off / gates, category, gate-block-
 * row-seat, ticket and order numbers, QR. Read through the app's PDF path.
 *
 *   SAMPLE_TICKET_OUT=/path/talanta-sample.pdf ./gradlew :app:testPlayDebugUnitTest --tests '*TalantaSample*'
 * also writes the PDF, to try the import on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TalantaSampleTicketTest {

    private val zone = ZoneId.of("Africa/Nairobi")
    private val today = LocalDate.of(2026, 11, 18)

    @Before
    fun init() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    private data class Run(val text: String, val x: Float, val y: Float, val size: Float, val bold: Boolean = false)

    private val runs = listOf(
        Run("TALANTA SPORTS CITY", 24f, 22f, 10f, bold = true),
        Run("E-TICKET", 330f, 22f, 10f, bold = true),
        Run("FC BAYERN MUNICH VS AFC AJAX", 24f, 50f, 17f, bold = true),
        Run("Stadium Inauguration Match", 24f, 74f, 11f),
        Run("Venue: Raila Odinga International Stadium, Nairobi", 24f, 104f, 10f),
        Run("Date: Tuesday, 05 January 2027", 24f, 122f, 10f),
        Run("Kick-off: 20:15", 24f, 140f, 10f),
        Run("Gates open: 11:00", 200f, 140f, 10f),
        Run("Category: Regular", 24f, 168f, 10f),
        Run("Gate: 4", 24f, 190f, 10f),
        Run("Block: C", 100f, 190f, 10f),
        Run("Row: 12", 176f, 190f, 10f),
        Run("Seat: 18", 252f, 190f, 10f),
        Run("Ticket No: TSC-27-000123", 24f, 218f, 10f),
        Run("Order: ORD-884201", 24f, 236f, 10f),
        Run("Holder: Jane Doe", 24f, 254f, 10f),
        Run("One scan only. No re-entry. Ticket void if transferred outside the official app.", 24f, 520f, 7f)
    )

    /** Fictional code: never a real entry pass. */
    private val qrPayload = "TSC-SAMPLE-27-000123-NOT-VALID"

    private fun pdf(): ByteArray {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A5)
            doc.addPage(page)
            val h = page.mediaBox.height
            val matrix = QRCodeWriter().encode(qrPayload, BarcodeFormat.QR_CODE, 300, 300)
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
            val image = LosslessFactory.createFromImage(doc, bitmap)
            PDPageContentStream(doc, page).use { cs ->
                for (r in runs) {
                    cs.beginText()
                    cs.setFont(if (r.bold) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, r.size)
                    cs.newLineAtOffset(r.x, h - r.y - r.size)
                    cs.showText(r.text)
                    cs.endText()
                }
                cs.drawImage(image, 110f, h - 500f, 200f, 200f)
            }
            return ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        }
    }

    @Test
    fun readsTheSampleTicket() {
        val bytes = pdf()
        System.getenv("SAMPLE_TICKET_OUT")?.takeIf { it.isNotBlank() }?.let { File(it).writeBytes(bytes) }
        PDDocument.load(bytes).use { doc ->
            val lines = PdfTextLines.read(doc)
            val fields = EventTicketExtractor.extract(lines, today, zone)
            assertEquals("FC Bayern Munich vs AFC Ajax", fields.title)
            assertEquals("Raila Odinga International Stadium, Nairobi", fields.venue)
            // "Kick-off: 20:15" is the start; "Gates open: 11:00" is the doors time.
            assertEquals(
                LocalDateTime.of(2027, 1, 5, 20, 15).atZone(zone).toInstant().toEpochMilli(),
                fields.startsAtMillis
            )
            assertEquals("11:00 AM", fields.doors)
            assertEquals("Regular", fields.tier)
            assertEquals("4", fields.gate)
            assertEquals("C", fields.section)
            assertEquals("12", fields.row)
            assertEquals("18", fields.seat)
            assertEquals("TSC-27-000123", fields.ticketNumber)
            assertEquals("ORD-884201", fields.orderRef)
            assertEquals("Jane Doe", fields.attendee)
        }
    }
}
