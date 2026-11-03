package com.lipabill.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLedgerTest {

    @Test
    fun request_phone_matches_ada_demo() {
        assertEquals("0700000001", DemoLedger.REQUEST_PHONE)
    }

    @Test
    fun rows_are_labelled_demo_and_are_not_a_pin() {
        val rows = DemoLedger.rows(nowMillis = 1_700_000_000_000L)
        assertTrue(rows.size >= 4)
        rows.forEach { row ->
            assertTrue(row.code.startsWith("DEMO"))
            assertTrue(row.counterpartyName.orEmpty().contains("Demo") ||
                row.counterpartyName.orEmpty().contains("Sample"))
            assertTrue(row.rawBody.contains("Not a PIN"))
            assertTrue(row.rawBody.contains("Not a real message"))
        }
        val till = rows.first { it.code == "DEMO04" }
        assertEquals("Demo Grocers", till.counterpartyName)
        assertEquals("123456", till.counterpartyPhone)
        val paybill = rows.first { it.code == "DEMO03" }
        assertEquals("Demo Paybill", paybill.counterpartyName)
        assertEquals("888888", paybill.counterpartyPhone)
    }
}
