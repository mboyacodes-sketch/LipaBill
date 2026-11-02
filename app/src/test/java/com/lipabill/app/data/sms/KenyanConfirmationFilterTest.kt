package com.lipabill.app.data.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KenyanConfirmationFilterTest {

    @Test
    fun credit_with_amount_is_a_confirmation() {
        assertTrue(
            KenyanConfirmationFilter.isMoneyConfirmation(
                "Dear Customer, your A/C 123XXXX789 has been credited with Ksh 5,000.00. " +
                    "Available bal is Ksh 12,345.00. Ref: FT21234ABC."
            )
        )
    }

    @Test
    fun otp_is_rejected_even_when_ksh_appears() {
        assertTrue(
            KenyanConfirmationFilter.isOtpOrPin(
                "Your KCB verification code is 123456. Do not share this code."
            )
        )
        assertFalse(
            KenyanConfirmationFilter.isMoneyConfirmation(
                "Your KCB OTP is 123456. Do not share this code. Ksh 0."
            )
        )
    }

    @Test
    fun promo_without_movement_is_rejected() {
        assertFalse(
            KenyanConfirmationFilter.isMoneyConfirmation(
                "Get 10% off this weekend. Dial *544# for more. KCB"
            )
        )
    }

    @Test
    fun confirmed_airtel_receipt_is_accepted() {
        assertTrue(
            KenyanConfirmationFilter.isMoneyConfirmation(
                "Confirmed. You have sent Ksh 200.00 to JANE DOE. New T-Kash balance is Ksh 1,000.00."
            )
        )
    }

    @Test
    fun kcb_transfer_and_processed_bill_are_accepted() {
        assertTrue(
            KenyanConfirmationFilter.isMoneyConfirmation(
                "Dear Customer you have successfully transferred KES 620.00 to 254712345678-JANE DOE " +
                    "on 12/05/2026 at 4:05 PM. M-PESA ref ABC123XYZ."
            )
        )
        assertTrue(
            KenyanConfirmationFilter.isMoneyConfirmation(
                "KPLC TOKEN 123 request of KES 2,000.00 from 122****604 to KPLC has been processed successfully."
            )
        )
    }

    @Test
    fun airtel_money_send_without_confirmed_is_accepted() {
        assertTrue(
            KenyanConfirmationFilter.isMoneyConfirmation(
                "43RAMS4FHNH. Ksh 70 sent to JANE DOE 254712345678 on 16/09/26 at 01:31 PM. " +
                    "Fee: Ksh 0. Bal: Ksh 66.0. MPESA ID: UIGRF6VRCN"
            )
        )
    }
}
