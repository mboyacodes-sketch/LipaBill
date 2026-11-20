package com.lipabill.app.data.sms

import com.lipabill.app.data.model.AppRegion
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

/**
 * End-to-end style checks for every failure path we rely on:
 * gate reject, inbox read-through, amount extract, void match, open dial.
 */
class FailurePathCoverageTest {

    private val failureAt = 1_700_000_000_000L

    @Test
    fun gate_rejects_failure_bodies_across_rails() {
        val samples = listOf(
            "COOPBANK" to
                "Transaction FAILED. Dear Fred, your M-PESA to Account Deposit of KES 50000.00 " +
                "from 0110173557 could not be completed.",
            "MPESA" to "Failed. Insufficient funds in your M-PESA account to pay Ksh50,000.00 to COOP.",
            "MPESA" to "Dear customer, you do not have sufficient funds to complete this transaction. Ksh 200.00",
            "MPESA" to "M-PESA is unable to process your request. Ksh 1,000.00",
            "AIRTELMONEY" to "Transaction failed. Ksh 500.00 to JANE DOE was unsuccessful.",
            "EQUITY" to "Dear Customer, your transaction of KES 2,000.00 was not successful.",
            "NCBA" to "Your M-Pesa payment of KES 1,500.00 was declined.",
            "KCB" to "Transaction timed out. KES 800.00 could not be completed.",
            "MTN" to "Failed. Insufficient funds. UGX 10000 to 256700000000."
        )
        for ((sender, body) in samples) {
            assertTrue("expected failure: $sender", FailureSms.isFailure(body))
            assertFalse(
                "must not accept as confirmation: $sender",
                ConfirmationIngestion.shouldAccept(sender, body, regionFor(sender), regionFor(sender).sources)
            )
        }
    }

    @Test
    fun inbox_still_reads_failure_sms_for_void_path() {
        val coopFail =
            "Transaction FAILED. Dear Fred, your M-PESA to Account Deposit of KES 50000.00 " +
                "from 0110173557 could not be completed."
        assertTrue(
            ConfirmationIngestion.shouldReadFromInbox(
                "COOPBANK",
                coopFail,
                AppRegion.KENYA,
                AppRegion.KENYA.sources
            )
        )
        assertFalse(
            ConfirmationIngestion.shouldAccept("COOPBANK", coopFail, AppRegion.KENYA)
        )
    }

    @Test
    fun kenya_mpesa_confirmed_success_still_accepted() {
        val ok =
            "ABC123XYZ Confirmed. Ksh1,500.00 sent to JOHN 254712345678 on 15/3/24 at 2:15 PM. " +
                "New M-PESA balance is Ksh12,450.50. Transaction cost, Ksh23.00."
        assertTrue(ConfirmationIngestion.shouldAccept("MPESA", ok, AppRegion.KENYA))
        assertFalse(FailureSms.isFailure(ok))
    }

    @Test
    fun real_deposit_without_failure_wording_still_accepted() {
        val ok =
            "Dear Customer, KES 5,000.00 has been deposited to Acc. ending with 1234 " +
                "from JANE DOE. Available bal is KES 12,000.00. Ref FT99ABC."
        assertTrue(ConfirmationIngestion.shouldAccept("COOPBANK", ok, AppRegion.KENYA))
        assertFalse(FailureSms.isFailure(ok))
    }

    @Test
    fun amount_extract_works_for_glued_and_spaced_currency() {
        assertEquals(
            50_000.0,
            FailureSms.extractAmount("Insufficient funds to pay Ksh50,000.00.")!!,
            0.001
        )
        assertEquals(
            50_000.0,
            FailureSms.extractAmount("Deposit of KES 50000.00 failed.")!!,
            0.001
        )
        assertEquals(
            10_000.0,
            FailureSms.extractAmount("Failed. UGX 10000 insufficient funds.", AppRegion.UGANDA)!!,
            0.001
        )
    }

    @Test
    fun mpesa_insufficient_voids_coop_when_party_present() {
        val coop = row(
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
    fun airtel_failure_voids_same_rail_paybill() {
        val row = row(
            id = 2,
            amount = 500.0,
            source = MoneySource.AIRTEL_MONEY,
            type = TransactionType.PAYBILL,
            phone = "888880",
            at = failureAt - 10_000L
        )
        val hit = FailureLedgerMatch.findVoidCandidate(
            amount = 500.0,
            source = MoneySource.AIRTEL_MONEY,
            partyDigits = emptySet(),
            candidates = listOf(row),
            failureAtMillis = failureAt
        )
        assertNotNull(hit)
    }

    @Test
    fun bank_failure_does_not_void_unrelated_wallet_without_party() {
        val mpesa = row(
            id = 1,
            amount = 50_000.0,
            source = MoneySource.MPESA,
            type = TransactionType.SENT,
            at = failureAt - 5_000L
        )
        assertNull(
            FailureLedgerMatch.findVoidCandidate(
                amount = 50_000.0,
                source = MoneySource.EQUITY,
                partyDigits = emptySet(),
                candidates = listOf(mpesa),
                failureAtMillis = failureAt
            )
        )
    }

    @Test
    fun open_dial_marks_failed_on_amount_match() {
        val snap = PendingPaymentReceipt.Snapshot(
            auditId = 42L,
            amount = 200.0,
            type = TransactionType.PAYBILL,
            counterpartyName = "KPLC",
            counterpartyPhone = "888880",
            accountHint = "ACC1",
            startedAtMillis = failureAt - 30_000L
        )
        assertTrue(
            FailureLedgerMatch.matchesOpenPayment(
                amount = 200.0,
                partyDigits = emptySet(),
                snapshot = snap,
                failureAtMillis = failureAt
            )
        )
    }

    @Test
    fun looks_like_stored_confirmation_false_for_failed_deposit_body() {
        val body =
            "Transaction FAILED. Dear Fred, your M-PESA to Account Deposit of KES 50000.00 " +
                "from 0110173557 could not be completed."
        assertFalse(ConfirmationIngestion.looksLikeStoredConfirmation(MoneySource.COOP, body))
    }

    @Test
    fun inbox_body_query_includes_failure_likes() {
        val (selection, args) = SmsInboxReader.bodyQuery(AppRegion.KENYA)
        assertTrue(args.any { it.contains("failed", ignoreCase = true) })
        assertTrue(args.any { it.contains("insufficient", ignoreCase = true) })
        assertTrue(selection.contains("LIKE"))
    }

    @Test
    fun disabled_rail_failure_is_not_read() {
        val body =
            "Transaction FAILED. Dear Fred, your M-PESA to Account Deposit of KES 50000.00 " +
                "from 0110173557 could not be completed."
        assertFalse(
            ConfirmationIngestion.shouldReadFromInbox(
                "COOPBANK",
                body,
                AppRegion.KENYA,
                setOf(MoneySource.MPESA)
            )
        )
    }

    private fun regionFor(sender: String): AppRegion =
        when {
            sender.equals("MTN", ignoreCase = true) -> AppRegion.UGANDA
            else -> AppRegion.KENYA
        }

    private fun row(
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
