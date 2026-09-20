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
}
