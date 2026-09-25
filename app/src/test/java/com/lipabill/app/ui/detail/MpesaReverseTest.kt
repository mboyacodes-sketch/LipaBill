package com.lipabill.app.ui.detail

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun truncate_keeps_through_timestamp_drops_balance() {
        val raw =
            "THX123 Confirmed. Ksh200.00 paid to SHOP on 15/3/24 at 9:05 AM. " +
                "New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00."
        val truncated = truncateAtTimestamp(raw)
        assertEquals(
            "THX123 Confirmed. Ksh200.00 paid to SHOP on 15/3/24 at 9:05 AM.",
            truncated
        )
    }

    @Test
    fun truncate_null_without_timestamp() {
        assertNull(truncateAtTimestamp("No date here"))
        assertNull(truncateAtTimestamp(""))
    }

    @Test
    fun share_prefers_truncated_sms() {
        val raw =
            "REF99 Confirmed. Ksh50.00 sent to ANN on 2/2/24 at 8:00 PM. New M-PESA balance is Ksh9.00."
        val share = shareTextFor(tx(rawBody = raw))
        assertEquals("REF99 Confirmed. Ksh50.00 sent to ANN on 2/2/24 at 8:00 PM.", share)
        assertFalse(share.contains("balance", ignoreCase = true))
    }

    @Test
    fun share_falls_back_to_structured_when_body_empty() {
        val share = shareTextFor(tx(rawBody = ""))
        assertTrue(share.contains("Ref: ABC123"))
        assertTrue(share.contains("To: JOHN"))
        assertTrue(share.contains("Date:"))
    }
}
