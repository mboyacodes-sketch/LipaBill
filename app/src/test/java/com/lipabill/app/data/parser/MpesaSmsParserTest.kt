package com.lipabill.app.data.parser

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.sms.REVERSAL_JULIUS_SMS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fabricated but real-shaped M-Pesa confirmation samples — at least two per type.
 */
class MpesaSmsParserTest {

    private val ts = 1_700_000_000_000L

    @Test
    fun sent_to_person_with_phone() {
        val body =
            "THX7K2LM9P Confirmed. Ksh1,500.00 sent to JOHN KAMAU 254712345678 on 15/3/24 at 2:15 PM. " +
                "New M-PESA balance is Ksh12,450.50. Transaction cost, Ksh23.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.SENT, tx.type)
        assertEquals("THX7K2LM9P", tx.code)
        assertEquals(1500.0, tx.amount!!, 0.001)
        assertEquals("JOHN KAMAU", tx.counterpartyName)
        assertEquals("254712345678", tx.counterpartyPhone)
        assertEquals(12450.50, tx.balance!!, 0.001)
        assertEquals(23.0, tx.cost!!, 0.001)
        assertEquals(body, tx.rawBody)
    }

    @Test
    fun sent_to_person_zero_prefix_phone() {
        val body =
            "SDE45FGHIJ Confirmed. Ksh250.00 sent to MARY WANJIKU 0712345678 on 16/3/24 at 9:01 AM. " +
                "New M-PESA balance is Ksh3,200.00. Transaction cost, Ksh6.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.SENT, tx.type)
        assertEquals("SDE45FGHIJ", tx.code)
        assertEquals(250.0, tx.amount!!, 0.001)
        assertEquals("MARY WANJIKU", tx.counterpartyName)
        assertEquals("0712345678", tx.counterpartyPhone)
    }

    @Test
    fun received_from_person() {
        val body =
            "QWE12ABCDE Confirmed. You have received Ksh5,000.00 from PETER OCHIENG 254722334455 " +
                "on 14/3/24 at 11:30 AM. New M-PESA balance is Ksh8,100.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.RECEIVED, tx.type)
        assertEquals("QWE12ABCDE", tx.code)
        assertEquals(5000.0, tx.amount!!, 0.001)
        assertEquals("PETER OCHIENG", tx.counterpartyName)
        assertEquals("254722334455", tx.counterpartyPhone)
        assertEquals(8100.0, tx.balance!!, 0.001)
    }

    @Test
    fun received_from_business_name() {
        val body =
            "RCV98XYZ12 Confirmed. You have received Ksh750.00 from NAIVAS SUPERMARKET " +
                "on 13/3/24 at 4:45 PM. New M-PESA balance is Ksh2,550.25."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.RECEIVED, tx.type)
        assertEquals(750.0, tx.amount!!, 0.001)
        assertEquals("NAIVAS SUPERMARKET", tx.counterpartyName)
    }

    @Test
    fun paid_to_paybill_with_account() {
        val body =
            "PBILL7X9YZ Confirmed. Ksh1,200.00 paid to KPLC PREPAID. Account 01451234567. " +
                "on 12/3/24 at 8:00 AM. New M-PESA balance is Ksh4,800.00. Transaction cost, Ksh0.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.PAYBILL, tx.type)
        assertEquals("PBILL7X9YZ", tx.code)
        assertEquals(1200.0, tx.amount!!, 0.001)
        assertEquals("KPLC PREPAID", tx.counterpartyName)
        assertEquals(0.0, tx.cost!!, 0.001)
    }

    @Test
    fun paid_to_paybill_safaricom() {
        val body =
            "SAF99PAY01 Confirmed. Ksh100.00 paid to Safaricom Limited. Acc. 0722000000 " +
                "on 11/3/24 at 7:10 PM. New M-PESA balance is Ksh900.00. Transaction cost, Ksh0.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.PAYBILL, tx.type)
        assertTrue(tx.counterpartyName!!.contains("Safaricom", ignoreCase = true))
        assertEquals(100.0, tx.amount!!, 0.001)
    }

    @Test
    fun paid_to_till_buy_goods() {
        val body =
            "TILL55ABCD Confirmed. Ksh450.00 paid to JAVA HOUSE WESTLANDS. " +
                "on 10/3/24 at 1:20 PM. New M-PESA balance is Ksh6,100.00. Transaction cost, Ksh0.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.BUY_GOODS, tx.type)
        assertEquals("JAVA HOUSE WESTLANDS", tx.counterpartyName)
        assertEquals(450.0, tx.amount!!, 0.001)
    }

    @Test
    fun paid_to_till_duka() {
        val body =
            "BGDS33KLMN Confirmed. Ksh85.00 paid to MAMA MBOTA GROCERY. " +
                "on 9/3/24 at 6:05 PM. New M-PESA balance is Ksh515.00. Transaction cost, Ksh0.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.BUY_GOODS, tx.type)
        assertEquals(85.0, tx.amount!!, 0.001)
        assertEquals("MAMA MBOTA GROCERY", tx.counterpartyName)
    }

    @Test
    fun withdraw_from_agent() {
        val body =
            "WDR12AGENT Confirmed. Withdraw Ksh2,000.00 from 001234 - Agent Jane Doe New " +
                "M-PESA balance is Ksh3,500.00. Transaction cost, Ksh28.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.WITHDRAW, tx.type)
        assertEquals("WDR12AGENT", tx.code)
        assertEquals(2000.0, tx.amount!!, 0.001)
        assertTrue(tx.counterpartyName!!.contains("Agent Jane Doe"))
        assertEquals(28.0, tx.cost!!, 0.001)
    }

    @Test
    fun withdraw_from_agent_short() {
        val body =
            "WTH88XYZAB Confirmed. on 8/3/24 at 3:00 PM Withdraw Ksh500.00 from 998877 - CITY AGENT New " +
                "M-PESA balance is Ksh1,000.00. Transaction cost, Ksh11.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.WITHDRAW, tx.type)
        assertEquals(500.0, tx.amount!!, 0.001)
        assertEquals(1000.0, tx.balance!!, 0.001)
    }

    @Test
    fun deposit_of_amount() {
        val body =
            "DEP01ABCDE Confirmed. You have received a deposit of Ksh10,000.00 from Agent 445566 - " +
                "QUICK CASH. New M-PESA balance is Ksh15,250.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.DEPOSIT, tx.type)
        assertEquals("DEP01ABCDE", tx.code)
        assertEquals(10000.0, tx.amount!!, 0.001)
        assertEquals(15250.0, tx.balance!!, 0.001)
    }

    @Test
    fun deposit_of_simple() {
        val body =
            "DPS77LMNOP Confirmed. deposit of Ksh3,000.00 has been made to your account from 112233 - " +
                "SHOP AGENT. New M-PESA balance is Ksh7,800.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.DEPOSIT, tx.type)
        assertEquals(3000.0, tx.amount!!, 0.001)
        assertTrue(tx.counterpartyName!!.contains("SHOP AGENT") || tx.counterpartyName!!.contains("112233"))
    }

    @Test
    fun sent_to_without_date_still_reads_name() {
        val body =
            "NODATE001 Confirmed. Ksh100.00 sent to JOHN. New M-PESA balance is Ksh50.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.SENT, tx.type)
        assertEquals("JOHN", tx.counterpartyName)
        assertEquals(100.0, tx.amount!!, 0.001)
    }

    @Test
    fun sent_ksh_to_other_network_reads_phone() {
        val body =
            "OTHNET001 Confirmed. You sent Ksh200.00 to 0733000000 on 1/1/26 at 10:00 AM. " +
                "New M-PESA balance is Ksh800.00. Transaction cost, Ksh0.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.SENT, tx.type)
        assertEquals("0733000000", tx.counterpartyPhone)
        assertEquals(200.0, tx.amount!!, 0.001)
    }

    @Test
    fun transferred_to_is_send() {
        val body =
            "XFER00001 Confirmed. Ksh75.00 transferred to 254711222333 on 2/2/26 at 9:00 AM. " +
                "New M-PESA balance is Ksh400.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.SENT, tx.type)
        assertEquals("254711222333", tx.counterpartyPhone)
    }

    @Test
    fun sent_to_pochi_by_name_is_pochi_even_without_the_word_in_the_party_only() {
        val body =
            "POCHI0001 Confirmed. Ksh500.00 sent to Pochi La Biashara JANE DOE 254712345678 " +
                "on 3/3/26 at 4:00 PM. New M-PESA balance is Ksh1,000.00. Transaction cost, Ksh0.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.POCHI, tx.type)
        assertEquals("254712345678", tx.counterpartyPhone)
        assertTrue(tx.counterpartyName!!.contains("JANE DOE"))
    }

    @Test
    fun paid_to_pochi_is_not_buy_goods() {
        val body =
            "POCHI0002 Confirmed. Ksh80.00 paid to POCHI LA BIASHARA - MAMA NJERI. " +
                "on 4/4/26 at 1:00 PM. New M-PESA balance is Ksh200.00. Transaction cost, Ksh0.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.POCHI, tx.type)
        assertTrue(tx.counterpartyName!!.contains("MAMA NJERI", ignoreCase = true))
    }

    @Test
    fun masked_phone_is_kept_on_send() {
        val body =
            "MASK00001 Confirmed. Ksh100.00 sent to JANE DOE 2547*****678 on 5/5/26 at 8:00 AM. " +
                "New M-PESA balance is Ksh300.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.SENT, tx.type)
        assertEquals("2547*****678", tx.counterpartyPhone)
        assertEquals("JANE DOE", tx.counterpartyName)
    }

    @Test
    fun reversal_credits_the_original_payee() {
        val tx = MpesaSmsParser.parse(REVERSAL_JULIUS_SMS, ts)
        assertEquals(TransactionType.REVERSED, tx.type)
        assertEquals("UJ4R7Q7R31", tx.code)
        assertEquals(125.0, tx.amount!!, 0.001)
        assertEquals("JULIUS VIAZI SUPPLIER", tx.counterpartyName)
        assertNull(tx.counterpartyPhone)
        assertEquals(4801.97, tx.balance!!, 0.001)
        assertEquals(REVERSAL_JULIUS_SMS, tx.rawBody)
    }

    @Test
    fun reversal_accepts_favor_spelling() {
        val body =
            "REV88ABCD1 Confirmed. Your original transaction SENT00ABCD in favor of " +
                "MAMA NJERI SHOP has been reversed successfully on 1/2/26 at 9:00 AM " +
                "and Ksh50.00 has been credited to your M-PESA account. " +
                "Your new M-PESA account balance is Ksh1,050.00."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.REVERSED, tx.type)
        assertEquals(50.0, tx.amount!!, 0.001)
        assertEquals("MAMA NJERI SHOP", tx.counterpartyName)
        assertEquals(1050.0, tx.balance!!, 0.001)
    }

    @Test
    fun unknown_preserves_raw_and_does_not_crash() {
        val body = "Hello from MPESA promo: earn points this weekend!"
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.UNKNOWN, tx.type)
        assertEquals(body, tx.rawBody)
        assertNull(tx.counterpartyName)
        assertTrue(tx.code.startsWith("UNK"))
    }

    @Test
    fun unknown_partial_amount_still_captured() {
        val body = "ZZZ00NOISE Confirmed. Something weird with Ksh99.00 happened today."
        val tx = MpesaSmsParser.parse(body, ts)
        assertEquals(TransactionType.UNKNOWN, tx.type)
        assertEquals("ZZZ00NOISE", tx.code)
        assertEquals(99.0, tx.amount!!, 0.001)
    }
}
