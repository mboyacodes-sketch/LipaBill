package com.lipabill.app.data.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AirtimeGiftFilterTest {

    @Test
    fun airtimeFromSomeoneIsNotMoney() {
        val gift = "You have received 20 Airtime from 0733123456. Buy Airtime easily using Airtel Money or *544#."
        assertTrue(KenyanConfirmationFilter.isAirtimeGift(gift))
        assertFalse(KenyanConfirmationFilter.isMoneyConfirmation(gift))
    }

    @Test
    fun airtelMoneyReceiptIsStillMoney() {
        val money = "TID123 Received Ksh500.00 from JANE DOE 0733123456 on 10/10/26 at 1:00 PM. New balance Ksh900.00."
        assertFalse(KenyanConfirmationFilter.isAirtimeGift(money))
        assertTrue(KenyanConfirmationFilter.isMoneyConfirmation(money))
    }
}
