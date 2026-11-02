package com.lipabill.app.data.sms

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationIngestionTest {

    private val ts = 1_700_000_000_000L

    private val mpesaBody =
        "THX7K2LM9P Confirmed. Ksh1,500.00 sent to JOHN KAMAU 254712345678 on 15/3/24 at 2:15 PM. " +
            "New M-PESA balance is Ksh12,450.50. Transaction cost, Ksh23.00."

    private val kcbBody =
        "Dear Customer, your A/C 123XXXX789 has been credited with Ksh 5,000.00 on 30/10/26. " +
            "Available bal is Ksh 12,345.00. Ref: FT21234ABC."

    @Test
    fun mpesa_still_uses_the_mpesa_parser() {
        val tx = ConfirmationIngestion.acceptAndParse("MPESA", mpesaBody, ts)
        assertNotNull(tx)
        assertEquals(MoneySource.MPESA, tx!!.source)
        assertEquals("THX7K2LM9P", tx.code)
        assertEquals(TransactionType.SENT, tx.type)
        assertEquals(1500.0, tx.amount!!, 0.001)
    }

    @Test
    fun kcb_credit_is_accepted() {
        assertTrue(ConfirmationIngestion.shouldAccept("KCB", kcbBody))
        val tx = ConfirmationIngestion.acceptAndParse("KCB", kcbBody, ts)
        assertNotNull(tx)
        assertEquals(MoneySource.KCB, tx!!.source)
        assertEquals("FT21234ABC", tx.code)
        assertEquals(TransactionType.RECEIVED, tx.type)
        assertEquals("KCB:FT21234ABC", ConfirmationIngestion.sourceKey(tx))
    }

    @Test
    fun unknown_sender_is_rejected() {
        assertFalse(ConfirmationIngestion.shouldAccept("Safaricom", mpesaBody))
        assertNull(ConfirmationIngestion.acceptAndParse("254700000000", mpesaBody, ts))
    }

    @Test
    fun otp_from_a_known_bank_is_rejected() {
        val otp = "Your KCB verification code is 123456. Do not share this code."
        assertFalse(ConfirmationIngestion.shouldAccept("KCB", otp))
        assertNull(ConfirmationIngestion.acceptAndParse("KCB", otp, ts))
    }

    @Test
    fun uganda_accepts_mtn_and_rejects_kenyan_tkash() {
        val momo =
            "You have received UGX 10,000 from JANE DOE. New MTN MoMo balance is UGX 20,000. TxnId: MM12AB34."
        assertTrue(ConfirmationIngestion.shouldAccept("MTN", momo, AppRegion.UGANDA))
        val tx = ConfirmationIngestion.acceptAndParse("MTN", momo, ts, AppRegion.UGANDA)
        assertNotNull(tx)
        assertEquals(MoneySource.MTN_MOMO, tx!!.source)
        assertEquals(10000.0, tx.amount!!, 0.001)

        val tkash =
            "Confirmed. You have sent Ksh 200.00 to JANE DOE. New T-Kash balance is Ksh 1,000.00. Ref TK998877."
        assertFalse(ConfirmationIngestion.shouldAccept("T-Kash", tkash, AppRegion.UGANDA))
        assertNull(ConfirmationIngestion.acceptAndParse("T-Kash", tkash, ts, AppRegion.UGANDA))
    }

    @Test
    fun kenya_airtel_telco_is_not_the_wallet() {
        val promo = "You've spent 1.00 KES and your balance is 0.05 KES. Dial *544# now"
        assertFalse(ConfirmationIngestion.shouldAccept("AIRTEL", promo, AppRegion.KENYA))
        assertTrue(
            ConfirmationIngestion.shouldAccept(
                "airtelmoney",
                "43RAMS4FHNH. Ksh 70 sent to JANE DOE 254712345678 on 16/09/26 at 01:31 PM. " +
                    "Fee: Ksh 0. Bal: Ksh 66.0. MPESA ID: UIGRF6VRCN",
                AppRegion.KENYA
            )
        )
    }

    @Test
    fun disabled_provider_is_not_accepted() {
        assertFalse(
            ConfirmationIngestion.shouldAccept(
                "KCB",
                kcbBody,
                AppRegion.KENYA,
                enabled = setOf(MoneySource.MPESA)
            )
        )
        assertTrue(
            ConfirmationIngestion.shouldAccept(
                "KCB",
                kcbBody,
                AppRegion.KENYA,
                enabled = setOf(MoneySource.MPESA, MoneySource.KCB)
            )
        )
    }

    @Test
    fun kenya_rejects_ugandan_airtel_currency() {
        val ugAirtel =
            "You have received UGX 500 from JOHN. New Airtel Money balance is UGX 2,000. TxnId: AM99."
        assertFalse(ConfirmationIngestion.shouldAccept("AirtelMoney", ugAirtel, AppRegion.KENYA))
        assertTrue(ConfirmationIngestion.shouldAccept("AirtelMoney", ugAirtel, AppRegion.UGANDA))
    }

    @Test
    fun mpesa_otp_is_still_rejected() {
        val otp = "MPESA: Your verification code is 445566. Do not share this PIN."
        assertFalse(ConfirmationIngestion.shouldAccept("MPESA", otp))
        assertFalse(ConfirmationIngestion.looksLikeStoredConfirmation(MoneySource.MPESA, otp))
    }

    @Test
    fun tanzania_mpesa_does_not_need_kenya_confirmed_balance() {
        val body =
            "You have received TZS 5,000 from JANE DOE. New M-Pesa balance is TZS 12,000. Ref: ABC123XYZ."
        assertTrue(ConfirmationIngestion.shouldAccept("MPESA", body, AppRegion.TANZANIA))
        val tx = ConfirmationIngestion.acceptAndParse("MPESA", body, ts, AppRegion.TANZANIA)
        assertNotNull(tx)
        assertEquals(MoneySource.MPESA, tx!!.source)
        assertEquals(5000.0, tx.amount!!, 0.001)
        assertTrue(ConfirmationIngestion.looksLikeStoredConfirmation(MoneySource.MPESA, body))
        assertTrue(ConfirmationIngestion.looksLikeStoredConfirmation(MoneySource.KCB, kcbBody))
    }
}
