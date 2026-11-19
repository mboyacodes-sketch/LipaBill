package com.lipabill.app.ussd

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays Airtel Kenya `*334#` screens recorded on a real phone (Nov 2026, stopped at PIN)
 * against the plans. Names and numbers are replaced with fictional ones.
 */
class AirtelKenyaLiveMenuTest {

    private val main = """
        98.CashBack
        0.Bizna Wallet
        1.Bundle Offers
        2.Buy Airtime
        3.Send Money
        4.Withdraw
        5.Paybill&Tills
        6.Loans &Saving
        7.My Account/PIN
        8.Reversals
    """.trimIndent()

    private val sendTo = """
        Send  to
        1.Send Money to Any Network (Free Airtel to Airtel )
        2.Airtel Bizna
        3.Send to M-PESA Pochi
        4.Another Country
        5.Send to T-Kash
    """.trimIndent()

    private val payMenu = "1.Paybill\n2.Buy Goods and Services\n3.Bizna"
    private val paybillNetwork = "Get 50% of your TXN fee as CASH in your BONUS WALLET\n\n1.Deposit To Bank\n2.Airtel Paybill\n3.Mpesa Paybill"
    private val tillNetwork = "1.Airtel Money Till\n2.M-pesa Till\n#.Home\n*.Back"

    private fun tx(type: TransactionType, phone: String? = null, name: String? = null, raw: String = "") =
        MpesaTransaction(
            id = 1, code = "T1", type = type, amount = 10.0, counterpartyName = name,
            counterpartyPhone = phone, timestampMillis = 0, balance = null, cost = null, rawBody = raw
        )

    /** Walks [screens] with [plan]; menus must resolve to [digits], prompts must accept the value. */
    private fun walk(plan: UssdMenuBuilder.MenuPlan, screens: List<String>, digits: List<String?>, pin: String) {
        assertEquals(screens.size, plan.actions.size)
        plan.actions.forEachIndexed { i, action ->
            val screen = screens[i]
            when (action) {
                is UssdAction.ChooseMenu ->
                    assertEquals("step $i on: $screen", digits[i],
                        UssdMenuMatcher.resolveChoice(screen, action.labels, action.excludeLabels))
                is UssdAction.TypeValue -> {
                    assertTrue("step $i is not a menu", !UssdMenuMatcher.looksLikeMenu(screen))
                    assertTrue("step $i hints ${action.promptHints} on '$screen'",
                        action.promptHints.any { screen.lowercase().contains(it.lowercase()) })
                }
            }
        }
        assertTrue("stops at PIN", PinDetector.isPinPrompt(pin))
    }

    @Test
    fun sendMoneyToAnyNetwork() {
        val plan = WalletMenuPlans.build(PaymentWallet.AIRTEL_KENYA, tx(TransactionType.SENT, phone = "0712345678"))
        assertNotNull(plan)
        walk(
            plan!!,
            listOf(main, sendTo, "Enter Recipient Mobile Number", "Enter Amount"),
            listOf("3", "1", null, null),
            "SEND Ksh 10 to Jane Doe 254712345678 at a cost of ksh 0 \nEnter PIN to confirm"
        )
        assertEquals("0712345678", (plan.actions[2] as UssdAction.TypeValue).value)
    }

    @Test
    fun pochiGoesToMpesaPochi() {
        val plan = WalletMenuPlans.build(PaymentWallet.AIRTEL_KENYA, tx(TransactionType.POCHI, phone = "0712345678"))
        assertNotNull(plan)
        assertEquals("3", UssdMenuMatcher.resolveChoice(sendTo, (plan!!.actions[1] as UssdAction.ChooseMenu).labels,
            (plan.actions[1] as UssdAction.ChooseMenu).excludeLabels))
        assertTrue((plan.actions[2] as UssdAction.TypeValue).promptHints.any { "enter safaricom number".contains(it) })
    }

    @Test
    fun buyGoodsGoesToMpesaTill() {
        val plan = WalletMenuPlans.build(
            PaymentWallet.AIRTEL_KENYA,
            tx(TransactionType.BUY_GOODS, phone = "4160387", name = "CORNER SHOP")
        )
        assertNotNull(plan)
        walk(
            plan!!,
            listOf(main, payMenu, tillNetwork, "Enter MPESA Till Number", "Pay CORNER SHOP LIMITED Enter Amount"),
            listOf("5", "2", "2", null, null),
            "PAY KES 10 to Safaricom Merchant Payments at a cost of KES 0 \n\n Enter PIN"
        )
    }

    @Test
    fun paybillGoesToMpesaPaybill() {
        val plan = WalletMenuPlans.build(
            PaymentWallet.AIRTEL_KENYA,
            tx(TransactionType.PAYBILL, phone = "888880", name = "KPLC PREPAID",
                raw = "ABC123 Confirmed. Ksh10.00 sent to KPLC PREPAID for account 12345678901 on 1/1/26")
        )
        assertNotNull(plan)
        walk(
            plan!!,
            listOf(main, payMenu, paybillNetwork, "Enter PayBill Number", "Enter Meter Number", "Enter Amount"),
            listOf("5", "1", "3", null, null, null),
            "PAY KES 10 to KPLC PREPAID at a cost of KES 0 \n\n Enter PIN"
        )
    }

    @Test
    fun billerAccountNeverTypesIntoAmount() {
        val plan = WalletMenuPlans.build(
            PaymentWallet.AIRTEL_KENYA,
            tx(TransactionType.PAYBILL, phone = "888880", raw = "X Confirmed. Ksh10.00 sent to KPLC for account 123456 on 1/1/26")
        )!!
        val account = plan.actions[4] as UssdAction.TypeValue
        assertTrue(account.promptHints.none { "enter amount".contains(it) })
    }
}
