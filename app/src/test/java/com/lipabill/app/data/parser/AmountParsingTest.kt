package com.lipabill.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountParsingTest {
    private val ts = 1_700_000_000_000L

    @Test
    fun parseAmount_handles_thousand_separators() {
        assertEquals(10000.0, MpesaSmsParser.parseAmount("10,000.00")!!, 0.001)
        assertEquals(10000.0, MpesaSmsParser.parseAmount("10 000.00")!!, 0.001)
        assertEquals(10000.0, MpesaSmsParser.parseAmount("10\u00A0000.00")!!, 0.001)
        assertEquals(10000.0, MpesaSmsParser.parseAmount("10\u202F000.00")!!, 0.001)
        assertEquals(10000.0, MpesaSmsParser.parseAmount("10.000,00")!!, 0.001)
        assertEquals(12450.5, MpesaSmsParser.parseAmount("12,450.50")!!, 0.001)
        assertEquals(1000000.0, MpesaSmsParser.parseAmount("1,000,000.00")!!, 0.001)
        assertEquals(1000.0, MpesaSmsParser.parseAmount("1,000")!!, 0.001)
    }

    @Test
    fun sms_large_amounts_not_truncated() {
        val cases = listOf(
            "AAA1111111 Confirmed. Ksh10,000.00 sent to JOHN 254712345678 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50,000.00. Transaction cost, Ksh0.00." to 10000.0,
            "AAA1111112 Confirmed. Ksh12,450.50 sent to JOHN 254712345678 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50,000.00." to 12450.50,
            "AAA1111113 Confirmed. Ksh100,000.00 sent to JOHN 254712345678 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50,000.00." to 100000.0,
            "AAA1111114 Confirmed. Ksh1,000,000.00 sent to JOHN 254712345678 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50,000.00." to 1000000.0,
            "AAA1111115 Confirmed. Ksh10 000.00 sent to JOHN 254712345678 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50 000.00." to 10000.0,
            "AAA1111116 Confirmed. Ksh10\u00A0000.00 sent to JOHN 254712345678 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50\u00A0000.00." to 10000.0,
            "AAA1111117 Confirmed. Ksh10\u202F000.00 sent to JOHN 254712345678 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50\u202F000.00." to 10000.0,
            "AAA1111118 Confirmed. Ksh10.000,00 sent to JOHN 254712345678 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50.000,00." to 10000.0,
            "AAA1111119 Confirmed. Ksh10000.00 sent to JOHN 254712345678 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50000.00." to 10000.0,
            "AAA1111120 Confirmed. you have received Ksh25,000.00 from JANE 254700000000 on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh75,000.00." to 25000.0,
        )
        cases.forEach { (body, expected) ->
            val tx = MpesaSmsParser.parse(body, ts)
            assertEquals("Failed for: ${body.take(60)}", expected, tx.amount!!, 0.001)
        }
    }
}
