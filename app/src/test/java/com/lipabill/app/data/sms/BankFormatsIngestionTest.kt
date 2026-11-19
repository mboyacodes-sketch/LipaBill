package com.lipabill.app.data.sms

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Bank/wallet formats that used to be dropped, and lookalikes that must stay out. Fictional data. */
class BankFormatsIngestionTest {

    private val region = AppRegion.KENYA
    private val ts = 1_760_000_000_000L

    private fun parse(sender: String, body: String) =
        ConfirmationIngestion.acceptAndParse(sender, body, ts, region, region.sources)

    @Test
    fun ncbaTransactionWasSuccessful() {
        val tx = parse(
            "NCBA_BANK",
            "Dear Jane Doe your transaction of KES 1,250.00 to ACME TRADERS LIMITED 123456 was successful on 01/02/2026 10:15 AM. M-Pesa Ref: TXA1B2C3D4"
        )
        assertNotNull(tx)
        assertEquals(MoneySource.NCBA, tx!!.source)
        assertEquals(1250.0, tx.amount!!, 0.001)
        assertEquals(TransactionType.SENT, tx.type)
    }

    @Test
    fun ncbaMpesaPaymentWasSuccessful() {
        val tx = parse(
            "NCBA_BANK",
            "Your M-Pesa payment of KES 430.00 to CORNER CAFE LTD 654321 was successful on 03/02/2026 01:05 PM. M-Pesa Ref: TXB9C8D7E6"
        )
        assertNotNull(tx)
        assertEquals(430.0, tx!!.amount!!, 0.001)
    }

    @Test
    fun equityDepositedInFavourOfIsAnOutflow() {
        val tx = parse(
            "Equity Bank",
            "Dear Jane Doe, Your transaction of KES. 900.00 has successfully been deposited to Equity Account in favor of JOHN SMITH Ref. Number 123ABC456"
        )
        assertNotNull(tx)
        assertEquals(TransactionType.SENT, tx!!.type)
    }

    @Test
    fun stanchartDepositToOwnAccountIsADeposit() {
        val tx = parse(
            "STANCHART",
            "Dear Client, KES 15000.00 has been deposited to Acc. ending with 4321. For any queries call us."
        )
        assertNotNull(tx)
        assertEquals(TransactionType.DEPOSIT, tx!!.type)
    }

    @Test
    fun kcbCompletedReceiptWithDialFooterStays() {
        assertNotNull(
            parse(
                "KCB",
                "AB12CD34EF completed.KES 2,000.00 sent to M-PESA 254700000000 on 05/02/2026 at 9:10 PM.Transaction cost KES 30.00. Dial *522# for more."
            )
        )
    }

    @Test
    fun kcbInitiationCodeIsNotATransaction() {
        assertNull(
            parse(
                "KCB",
                "<#> You have initiated an MPESA transfer of KES 5,000 from account to 0700000000. Authorization code: 1234. KCB WILL NOT CALL YOU."
            )
        )
    }

    @Test
    fun walletPromoIsNotATransaction() {
        assertNull(
            parse(
                "airtelmoney",
                "Transfer Ksh100 & below from M-Pesa to Airtel Money for FREE! Dial *334# on your Airtel line."
            )
        )
    }
}
