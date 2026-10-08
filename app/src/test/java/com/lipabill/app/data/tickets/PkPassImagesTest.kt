package com.lipabill.app.data.tickets

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PkPassImagesTest {

    @Test
    fun prefers_the_sharper_strip_and_keeps_logo_and_footer() {
        val zip = zipOf(
            "pass.json" to """{"description":"Show"}""",
            "strip.png" to byteArrayOf(1),
            "strip@2x.png" to byteArrayOf(2, 2),
            "strip@3x.png" to byteArrayOf(3, 3, 3),
            "logo@2x.png" to byteArrayOf(4),
            "icon.png" to byteArrayOf(9),
            "footer.png" to byteArrayOf(5)
        )
        val read = readPkPassZip(ByteArrayInputStream(zip))
        assertEquals("""{"description":"Show"}""", read.passJson)
        assertArrayEquals(byteArrayOf(3, 3, 3), read.images.hero)
        assertArrayEquals(byteArrayOf(4), read.images.logo)
        assertArrayEquals(byteArrayOf(5), read.images.footer)
    }

    @Test
    fun thumbnail_stands_in_when_there_is_no_strip() {
        val zip = zipOf(
            "pass.json" to "{}",
            "thumbnail@2x.png" to byteArrayOf(7),
            "icon@3x.png" to byteArrayOf(8)
        )
        val images = readPkPassZip(ByteArrayInputStream(zip)).images
        assertArrayEquals(byteArrayOf(7), images.hero)
        assertArrayEquals(byteArrayOf(8), images.logo)
        assertNull(images.footer)
    }

    private fun zipOf(vararg files: Pair<String, Any>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, body) ->
                zip.putNextEntry(ZipEntry(name))
                val bytes = when (body) {
                    is ByteArray -> body
                    is String -> body.toByteArray()
                    else -> error(body)
                }
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
