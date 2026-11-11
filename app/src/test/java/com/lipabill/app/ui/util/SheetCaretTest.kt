package com.lipabill.app.ui.util

import com.lipabill.app.data.parser.extractAccount
import com.lipabill.app.data.parser.sanitizeAccountNumber
import org.junit.Assert.assertEquals
import org.junit.Test

class SheetCaretTest {

    @Test
    fun insert_and_backspace_honor_the_caret() {
        val middle = SheetCaret("ac", 1, 1).insert("B")
        assertEquals("aBc", middle.text)
        assertEquals(2, middle.start)

        val selected = SheetCaret("abcd", 1, 3).backspace()
        assertEquals("ad", selected.text)
        assertEquals(1, selected.start)
    }

    @Test
    fun account_keeps_keyboard_symbols() {
        val typed = SheetCaret("AB", 2, 2).insert("-12/3", ::sanitizeAccountNumber)
        assertEquals("AB-12/3", typed.text)
        assertEquals(7, typed.start)
        assertEquals("A B!", sanitizeAccountNumber("A B!\n"))
        assertEquals(
            "12345678901234567890",
            sanitizeAccountNumber("12345678901234567890EXTRA")
        )
        assertEquals("AB#12*3", sanitizeAccountNumber("AB#12*3"))
        assertEquals("ABC#334", extractAccount("Confirmed. Account ABC#334 on 1/1/24"))
        assertEquals("ABC12", extractAccount("Account ABC12"))
    }

    @Test
    fun caret_scrolls_into_view_and_stays_when_already_visible() {
        assertEquals(null, caretScrollTarget(cursorX = 40f, viewportPx = 100, currentPx = 0, padPx = 12f))
        assertEquals(-8, caretScrollTarget(cursorX = 4f, viewportPx = 100, currentPx = 80, padPx = 12f))
        assertEquals(140, caretScrollTarget(cursorX = 220f, viewportPx = 100, currentPx = 0, padPx = 20f))
    }

    @Test
    fun paste_keeps_a_selection_when_the_text_is_unchanged_by_sanitize() {
        val pasted = SheetCaret().applyUser("0712 345678", 11, 11)
        assertEquals("0712 345678", pasted.text)
        assertEquals(11, pasted.start)
    }
}
