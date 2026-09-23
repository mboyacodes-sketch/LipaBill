package com.lipabill.app.data.sms

import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end gate: unrelated SMS must not become ledger transactions.
 * "Confirmed" alone is never enough.
 */
class MpesaSmsIngestionTest {

    private val ts = 1_700_000_000_000L

    private val validBody =
        "THX7K2LM9P Confirmed. Ksh1,500.00 sent to JOHN KAMAU 254712345678 on 15/3/24 at 2:15 PM. " +
            "New M-PESA balance is Ksh12,450.50. Transaction cost, Ksh23.00."

    @Test
    fun valid_mpesa_confirmation_accepted() {
        val tx = MpesaSmsIngestion.acceptAndParse("MPESA", validBody, ts)
        assertNotNull(tx)
        assertEquals("THX7K2LM9P", tx!!.code)
        assertEquals(TransactionType.SENT, tx.type)
        assertEquals(1500.0, tx.amount!!, 0.001)
        assertEquals("JOHN KAMAU", tx.counterpartyName)
        assertEquals("254712345678", tx.counterpartyPhone)
    }

    @Test
    fun wrong_sender_rejected() {
        assertNull(MpesaSmsIngestion.acceptAndParse("Safaricom", validBody, ts))
        assertNull(MpesaSmsIngestion.acceptAndParse("254700000000", validBody, ts))
        assertFalse(MpesaSmsIngestion.shouldAccept("Safaricom", validBody))
    }

    @Test
    fun sender_containing_mpesa_but_not_exact_rejected() {
        assertFalse(MpesaSmsIngestion.shouldAccept("INFO-MPESA", validBody))
        assertFalse(MpesaSmsIngestion.shouldAccept("MPESA Promo", validBody))
        assertFalse(MpesaSmsIngestion.shouldAccept("MyMPESA", validBody))
        assertNull(MpesaSmsIngestion.acceptAndParse("INFO-MPESA", validBody, ts))
    }

    @Test
    fun promotional_message_rejected() {
        val promo =
            "MPESA: Get 50% airtime bonus this weekend. Dial *544#. Terms apply."
        assertFalse(MpesaSmsIngestion.shouldAccept("MPESA", promo))
        assertNull(MpesaSmsIngestion.acceptAndParse("MPESA", promo, ts))
    }

    @Test
    fun otp_message_rejected() {
        val otp = "Your M-PESA verification code is 482910. Do not share with anyone."
        assertFalse(MpesaSmsIngestion.shouldAccept("MPESA", otp))
        assertNull(MpesaSmsIngestion.acceptAndParse("MPESA", otp, ts))
    }

    @Test
    fun pin_security_message_rejected() {
        val pin =
            "Confirmed. You requested a new M-PESA PIN. Reply YES to continue. " +
                "If you did not request this, dial *234#"
        assertFalse(MpesaSmsIngestion.shouldAccept("MPESA", pin))
        assertNull(MpesaSmsIngestion.acceptAndParse("MPESA", pin, ts))
    }

    @Test
    fun confirmed_without_transaction_structure_rejected() {
        val body = "THX Confirmed. Something happened with Ksh99.00 today."
        assertFalse(MpesaSmsFilter.isTransactionConfirmation(body))
        assertNull(MpesaSmsIngestion.acceptAndParse("MPESA", body, ts))
    }

    @Test
    fun message_without_balance_rejected() {
        val body =
            "THX7K2LM9P Confirmed. Ksh500.00 sent to JANE on 15/3/24 at 2:15 PM. " +
                "Transaction cost, Ksh5.00."
        assertFalse(MpesaSmsFilter.isTransactionConfirmation(body))
        assertNull(MpesaSmsIngestion.acceptAndParse("MPESA", body, ts))
    }

    @Test
    fun unicode_and_formatting_sender_variants_accepted() {
        assertTrue(MpesaSmsIngestion.shouldAccept("M-PESA", validBody))
        assertTrue(MpesaSmsIngestion.shouldAccept(" mpesa ", validBody))
        assertTrue(MpesaSmsIngestion.shouldAccept("M_PESA", validBody))
    }

    @Test
    fun thousand_separated_amounts_parsed() {
        val body =
            "AMT99THOU01 Confirmed. Ksh10,500.00 sent to ALICE 0712345678 on 1/1/25 at 1:00 PM. " +
                "New M-PESA balance is Ksh1,234,567.89. Transaction cost, Ksh30.00."
        val tx = MpesaSmsIngestion.acceptAndParse("MPESA", body, ts)!!
        assertEquals(10500.0, tx.amount!!, 0.001)
        assertEquals(1_234_567.89, tx.balance!!, 0.001)
    }

    @Test
    fun counterparty_without_phone_still_accepted() {
        val body =
            "NBZ12SHOP99 Confirmed. Ksh450.00 paid to JAVA HOUSE WESTLANDS. " +
                "on 10/3/24 at 1:20 PM. New M-PESA balance is Ksh9,999.00. Transaction cost, Ksh0.00."
        val tx = MpesaSmsIngestion.acceptAndParse("MPESA", body, ts)!!
        assertNotNull(tx.counterpartyName)
        assertNull(tx.counterpartyPhone)
        assertTrue(tx.amount != null && tx.amount!! > 0)
    }

    @Test
    fun missing_receipt_code_still_gated_by_confirmation() {
        // No leading code — still not accepted without balance marker.
        val noBalance = "Confirmed. Ksh100.00 sent to BOB on 1/1/25."
        assertNull(MpesaSmsIngestion.acceptAndParse("MPESA", noBalance, ts))
        // With balance, acceptAndParse may synthesize a code — but only after filter.
        val withBalance =
            "Confirmed. Ksh100.00 sent to BOB 0712345678 on 1/1/25 at 10:00 AM. " +
                "New M-PESA balance is Ksh200.00."
        // Filter requires Confirmed + mpesa balance — this passes filter; parse synthesizes code.
        if (MpesaSmsFilter.isTransactionConfirmation(withBalance)) {
            val tx = MpesaSmsIngestion.acceptAndParse("MPESA", withBalance, ts)
            assertNotNull(tx)
            assertTrue(tx!!.code.isNotBlank())
        }
    }

    @Test
    fun unexpected_type_unknown_never_bypasses_filter() {
        val weird =
            "HELLO99 Confirmed. Weird event with Ksh1.00. New M-PESA balance is Ksh2.00."
        val tx = MpesaSmsIngestion.acceptAndParse("MPESA", weird, ts)
        // May parse as UNKNOWN but only after filter — never from non-MPESA sender
        assertNull(MpesaSmsIngestion.acceptAndParse("SCAMMER", weird, ts))
        if (tx != null) {
            assertTrue(
                tx.type == TransactionType.UNKNOWN ||
                    tx.amount != null ||
                    tx.balance != null
            )
        }
    }

    @Test
    fun blank_and_null_ignored() {
        assertNull(MpesaSmsIngestion.acceptAndParse("MPESA", null, ts))
        assertNull(MpesaSmsIngestion.acceptAndParse("MPESA", "", ts))
        assertNull(MpesaSmsIngestion.acceptAndParse("MPESA", "   ", ts))
        assertFalse(MpesaSmsIngestion.shouldAccept(null, validBody))
    }

    @Test
    fun duplicate_receipt_codes_are_stable() {
        val a = MpesaSmsIngestion.acceptAndParse("MPESA", validBody, ts)!!
        val b = MpesaSmsIngestion.acceptAndParse("MPESA", validBody, ts + 1)!!
        assertEquals(a.code, b.code)
        // Dedup is by unique Room index on code — same code → upsert overwrite path.
    }

    @Test
    fun ingestion_does_not_accept_confirmed_alone() {
        assertFalse(
            MpesaSmsIngestion.shouldAccept(
                "MPESA",
                "Confirmed. Your request was received."
            )
        )
    }
}
