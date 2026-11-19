package com.lipabill.app.ussd

import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentManualDetailsTest {

    @Test
    fun format_includes_dial_code_and_manual_path() {
        val text = PaymentManualDetails.format(
            typeLabel = "Send",
            toLine = "Ada 0712345678",
            amountLabel = "KES 100",
            planDescription = "Send money → phone → amount",
            simLabel = "SIM 1 · Safaricom"
        )
        assertTrue(text.contains("Dial ${UssdMenuBuilder.USSD_CODE}"))
        assertTrue(text.contains("Ada 0712345678"))
        assertTrue(text.contains("KES 100"))
        assertTrue(text.contains("complete the mobile money menus yourself", ignoreCase = true))
        assertTrue(text.contains("automatic repeat", ignoreCase = true))
        assertTrue(text.contains("PIN is entered in mobile money", ignoreCase = true))
        assertTrue(text.contains("does not store", ignoreCase = true))
    }

    @Test
    fun format_uses_wallet_ussd_and_badge() {
        val text = PaymentManualDetails.format(
            typeLabel = "Send",
            toLine = "Ada",
            amountLabel = "UGX 1000",
            planDescription = null,
            simLabel = null,
            ussdCode = "*185#",
            walletBadge = "Airtel Money"
        )
        assertTrue(text.contains("Dial *185#"))
        assertTrue(text.contains("Airtel Money", ignoreCase = true))
    }
}
