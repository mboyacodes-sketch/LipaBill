package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FulizaStatusTest {

    private val hundred =
        "UI3E552R94 Confirmed. Fuliza M-PESA amount is Ksh 100.00. Access Fee charged Ksh 1.00. " +
            "Total Fuliza M-PESA outstanding amount is Ksh192.97 due on 02/10/26. " +
            "To check daily charges, Dial *334#OK Select Query Charges"
    private val forty =
        "UI3E551JPG Confirmed. Fuliza M-PESA amount is Ksh 40.00. Access Fee charged Ksh 0.40. " +
            "Total Fuliza M-PESA outstanding amount is Ksh61.67 due on 02/10/26. " +
            "To check daily charges, Dial *334#OK Select Query Charges"

    @Test
    fun due_date_is_day_month_year() {
        assertEquals(LocalDate.of(2026, 10, 2), fulizaDueDate(hundred))
        assertNull(fulizaDueDate("Confirmed. No date here"))
    }

    @Test
    fun out_of_order_burst_uses_the_later_receipt_code() {
        // The later draw (552R94) arrived a minute before the earlier one (551JPG).
        val rows = listOf(
            draw("UI3E552R94", 192.97, minute = 33, body = hundred),
            draw("UI3E551JPG", 61.67, minute = 34, body = forty)
        )
        val status = latestFulizaStatus(rows)!!
        assertEquals(192.97, status.outstanding, 0.001)
        assertEquals(LocalDate.of(2026, 10, 2), status.dueDate)
    }

    @Test
    fun texts_far_apart_use_the_newest() {
        val rows = listOf(
            draw("UI3E552R94", 192.97, minute = 0, body = hundred),
            draw("UI4A000001", 50.0, minute = 120, body = forty)
        )
        assertEquals(50.0, latestFulizaStatus(rows)!!.outstanding, 0.001)
    }

    @Test
    fun due_copy_reads_naturally() {
        val today = LocalDate.of(2026, 10, 1)
        assertEquals("due 2 Oct", fulizaDueCopy(LocalDate.of(2026, 10, 2), today))
        assertEquals("due today", fulizaDueCopy(today, today))
        assertEquals("was due 30 Sep", fulizaDueCopy(LocalDate.of(2026, 9, 30), today))
    }

    private fun draw(code: String, outstanding: Double, minute: Int, body: String) = MpesaTransaction(
        code = code,
        type = TransactionType.FULIZA,
        amount = 0.0,
        counterpartyName = "Due 02/10/26",
        counterpartyPhone = null,
        timestampMillis = 1_790_000_000_000L + minute * 60_000L,
        balance = outstanding,
        cost = null,
        rawBody = body
    )
}
