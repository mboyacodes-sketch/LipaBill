package com.lipabill.app.data.tickets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PkPassDetectionTest {

    @Test
    fun picker_files_are_recognised_by_display_name() {
        // Content URIs from the picker often end in a numeric id; the display name carries the extension.
        assertTrue(PkPassParser.looksLikePkPass(listOf("/document/1234", "1234", "KQ612.pkpass"), null))
    }

    @Test
    fun extension_is_case_insensitive() {
        assertTrue(PkPassParser.looksLikePkPass(listOf("Boarding.PKPASS"), "application/octet-stream"))
    }

    @Test
    fun wallet_mime_types_count_without_a_name() {
        assertTrue(PkPassParser.looksLikePkPass(emptyList(), "application/vnd.apple.pkpass"))
        assertTrue(PkPassParser.looksLikePkPass(emptyList(), "APPLICATION/VND.APPLE.PKPASSES"))
        assertTrue(PkPassParser.looksLikePkPass(emptyList(), "application/vnd-apple.pkpass"))
    }

    @Test
    fun pdfs_and_photos_are_not_passes() {
        assertFalse(PkPassParser.looksLikePkPass(listOf("ticket.pdf"), "application/pdf"))
        assertFalse(PkPassParser.looksLikePkPass(listOf("IMG_2041.jpg"), "image/jpeg"))
        assertFalse(PkPassParser.looksLikePkPass(emptyList(), null))
    }
}
