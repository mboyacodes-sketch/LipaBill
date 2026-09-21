package com.lipabill.app.data.parser

import com.lipabill.app.ui.util.formatKes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MillionAmountTest {
    @Test
    fun parses_and_formats_millions() {
        assertEquals(1_000_000.0, MpesaSmsParser.parseAmount("1,000,000.00")!!, 0.001)
        assertEquals(2_450_000.5, MpesaSmsParser.parseAmount("2,450,000.50")!!, 0.001)
        assertEquals(12_345_678.9, MpesaSmsParser.parseAmount("12,345,678.90")!!, 0.001)

        val body =
            "MIL01AAAAA Confirmed.You have received Ksh1,000,000.00 from BANK 123 on 21/9/26 at 4:35 PM " +
                "New M-PESA balance is Ksh1,250,333.28."
        val tx = MpesaSmsParser.parse(body, 1L)
        assertEquals(1_000_000.0, tx.amount!!, 0.001)
        assertEquals(1_250_333.28, tx.balance!!, 0.001)

        val shown = formatKes(1_250_333.28)
        println("formatted=$shown len=${shown.length}")
        assertEquals("1,250,333.28", shown)
        assertTrue(shown.length <= 14)
    }
}
