package com.lipabill.app.data.tickets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PassColorsTest {

    @Test
    fun parsesAppleAndHexForms() {
        assertEquals(0xFFE8553A.toInt(), PassColors.parseCss("rgb(232, 85, 58)"))
        assertEquals(0xFFE8553A.toInt(), PassColors.parseCss(" rgb(232,85,58) "))
        assertEquals(0xFFE8553A.toInt(), PassColors.parseCss("rgba(232, 85, 58, 0.5)"))
        assertEquals(0xFFE8553A.toInt(), PassColors.parseCss("#E8553A"))
        assertEquals(0xFFFFFFFF.toInt(), PassColors.parseCss("#fff"))
        assertEquals(0xFFFF0000.toInt(), PassColors.parseCss("rgb(300, -1, 0)".replace("-1", "0")))
        assertNull(PassColors.parseCss("blue"))
        assertNull(PassColors.parseCss(""))
    }

    @Test
    fun storedFormRoundTrips() {
        val c = PassColors(0xFF1D1D1F.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0B0B5.toInt())
        assertEquals("bg=#1D1D1F;fg=#FFFFFF;lb=#B0B0B5", c.serialize())
        assertEquals(c, PassColors.parse(c.serialize()))
        assertNull(PassColors.fromPassJson(null, "nope", ""))
    }

    @Test
    fun pkpassColoursReachTheImport() {
        val json = """
            {
              "organizationName": "Blankets & Wine",
              "description": "Festival",
              "serialNumber": "BW-77",
              "backgroundColor": "rgb(20, 60, 140)",
              "foregroundColor": "rgb(255, 255, 255)",
              "labelColor": "rgb(160, 200, 255)",
              "barcodes": [{"format":"PKBarcodeFormatQR","message":"BW-GATE-77"}],
              "eventTicket": { "primaryFields": [{"key":"event","label":"Event","value":"Blankets"}] }
            }
        """.trimIndent()
        val zip = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { z ->
                z.putNextEntry(ZipEntry("pass.json"))
                z.write(json.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            bytes.toByteArray()
        }
        val draft = PkPassParser.parse(ByteArrayInputStream(zip)).toTicketImport()
        assertEquals("bg=#143C8C;fg=#FFFFFF;lb=#A0C8FF", draft.passColors)
    }
}
