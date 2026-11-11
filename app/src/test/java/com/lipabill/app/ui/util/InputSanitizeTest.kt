package com.lipabill.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class InputSanitizeTest {

    @Test
    fun digits_and_phone_fields() {
        assertEquals("5629921", sanitizeDigits("Till 5629921!!"))
        assertEquals("12345", sanitizeDigits("123456789012345", max = 5))
        assertEquals("+254 712 345 678", sanitizePayPhone("+254 712 345 678abc"))
        assertEquals("0712345678901234", sanitizePayPhone("071234567890123456789"))
    }

    @Test
    fun account_and_whole_shillings() {
        assertEquals("SCHOOLFEES", sanitizePayAccount("SCHOOL-FEES!!"))
        assertEquals("123456", sanitizeWholeShillings("Ksh 1234567"))
        assertEquals("", sanitizeWholeShillings("abc"))
    }
}
