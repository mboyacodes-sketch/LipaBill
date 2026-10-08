package com.lipabill.app.data.tickets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SharedTicketInputTest {

    @Test
    fun only_content_uris_are_shared_files() {
        assertTrue(PkPassIntents.isSharedContentScheme("content"))
        assertTrue(PkPassIntents.isSharedContentScheme("CONTENT"))
        assertFalse(PkPassIntents.isSharedContentScheme("file"))
        assertFalse(PkPassIntents.isSharedContentScheme(null))
    }

    @Test
    fun a_wildcard_mime_is_not_treated_as_a_pass() {
        assertTrue(PkPassIntents.mimeCanWrapPkPass("application/zip"))
        assertTrue(PkPassIntents.mimeCanWrapPkPass("application/octet-stream"))
        assertFalse(PkPassIntents.mimeCanWrapPkPass("*/*"))
        assertFalse(PkPassIntents.mimeCanWrapPkPass(""))
    }

    @Test
    fun copy_stops_at_the_cap() {
        val input = ByteArrayInputStream(ByteArray(32) { 1 })
        val out = ByteArrayOutputStream()
        assertFalse(copyAtMost(input, out, maxBytes = 8))
        assertNull(readAtMost(ByteArrayInputStream(ByteArray(32) { 2 }), 8))
        val exact = readAtMost(ByteArrayInputStream(byteArrayOf(9, 8, 7)), 3)
        assertTrue(exact.contentEquals(byteArrayOf(9, 8, 7)))
    }

    @Test
    fun an_oversized_pass_json_is_dropped() {
        val zip = zipWith("pass.json" to "x".repeat(MAX_PASS_JSON_BYTES + 1))
        assertNull(readPkPassZip(ByteArrayInputStream(zip)).passJson)
    }

    private fun zipWith(file: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry(file.first))
            zip.write(file.second.toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
