package com.lipabill.app.ui.tickets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.lipabill.app.data.tickets.PassColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPassPaletteTest {

    @Test
    fun noColoursKeepsTheMyPassOrange() {
        assertEquals(EventPassPalette.Default, EventPassPalette.from(null))
        assertEquals(EventPassPalette.Default, EventPassPalette.from(PassColors(foreground = 0xFFFFFFFF.toInt())))
    }

    @Test
    fun passColoursDriveCardAndText() {
        val p = EventPassPalette.from(
            PassColors(0xFF143C8C.toInt(), 0xFFFFFFFF.toInt(), 0xFFA0C8FF.toInt())
        )
        assertEquals(Color(0xFF143C8C), p.card)
        assertEquals(Color(0xFF143C8C), p.stageTop)
        assertEquals(Color.White, p.ink)
        assertEquals(Color(0xFFA0C8FF), p.label)
        assertEquals(Color.White, p.onStage)
    }

    @Test
    fun unreadablePairFallsBackToContrastingInk() {
        // Yellow text on a white pass: replaced with near-black, header turns dark too.
        val p = EventPassPalette.from(PassColors(0xFFFFFFFF.toInt(), 0xFFFFF176.toInt(), 0xFFFFF59D.toInt()))
        assertTrue(PassColors.contrast(p.ink.toArgb(), p.card.toArgb()) >= 4.5)
        assertTrue(PassColors.contrast(p.onStage.toArgb(), p.stageTop.toArgb()) >= 4.5)
        assertTrue(PassColors.contrast(p.label.copy(alpha = 1f).toArgb(), p.card.toArgb()) >= 3.0)
    }
}
