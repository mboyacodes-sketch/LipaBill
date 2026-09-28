package com.lipabill.app.ussd

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.parser.MpesaSmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPaymentMatcherTest {

    private val started = 1_700_000_000_000L

    private fun snapshot(
        amount: Double = 100.0,
        type: TransactionType = TransactionType.SENT,
        phone: String? = "0712345678",
        name: String? = "Jane Doe",
        account: String? = null
    ) = PendingPaymentReceipt.Snapshot(
        auditId = 1L,
        amount = amount,
        type = type,
        counterpartyName = name,
        counterpartyPhone = phone,
        accountHint = account,
        startedAtMillis = started
    )

    private fun tx(
        amount: Double = 100.0,
        type: TransactionType = TransactionType.SENT,
        phone: String? = "254712345678",
        name: String? = "JANE DOE",
        raw: String = "Confirmed. Ksh100.00 sent to Jane",
        time: Long = started + 5_000L
    ) = MpesaTransaction(
        id = 9,
        code = "ABC123XYZ",
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = time,
        balance = 50.0,
        cost = 0.0,
        rawBody = raw
    )

    @Test
    fun matches_send_by_amount_type_and_phone_suffix() {
        assertTrue(PendingPaymentMatcher.matches(snapshot(), tx()))
    }

    @Test
    fun rejects_wrong_amount() {
        assertFalse(PendingPaymentMatcher.matches(snapshot(), tx(amount = 200.0)))
    }

    @Test
    fun rejects_wrong_type() {
        assertFalse(
            PendingPaymentMatcher.matches(
                snapshot(type = TransactionType.SENT),
                tx(type = TransactionType.PAYBILL)
            )
        )
    }

    @Test
    fun requires_account_when_hint_present() {
        val snap = snapshot(
            type = TransactionType.PAYBILL,
            phone = "888880",
            account = "ACC99"
        )
        assertFalse(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.PAYBILL,
                    phone = "888880",
                    raw = "Confirmed. Paid to Biz Acc other"
                )
            )
        )
        assertTrue(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.PAYBILL,
                    phone = "888880",
                    raw = "Confirmed. Paid to Biz Account ACC99"
                )
            )
        )
    }

    @Test
    fun pochi_matches_plain_sent_to_sms_with_254_phone() {
        val snap = snapshot(
            type = TransactionType.POCHI,
            phone = "0712345678",
            name = "Mama Shop"
        )
        assertTrue(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.SENT,
                    phone = "254712345678",
                    name = "JANE DOE",
                    raw = "Confirmed. Ksh100.00 sent to JANE DOE 254712345678 on 1/1/24"
                )
            )
        )
    }

    @Test
    fun pochi_matches_name_only_sent_to_sms() {
        val snap = snapshot(
            type = TransactionType.POCHI,
            phone = "0712345678",
            name = "Mama Shop"
        )
        assertTrue(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.SENT,
                    phone = null,
                    name = "JANE DOE",
                    raw = "Confirmed. Ksh100.00 sent to JANE DOE on 1/1/24"
                )
            )
        )
    }

    @Test
    fun pochi_matches_masked_phone() {
        val snap = snapshot(type = TransactionType.POCHI, phone = "0712345678", name = "Mama Shop")
        assertTrue(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.SENT,
                    phone = "2547*****678",
                    name = "JANE DOE",
                    raw = "Confirmed. Ksh100.00 sent to JANE DOE 2547*****678 on 1/1/24"
                )
            )
        )
    }

    @Test
    fun pochi_rejects_different_phone() {
        val snap = snapshot(type = TransactionType.POCHI, phone = "0712345678")
        assertFalse(
            PendingPaymentMatcher.matches(
                snap,
                tx(type = TransactionType.SENT, phone = "254799999999", name = "OTHER PERSON")
            )
        )
    }

    @Test
    fun send_matches_when_sms_omits_phone() {
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(name = "Mum", phone = "0712345678"),
                tx(type = TransactionType.SENT, phone = null, name = "JOHN KAMAU")
            )
        )
    }

    @Test
    fun send_matches_transferred_to_other_network_shape() {
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(phone = "0733000000", name = "0733000000"),
                tx(
                    type = TransactionType.SENT,
                    phone = "254733000000",
                    name = null,
                    raw = "Confirmed. Ksh100.00 transferred to 254733000000 on 1/1/24"
                )
            )
        )
    }

    @Test
    fun till_placeholder_matches_business_name_sms() {
        val snap = snapshot(
            type = TransactionType.BUY_GOODS,
            phone = "123456",
            name = "Till 123456",
            account = null
        )
        assertTrue(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.BUY_GOODS,
                    phone = null,
                    name = "JAVA HOUSE WESTLANDS",
                    raw = "Confirmed. Ksh100.00 paid to JAVA HOUSE WESTLANDS."
                )
            )
        )
    }

    @Test
    fun till_rejects_different_business_name() {
        val snap = snapshot(
            type = TransactionType.BUY_GOODS,
            phone = "123456",
            name = "Java House",
            account = null
        )
        assertFalse(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.BUY_GOODS,
                    phone = null,
                    name = "NAIVAS SUPERMARKET",
                    raw = "Confirmed. Ksh100.00 paid to NAIVAS SUPERMARKET."
                )
            )
        )
    }

    @Test
    fun findMatch_prefers_phone_agreement_over_an_earlier_weak_hit() {
        val snap = snapshot()
        val weak = tx(phone = null, name = "SOMEONE ELSE", time = started + 8_000L)
        val strong = tx(time = started + 4_000L)
        val match = PendingPaymentMatcher.findMatch(snap, listOf(weak, strong))
        assertNotNull(match)
        assertTrue(match!!.counterpartyPhone == "254712345678")
    }

    @Test
    fun rejects_sms_from_before_the_dial() {
        assertFalse(
            PendingPaymentMatcher.matches(
                snapshot(),
                tx(time = started - 180_000L)
            )
        )
    }

    @Test
    fun accepts_sms_clock_slightly_before_dial() {
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(),
                tx(time = started - 30_000L)
            )
        )
    }

    @Test
    fun send_rejects_a_different_phone() {
        assertFalse(
            PendingPaymentMatcher.matches(
                snapshot(phone = "0712345678"),
                tx(phone = "254700000000", name = "SOMEONE ELSE")
            )
        )
    }

    @Test
    fun send_matches_pochi_labelled_sms_for_the_same_phone() {
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(type = TransactionType.SENT, phone = "0712345678"),
                tx(
                    type = TransactionType.POCHI,
                    phone = "254712345678",
                    raw = "Confirmed. Ksh100.00 sent to Pochi La Biashara JANE DOE 254712345678"
                )
            )
        )
    }

    @Test
    fun pochi_matches_paid_to_pochi_receipt() {
        val snap = snapshot(type = TransactionType.POCHI, phone = "0712345678", name = "Mama Shop")
        assertTrue(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.BUY_GOODS,
                    phone = null,
                    name = "POCHI LA BIASHARA - MAMA NJERI",
                    raw = "Confirmed. Ksh100.00 paid to POCHI LA BIASHARA - MAMA NJERI."
                )
            )
        )
    }

    @Test
    fun pochi_does_not_match_a_till_receipt() {
        val snap = snapshot(type = TransactionType.POCHI, phone = "0712345678", name = "Mama Shop")
        assertFalse(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.BUY_GOODS,
                    phone = null,
                    name = "JAVA HOUSE",
                    raw = "Confirmed. Ksh100.00 paid to JAVA HOUSE."
                )
            )
        )
    }

    @Test
    fun masked_phone_that_does_not_align_is_rejected() {
        assertFalse(
            PendingPaymentMatcher.matches(
                snapshot(type = TransactionType.POCHI, phone = "0712345678"),
                tx(type = TransactionType.SENT, phone = "2547*****000", name = "JANE DOE")
            )
        )
    }

    @Test
    fun paybill_placeholder_matches_when_account_is_present() {
        val snap = snapshot(
            type = TransactionType.PAYBILL,
            phone = "888880",
            name = "Paybill 888880",
            account = "ACC99"
        )
        assertTrue(
            PendingPaymentMatcher.matches(
                snap,
                tx(
                    type = TransactionType.PAYBILL,
                    phone = null,
                    name = "KPLC PREPAID",
                    raw = "Confirmed. Ksh100.00 paid to KPLC PREPAID. Account ACC99."
                )
            )
        )
    }

    @Test
    fun parsed_plain_sent_to_matches_pochi_dial() {
        val parsed = MpesaSmsParser.parse(
            "PKA1SENT01 Confirmed. Ksh100.00 sent to JANE DOE 254712345678 on 1/1/24 at 2:00 PM. " +
                "New M-PESA balance is Ksh50.00. Transaction cost, Ksh0.00.",
            started + 5_000L
        )
        assertEquals(TransactionType.SENT, parsed.type)
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(type = TransactionType.POCHI, phone = "0712345678", name = "Mama Shop"),
                parsed
            )
        )
    }

    @Test
    fun parsed_name_only_sent_to_matches_send_dial() {
        val parsed = MpesaSmsParser.parse(
            "PKA1NAME01 Confirmed. Ksh100.00 sent to JOHN KAMAU. " +
                "New M-PESA balance is Ksh50.00. Transaction cost, Ksh0.00.",
            started + 5_000L
        )
        assertNull(parsed.counterpartyPhone)
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(type = TransactionType.SENT, phone = "0712345678", name = "Mum"),
                parsed
            )
        )
    }

    @Test
    fun parsed_masked_and_other_network_receipts_match_send() {
        val masked = MpesaSmsParser.parse(
            "PKA1MASK01 Confirmed. Ksh100.00 sent to JANE DOE 0712***678 on 1/1/24 at 2:00 PM. " +
                "New M-PESA balance is Ksh50.00.",
            started + 5_000L
        )
        assertEquals("0712***678", masked.counterpartyPhone)
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(phone = "0712345678", name = "Mum"),
                masked
            )
        )

        val otherNetwork = MpesaSmsParser.parse(
            "PKA1XFER01 Confirmed. Ksh100.00 transferred to 254712345678 on 1/1/24 at 2:00 PM. " +
                "New M-PESA balance is Ksh50.00.",
            started + 5_000L
        )
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(phone = "0712345678", name = "0712345678"),
                otherNetwork
            )
        )
    }

    @Test
    fun parsed_paybill_and_till_match_their_dials() {
        val paybill = MpesaSmsParser.parse(
            "PKA1BILL01 Confirmed. Ksh100.00 paid to KPLC PREPAID. Account ACC99. " +
                "on 1/1/24 at 2:00 PM. New M-PESA balance is Ksh50.00. Transaction cost, Ksh0.00.",
            started + 5_000L
        )
        assertEquals(TransactionType.PAYBILL, paybill.type)
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(
                    type = TransactionType.PAYBILL,
                    phone = "888880",
                    name = "Paybill 888880",
                    account = "ACC99"
                ),
                paybill
            )
        )

        val till = MpesaSmsParser.parse(
            "PKA1TILL01 Confirmed. Ksh100.00 paid to JAVA HOUSE WESTLANDS. " +
                "on 1/1/24 at 2:00 PM. New M-PESA balance is Ksh50.00. Transaction cost, Ksh0.00.",
            started + 5_000L
        )
        assertEquals(TransactionType.BUY_GOODS, till.type)
        assertTrue(
            PendingPaymentMatcher.matches(
                snapshot(
                    type = TransactionType.BUY_GOODS,
                    phone = "123456",
                    name = "Till 123456"
                ),
                till
            )
        )
    }

    @Test
    fun findMatch_returns_first_hit() {
        val snap = snapshot()
        assertNotNull(
            PendingPaymentMatcher.findMatch(
                snap,
                listOf(tx(amount = 50.0), tx(amount = 100.0))
            )
        )
        assertNull(
            PendingPaymentMatcher.findMatch(snap, listOf(tx(amount = 1.0)))
        )
    }
}
