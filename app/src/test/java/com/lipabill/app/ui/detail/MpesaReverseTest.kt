package com.lipabill.app.ui.detail

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpesaReverseTest {

    private val paidAt = 1_700_000_000_000L

    private fun tx(
        rawBody: String = "ABC123 Confirmed. Ksh100.00 sent to JOHN on 1/1/24 at 12:00 PM. New M-PESA balance is Ksh50.00.",
        timestampMillis: Long = paidAt
    ) = MpesaTransaction(
        id = 1,
        code = "ABC123",
        type = TransactionType.SENT,
        amount = 100.0,
        counterpartyName = "JOHN",
        counterpartyPhone = "0712345678",
        timestampMillis = timestampMillis,
        balance = 50.0,
        cost = 0.0,
        rawBody = rawBody
    )

    @Test
    fun reverse_allowed_within_window() {
        assertTrue(canRequestMpesaReverse(tx(), nowMs = paidAt + 1_000))
        assertTrue(canRequestMpesaReverse(tx(), nowMs = paidAt + REVERSE_WINDOW_MS - 1))
    }

    @Test
    fun reverse_denied_at_or_past_window() {
        assertFalse(canRequestMpesaReverse(tx(), nowMs = paidAt + REVERSE_WINDOW_MS))
        assertFalse(canRequestMpesaReverse(tx(), nowMs = paidAt + REVERSE_WINDOW_MS + 60_000))
    }

    @Test
    fun reverse_denied_for_future_timestamp() {
        assertFalse(canRequestMpesaReverse(tx(), nowMs = paidAt - 1))
    }

    @Test
    fun reverse_denied_when_body_blank() {
        assertFalse(canRequestMpesaReverse(tx(rawBody = "  "), nowMs = paidAt + 1_000))
    }

    @Test
    fun reverse_denied_for_incoming() {
        val now = paidAt + 1_000
        listOf(
            TransactionType.RECEIVED,
            TransactionType.DEPOSIT,
            TransactionType.REVERSED
        ).forEach { type ->
            assertFalse(canRequestMpesaReverse(tx().copy(type = type), nowMs = now))
        }
    }

    @Test
    fun share_is_the_receipt_code_only() {
        val raw =
            "REF99 Confirmed. Ksh50.00 sent to ANN on 2/2/24 at 8:00 PM. New M-PESA balance is Ksh9.00."
        val share = shareTextFor(tx(rawBody = raw))
        assertEquals("ABC123", share)
        assertFalse(share.contains("balance", ignoreCase = true))
        assertFalse(share.contains("Ksh", ignoreCase = true))
    }

    @Test
    fun share_stays_the_code_when_the_body_is_empty() {
        assertEquals("ABC123", shareTextFor(tx(rawBody = "")))
        assertEquals("ABC123", shareTextFor(tx(rawBody = "No date here and balance Ksh1")))
    }
}
