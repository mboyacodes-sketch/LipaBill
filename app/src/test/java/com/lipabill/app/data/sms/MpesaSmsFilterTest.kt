package com.lipabill.app.data.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpesaSmsFilterTest {

    @Test
    fun accepts_mpesa_sender_variants() {
        assertTrue(MpesaSmsFilter.isMpesaSender("MPESA"))
        assertTrue(MpesaSmsFilter.isMpesaSender("mpesa"))
        assertTrue(MpesaSmsFilter.isMpesaSender("M-PESA"))
        assertTrue(MpesaSmsFilter.isMpesaSender(" M-Pesa "))
    }

    @Test
    fun rejects_non_mpesa_senders() {
        assertFalse(MpesaSmsFilter.isMpesaSender("Safaricom"))
        assertFalse(MpesaSmsFilter.isMpesaSender("254700000000"))
        assertFalse(MpesaSmsFilter.isMpesaSender("MPESA Promo"))
        assertFalse(MpesaSmsFilter.isMpesaSender("INFO-MPESA"))
        assertFalse(MpesaSmsFilter.isMpesaSender(""))
        assertFalse(MpesaSmsFilter.isMpesaSender(null))
    }

    @Test
    fun accepts_confirmed_balance_bodies() {
        assertTrue(
            MpesaSmsFilter.isTransactionConfirmation(
                "THX7K2LM9P Confirmed. Ksh1,500.00 sent to JOHN. New M-PESA balance is Ksh12,450.50."
            )
        )
        assertTrue(
            MpesaSmsFilter.isTransactionConfirmation(
                "ABC Confirmed. paid to SHOP. New MPESA balance is Ksh100.00."
            )
        )
    }

    @Test
    fun rejects_bodies_missing_confirmed_or_balance() {
        assertFalse(MpesaSmsFilter.isTransactionConfirmation("Your new M-PESA PIN is 1234"))
        assertFalse(
            MpesaSmsFilter.isTransactionConfirmation(
                "THX Confirmed. Something happened with Ksh99.00 today."
            )
        )
        assertFalse(
            MpesaSmsFilter.isTransactionConfirmation(
                "Reminder: your M-PESA balance is Ksh100. Open the app for details."
            )
        )
        assertFalse(MpesaSmsFilter.isTransactionConfirmation(""))
        assertFalse(MpesaSmsFilter.isTransactionConfirmation(null))
    }

    @Test
    fun rejects_otp_and_promo_style_bodies() {
        assertFalse(
            MpesaSmsFilter.isTransactionConfirmation(
                "Your M-PESA verification code is 482910. Do not share."
            )
        )
        assertFalse(
            MpesaSmsFilter.isTransactionConfirmation(
                "MPESA: Get 50%% airtime bonus this weekend. Dial *544#."
            )
        )
        assertFalse(
            MpesaSmsFilter.isTransactionConfirmation(
                "Confirmed. You requested a new M-PESA PIN. Reply YES to continue."
            )
        )
    }

    @Test
    fun pipeline_requires_sender_and_confirmation_together() {
        // Conceptual gate used by receiver/inbox: both checks must pass.
        val legitBody =
            "THX7K2LM9P Confirmed. Ksh500.00 sent to JANE. New M-PESA balance is Ksh9,000.00."
        assertTrue(MpesaSmsFilter.isMpesaSender("MPESA"))
        assertTrue(MpesaSmsFilter.isTransactionConfirmation(legitBody))

        assertFalse(MpesaSmsFilter.isMpesaSender("254712345678"))
        assertTrue(MpesaSmsFilter.isTransactionConfirmation(legitBody))

        assertTrue(MpesaSmsFilter.isMpesaSender("MPESA"))
        assertFalse(
            MpesaSmsFilter.isTransactionConfirmation("MPESA OTP 123456 Confirmed by you.")
        )
    }
}
