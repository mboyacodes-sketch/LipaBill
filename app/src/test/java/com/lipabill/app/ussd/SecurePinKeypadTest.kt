package com.lipabill.app.ussd

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun a_shuffled_pad_still_has_every_digit() {
        val rows = securePinKeyRows(SecureRandom())
        val digits = rows.flatten().filter { it.length == 1 && it[0].isDigit() }
        assertEquals(10, digits.toSet().size)
        assertTrue(digits.containsAll(('0'..'9').map(Char::toString)))
        assertEquals("⌫", rows.last().last())
    }
}
