package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SendInsightsTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun fee_comes_from_the_nearest_past_send() {
        val history = listOf(
            send(1_000.0, cost = 13.0),
            send(2_400.0, cost = 33.0),
            send(10_000.0, cost = 87.0)
        )
        assertEquals(33.0, estimatedSendFee(history, 2_500.0)!!, 0.001)
        assertEquals(13.0, estimatedSendFee(history, 1_050.0)!!, 0.001)
    }

    @Test
    fun no_fee_when_nothing_close_is_known() {
        assertNull(estimatedSendFee(listOf(send(10_000.0, cost = 87.0)), 2_500.0))
        assertNull(estimatedSendFee(listOf(send(2_500.0, cost = null)), 2_500.0))
        assertNull(estimatedSendFee(emptyList(), 500.0))
    }

    @Test
    fun counts_sends_to_the_same_number_this_month_in_either_format() {
        val today = LocalDate.of(2026, 11, 10)
        val history = listOf(
            send(500.0, phone = "0712345678", date = LocalDate.of(2026, 11, 2)),
            send(800.0, phone = "254712345678", date = LocalDate.of(2026, 11, 8)),
            send(800.0, phone = "0712345678", date = LocalDate.of(2026, 10, 30)),
            send(900.0, phone = "0799999999", date = LocalDate.of(2026, 11, 9))
        )
        assertEquals(2, sendsThisMonth(history, "+254712345678", today, zone))
    }

    @Test
    fun monthly_counts_key_by_last_nine_digits() {
        val today = LocalDate.of(2026, 11, 10)
        val counts = monthlySendCounts(
            listOf(
                send(500.0, phone = "0712345678", date = LocalDate.of(2026, 11, 2)),
                send(800.0, phone = "+254712345678", date = LocalDate.of(2026, 11, 5)),
                send(800.0, phone = "0799999999", date = LocalDate.of(2026, 10, 5))
            ),
            today,
            zone
        )
        assertEquals(mapOf("712345678" to 2), counts)
        assertEquals("sent 2 times this month", sentThisMonthCopy(2))
        assertEquals("first send this month", sentThisMonthCopy(0))
    }

    @Test
    fun phone_is_masked_in_the_middle() {
        assertEquals("07** *** 678", maskPhone("0712345678"))
        assertEquals("07** *** 678", maskPhone("254712345678"))
    }

    private fun send(
        amount: Double,
        cost: Double? = null,
        phone: String = "0712345678",
        date: LocalDate = LocalDate.of(2026, 11, 1)
    ) = MpesaTransaction(
        code = "S$amount$date$phone",
        type = TransactionType.SENT,
        amount = amount,
        counterpartyName = "Grace",
        counterpartyPhone = phone,
        timestampMillis = date.atTime(12, 0).toInstant(zone).toEpochMilli(),
        balance = null,
        cost = cost,
        rawBody = ""
    )
}
