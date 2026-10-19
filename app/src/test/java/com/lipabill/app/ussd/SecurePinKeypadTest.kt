package com.lipabill.app.ussd

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurePinKeypadTest {

    @Test
    fun each_digit_appears_once_and_delete_stays_put() {
        val rows = pinKeyRows(('0'..'9').toList())
        val flat = rows.flatten().filter { it.isNotEmpty() && it != "⌫" }
        assertEquals(('0'..'9').map(Char::toString), flat)
        assertEquals("", rows.last().first())
        assertEquals("⌫", rows.last().last())
    }

    @Test
    fun pin_pad_uses_phone_order() {
        val rows = securePinKeyRows()
        assertEquals(listOf("1", "2", "3"), rows[0])
        assertEquals(listOf("4", "5", "6"), rows[1])
        assertEquals(listOf("7", "8", "9"), rows[2])
        assertEquals(listOf("", "0", "⌫"), rows[3])
    }
}
