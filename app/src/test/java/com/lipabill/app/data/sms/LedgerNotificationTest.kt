package com.lipabill.app.data.sms

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerNotificationTest {

    @Test
    fun received_names_the_sender() {
        val (title, body) = ledgerNotificationCopy(
            sample(TransactionType.RECEIVED, "JANE WANJIKU", "0712345678")
        )
        assertEquals("Received · Ksh 500.00", title)
        assertEquals("From JANE WANJIKU", body)
    }

    @Test
    fun sent_names_the_recipient() {
        val (_, body) = ledgerNotificationCopy(
            sample(TransactionType.SENT, "PETER KAMAU", null)
        )
        assertEquals("To PETER KAMAU", body)
    }

    @Test
    fun fuliza_keeps_the_due_line() {
        val (_, body) = ledgerNotificationCopy(
            sample(TransactionType.FULIZA, "Due 02/10/26", null)
        )
        assertEquals("Due 02/10/26", body)
    }

    @Test
    fun fresh_window_skips_history() {
        val now = 1_700_000_000_000L
        assertTrue(LedgerNotifier.isFresh(now - 60_000L, now))
        assertFalse(LedgerNotifier.isFresh(now - LedgerNotifier.FRESH_WINDOW_MS - 1, now))
    }

    private fun sample(type: TransactionType, name: String?, phone: String?) = MpesaTransaction(
        code = "ABC123",
        type = type,
        amount = 500.0,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = 0L,
        balance = null,
        cost = null,
        rawBody = ""
    )
}
