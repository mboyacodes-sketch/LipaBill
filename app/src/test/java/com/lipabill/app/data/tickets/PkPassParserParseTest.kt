package com.lipabill.app.data.tickets

import com.lipabill.app.data.model.TicketBarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PkPassParserParseTest {

    @Test
    fun parse_reads_event_ticket_fields_from_zip() {
        val json = """
            {
              "organizationName": "Ticketmaster",
              "description": "Live show",
              "logoText": "Sauti Sol",
              "serialNumber": "ORD-99",
              "barcodes": [{"format":"PKBarcodeFormatQR","message":"GATE-QR-1"}],
              "eventTicket": {
                "primaryFields": [{"key":"event","label":"Event","value":"Sauti Sol"}],
                "secondaryFields": [
                  {"key":"venue","label":"Venue","value":"KICC"},
                  {"key":"seat","label":"Seat","value":"A12"}
                ]
              }
            }
        """.trimIndent()
        val zip = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("pass.json"))
                zip.write(json.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            bytes.toByteArray()
        }
        val pass = PkPassParser.parse(ByteArrayInputStream(zip))
        assertEquals("Sauti Sol", pass.title)
        assertEquals("KICC", pass.venue)
        assertEquals("A12", pass.seatOrTier)
        assertEquals("GATE-QR-1", pass.barcodeValue)
        assertEquals(TicketBarcodeFormat.QR_CODE, pass.barcodeFormat)
        assertEquals("ORD-99", pass.orderId)
        assertTrue(pass.notes?.contains("Ticketmaster") == true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parse_rejects_zip_without_pass_json() {
        val zip = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { z ->
                z.putNextEntry(ZipEntry("manifest.json"))
                z.write("{}".toByteArray())
                z.closeEntry()
            }
            bytes.toByteArray()
        }
        PkPassParser.parse(ByteArrayInputStream(zip))
    }
}
