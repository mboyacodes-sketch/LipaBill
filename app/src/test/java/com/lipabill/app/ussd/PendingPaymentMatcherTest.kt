package com.lipabill.app.ussd

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPaymentMatcherTest {

    private val started = 1_700_000_000_000L

    private fun snapshot(
        amount: Double = 100.0,
        type: TransactionType = TransactionType.SENT,
        phone: String? = "0712345678",
        name: String? = "Jane Doe",
        account: String? = null
    ) = PendingPaymentReceipt.Snapshot(
        auditId = 1L,
        amount = amount,
        type = type,
        counterpartyName = name,
        counterpartyPhone = phone,
        accountHint = account,
        startedAtMillis = started
    )

    private fun tx(
        amount: Double = 100.0,
        type: TransactionType = TransactionType.SENT,
        phone: String? = "254712345678",
        name: String? = "JANE DOE",
        raw: String = "Confirmed. Ksh100.00 sent to Jane",
        time: Long = started + 5_000L
    ) = MpesaTransaction(
        id = 9,
        code = "ABC123XYZ",
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = time,
        balance = 50.0,
        cost = 0.0,
        rawBody = raw
    )

    @Test
    fun matches_send_by_amount_type_and_phone_suffix() {
        assertTrue(PendingPaymentMatcher.matches(snapshot(), tx()))
    }

    @Test
    fun rejects_wrong_amount() {
        assertFalse(PendingPaymentMatcher.matches(snapshot(), tx(amount = 200.0)))
    }

    @Test
    fun rejects_wrong_type() {
        assertFalse(
            PendingPaymentMatcher.matches(
                snapshot(type = TransactionType.SENT),
                tx(type = TransactionType.PAYBILL)
            )
        )
    }

    @Test
    fun requires_account_when_hint_present() {
        val snap = snapshot(
            type = TransactionType.PAYBILL,
            phone = "888880",
            account = "ACC99"
        )
        assertFalse(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.PAYBILL,
                    phone = "888880",
                    raw = "Confirmed. Paid to Biz Acc other"
                )
            )
        )
        assertTrue(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.PAYBILL,
                    phone = "888880",
                    raw = "Confirmed. Paid to Biz Account ACC99"
                )
            )
        )
    }

    @Test
    fun findMatch_returns_first_hit() {
        val snap = snapshot()
        assertNotNull(
            PendingPaymentMatcher.findMatch(
                snap,
                listOf(tx(amount = 50.0), tx(amount = 100.0))
            )
        )
        assertNull(
            PendingPaymentMatcher.findMatch(snap, listOf(tx(amount = 1.0)))
        )
    }
}
