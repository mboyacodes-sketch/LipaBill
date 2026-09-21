package com.lipabill.app.ui.tickets

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardingPassPdf417Test {

    private val sampleBcbp =
        "M1HASSAN/A            E7613002MBANBOKQ 0609275U022A0000100"

    @Test
    fun encodesHorizontalPdf417MatrixFromBcbp() {
        val matrix = BoardingPassPdf417.encodeHorizontalMatrix(
            sampleBcbp,
            width = 900,
            height = 160
        )
        assertNotNull(matrix)
        // Horizontal: wider than tall
        assertTrue(matrix!!.width > matrix.height)
        assertTrue(matrix.width >= 200)
        assertTrue(matrix.height >= 60)
    }

    @Test
    fun emptyPayloadReturnsNull() {
        assertNull(BoardingPassPdf417.encodeHorizontalMatrix(""))
        assertNull(BoardingPassPdf417.encodeHorizontalMatrix("   "))
    }
}
