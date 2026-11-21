package com.lipabill.app.data.sms

import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ussd.PendingPaymentReceipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureLedgerMatchTest {

    @Test
    fun extract_amount_from_coop_failed_deposit() {
        val amount = FailureSms.extractAmount(
            "Transaction FAILED. Dear Fred, your M-PESA to Account Deposit of KES 50000.00 " +
                "from 0110173557 could not be completed."
        )
        assertEquals(50000.0, amount!!, 0.001)
    }

    @Test
    fun voids_recent_same_amount_coop_deposit() {
        val failureAt = 1_700_000_000_000L
        val row = tx(
            id = 9,
            amount = 50_000.0,
            source = MoneySource.COOP,
            type = TransactionType.DEPOSIT,
            phone = "0110173557",
            at = failureAt - 60_000L
        )
        val hit = FailureLedgerMatch.findVoidCandidate(
            amount = 50_000.0,
            source = MoneySource.COOP,
            partyDigits = setOf("0110173557"),
            candidates = listOf(row),
            failureAtMillis = failureAt
        )
        assertNotNull(hit)
        assertEquals(9L, hit!!.id)
    }

    @Test
    fun bank_failure_does_not_void_wallet_row_without_cross_rail_rule() {
        val failureAt = 1_700_000_000_000L
        val row = tx(
            id = 3,
            amount = 50_000.0,
            source = MoneySource.MPESA,
            type = TransactionType.PAYBILL,
            at = failureAt - 30_000L
        )
        assertNull(
            FailureLedgerMatch.findVoidCandidate(
                amount = 50_000.0,
                source = MoneySource.COOP,
                partyDigits = emptySet(),
                candidates = listOf(row),
                failureAtMillis = failureAt
            )
        )
    }

    @Test
    fun mpesa_failure_voids_coop_deposit_when_party_digits_match() {
        val failureAt = 1_700_000_000_000L
        val coop = tx(
            id = 9,
            amount = 50_000.0,
            source = MoneySource.COOP,
            type = TransactionType.DEPOSIT,
            phone = "0110173557",
            at = failureAt - 60_000L
        )
        val hit = FailureLedgerMatch.findVoidCandidate(
            amount = 50_000.0,
            source = MoneySource.MPESA,
            partyDigits = setOf("0110173557"),
            candidates = listOf(coop),
            failureAtMillis = failureAt
        )
        assertNotNull(hit)
        assertEquals(9L, hit!!.id)
    }

    @Test
    fun mpesa_failure_does_not_void_coop_without_party_digits() {
        val failureAt = 1_700_000_000_000L
        val coop = tx(
            id = 9,
            amount = 50_000.0,
            source = MoneySource.COOP,
            type = TransactionType.DEPOSIT,
            phone = "0110173557",
            at = failureAt - 60_000L
        )
        assertNull(
            FailureLedgerMatch.findVoidCandidate(
                amount = 50_000.0,
                source = MoneySource.MPESA,
                partyDigits = emptySet(),
                candidates = listOf(coop),
                failureAtMillis = failureAt
            )
        )
    }

    @Test
    fun unmapped_sender_voids_when_party_digits_match() {
        val failureAt = 1_700_000_000_000L
        val coop = tx(
            id = 4,
            amount = 50_000.0,
            source = MoneySource.COOP,
            type = TransactionType.DEPOSIT,
            phone = "0110173557",
            at = failureAt - 45_000L
        )
        val hit = FailureLedgerMatch.findVoidCandidate(
            amount = 50_000.0,
            source = null,
            partyDigits = setOf("0110173557"),
            candidates = listOf(coop),
            failureAtMillis = failureAt
        )
        assertNotNull(hit)
        assertEquals(4L, hit!!.id)
    }

    @Test
    fun unmapped_sender_does_not_void_on_amount_alone() {
        val failureAt = 1_700_000_000_000L
        val coop = tx(
            id = 4,
            amount = 50_000.0,
            source = MoneySource.COOP,
            type = TransactionType.DEPOSIT,
            phone = "0110173557",
            at = failureAt - 45_000L
        )
        assertNull(
            FailureLedgerMatch.findVoidCandidate(
                amount = 50_000.0,
                source = null,
                partyDigits = emptySet(),
                candidates = listOf(coop),
                failureAtMillis = failureAt
            )
        )
    }

    @Test
    fun prefers_same_rail_over_cross_rail() {
        val failureAt = 1_700_000_000_000L
        val mpesa = tx(
            id = 1,
            amount = 50_000.0,
            source = MoneySource.MPESA,
            type = TransactionType.PAYBILL,
            phone = "0110173557",
            at = failureAt - 30_000L
        )
        val coop = tx(
            id = 2,
            amount = 50_000.0,
            source = MoneySource.COOP,
            type = TransactionType.DEPOSIT,
            phone = "0110173557",
            at = failureAt - 20_000L
        )
        val hit = FailureLedgerMatch.findVoidCandidate(
            amount = 50_000.0,
            source = MoneySource.MPESA,
            partyDigits = setOf("0110173557"),
            candidates = listOf(mpesa, coop),
            failureAtMillis = failureAt
        )
        assertEquals(1L, hit!!.id)
    }

    @Test
    fun amount_only_same_rail_refuses_when_several_rows_match() {
        val failureAt = 1_700_000_000_000L
        val a = tx(1, 500.0, MoneySource.MPESA, TransactionType.BUY_GOODS, at = failureAt - 30_000L)
        val b = tx(2, 500.0, MoneySource.MPESA, TransactionType.SENT, phone = "254711111111", at = failureAt - 20_000L)
        assertNull(
            FailureLedgerMatch.findVoidCandidate(
                amount = 500.0,
                source = MoneySource.MPESA,
                partyDigits = emptySet(),
                candidates = listOf(a, b),
                failureAtMillis = failureAt
            )
        )
        assertNotNull(
            FailureLedgerMatch.findVoidCandidate(
                amount = 500.0,
                source = MoneySource.MPESA,
                partyDigits = emptySet(),
                candidates = listOf(a),
                failureAtMillis = failureAt
            )
        )
    }

    @Test
    fun open_payment_matches_amount_without_party_digits() {
        val snap = PendingPaymentReceipt.Snapshot(
            auditId = 1L,
            amount = 200.0,
            type = TransactionType.PAYBILL,
            counterpartyName = "KPLC",
            counterpartyPhone = "888880",
            accountHint = "12345",
            startedAtMillis = System.currentTimeMillis()
        )
        assertTrue(FailureLedgerMatch.matchesOpenPayment(200.0, emptySet(), snap))
        assertFalse(FailureLedgerMatch.matchesOpenPayment(201.0, emptySet(), snap))
    }

    private fun tx(
        id: Long,
        amount: Double,
        source: MoneySource,
        type: TransactionType,
        phone: String? = null,
        at: Long
    ) = MpesaTransaction(
        id = id,
        code = "T$id",
        type = type,
        amount = amount,
        counterpartyName = null,
        counterpartyPhone = phone,
        timestampMillis = at,
        balance = null,
        cost = null,
        rawBody = "",
        source = source
    )
}
