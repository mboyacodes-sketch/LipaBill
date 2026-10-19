package com.lipabill.app.ussd

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UssdMenuBuilderTest {

    private fun tx(
        type: TransactionType,
        amount: Double? = 100.0,
        phone: String? = "0712345678",
        name: String? = "TEST",
        raw: String = ""
    ) = MpesaTransaction(
        id = 1,
        code = "ABC123",
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = 1L,
        balance = null,
        cost = null,
        rawBody = raw
    )

    @Test
    fun sent_uses_label_then_phone_and_amount() {
        val plan = UssdMenuBuilder.build(tx(TransactionType.SENT))
        assertNotNull(plan)
        assertEquals(4, plan!!.actions.size)
        val main = plan.actions[0] as UssdAction.ChooseMenu
        val sub = plan.actions[1] as UssdAction.ChooseMenu
        assertTrue(main.labels.any { it.contains("send money", ignoreCase = true) })
        assertTrue(sub.labels.any { it.contains("send money", ignoreCase = true) })
        assertEquals("0712345678", (plan.actions[2] as UssdAction.TypeValue).value)
        assertEquals("100", (plan.actions[3] as UssdAction.TypeValue).value)
    }

    @Test
    fun sent_normalizes_254_prefix() {
        val plan = UssdMenuBuilder.build(tx(TransactionType.SENT, phone = "254712345678"))
        assertEquals("0712345678", (plan!!.actions[2] as UssdAction.TypeValue).value)
    }

    @Test
    fun buy_goods_uses_lipa_then_buy_goods_labels() {
        val plan = UssdMenuBuilder.build(
            tx(TransactionType.BUY_GOODS, phone = "123456", name = "SHOP")
        )
        assertNotNull(plan)
        val lipa = plan!!.actions[0] as UssdAction.ChooseMenu
        val buy = plan.actions[1] as UssdAction.ChooseMenu
        assertTrue(lipa.labels.any { it.contains("lipa") })
        assertTrue(buy.labels.any { it.contains("buy goods") })
        assertEquals("123456", (plan.actions[2] as UssdAction.TypeValue).value)
    }

    @Test
    fun received_cannot_repeat() {
        assertNull(UssdMenuBuilder.build(tx(TransactionType.RECEIVED)))
        assertFalse(UssdMenuBuilder.canRepeat(tx(TransactionType.RECEIVED)))
    }

    @Test
    fun amount_override_used_in_steps() {
        val plan = UssdMenuBuilder.build(
            tx(TransactionType.SENT, amount = 100.0),
            amountOverride = 250.0
        )
        assertEquals("250", (plan!!.actions.last() as UssdAction.TypeValue).value)
    }

    @Test
    fun missing_amount_cannot_build_without_override() {
        assertNull(UssdMenuBuilder.build(tx(TransactionType.SENT, amount = null)))
    }

    @Test
    fun missing_amount_can_repeat_with_destination() {
        assertTrue(UssdMenuBuilder.canRepeat(tx(TransactionType.SENT, amount = null)))
    }

    @Test
    fun shortfall_watches_for_fuliza_prompt() {
        val plan = UssdMenuBuilder.build(
            tx(TransactionType.SENT, amount = 500.0),
            mpesaBalance = 120.0
        )
        assertNotNull(plan)
        assertTrue(plan!!.watchFuliza)
        assertEquals("500", (plan.actions.last() as UssdAction.TypeValue).value)
    }

    @Test
    fun covered_amount_does_not_watch_for_fuliza() {
        val plan = UssdMenuBuilder.build(
            tx(TransactionType.PAYBILL, amount = 100.0, phone = "888880", raw = "Account 12345"),
            mpesaBalance = 400.0
        )
        assertNotNull(plan)
        assertFalse(plan!!.watchFuliza)
    }

    @Test
    fun fuliza_opt_in_follows_loans_then_fuliza_then_opt_in() {
        val plan = UssdMenuBuilder.fulizaOptIn()
        assertEquals(3, plan.actions.size)
        assertFalse(plan.watchFuliza)
        val loans = plan.actions[0] as UssdAction.ChooseMenu
        val fuliza = plan.actions[1] as UssdAction.ChooseMenu
        val optIn = plan.actions[2] as UssdAction.ChooseMenu
        assertTrue(loans.labels.any { it.contains("loans and savings") })
        assertTrue(fuliza.labels.any { it.contains("fuliza") })
        assertTrue(fuliza.excludeLabels.any { it.contains("biashara") })
        assertTrue(optIn.labels.any { it.contains("opt in") })
        assertTrue(optIn.excludeLabels.any { it.contains("opt out") })
    }
}

class UssdMenuMatcherTest {

    private val classicMain = """
        Safaricom
        1. Send Money
        2. Withdraw Cash
        3. Buy Airtime
        4. Lipa na M-PESA
        5. My Account
    """.trimIndent()

    private val reorderedMain = """
        1. Buy Airtime
        2. Lipa na M-PESA
        3. Send Money
        4. Withdraw Cash
    """.trimIndent()

    private val lipaSub = """
        Lipa na M-PESA
        1. Pay Bill
        2. Buy Goods and Services
        3. Pochi La Biashara
    """.trimIndent()

    @Test
    fun parses_classic_numbered_menu() {
        val options = UssdMenuMatcher.parseOptions(classicMain)
        assertEquals(5, options.size)
        assertEquals("Send Money", options[0].label)
        assertEquals("4", options[3].number)
    }

    @Test
    fun send_money_follows_label_when_reordered() {
        assertEquals("3", UssdMenuMatcher.resolveChoice(reorderedMain, listOf("send money")))
        assertEquals("1", UssdMenuMatcher.resolveChoice(classicMain, listOf("send money")))
    }

    @Test
    fun lipa_and_pay_bill_match() {
        assertEquals("2", UssdMenuMatcher.resolveChoice(reorderedMain, listOf("lipa na m-pesa", "lipa")))
        assertEquals("1", UssdMenuMatcher.resolveChoice(lipaSub, listOf("pay bill", "paybill")))
        assertEquals(
            "2",
            UssdMenuMatcher.resolveChoice(lipaSub, listOf("buy goods and services", "buy goods"))
        )
    }

    private val sendSubmenu = """
        Send Money
        1 Send Money
        2 Send money to other network
        3 M-pesa global
        4 Pochi la biashara
    """.trimIndent()

    private val sendSubmenuWithoutPlain = """
        Send Money
        1 Send money to other network
        2 M-pesa global
        3 Pochi la biashara
    """.trimIndent()

    @Test
    fun send_submenu_picks_plain_send_money_not_other_network() {
        val choice = UssdMenuMatcher.resolveChoice(
            sendSubmenu,
            labels = listOf("send money", "send to m-pesa"),
            excludeLabels = listOf("other network", "global", "pochi")
        )
        assertEquals("1", choice)
    }

    @Test
    fun send_submenu_without_plain_option_does_not_guess() {
        val choice = UssdMenuMatcher.resolveChoice(
            sendSubmenuWithoutPlain,
            labels = listOf("send money"),
            excludeLabels = listOf("other network", "global", "pochi")
        )
        assertNull(choice)
    }

    @Test
    fun looks_like_menu() {
        assertTrue(UssdMenuMatcher.looksLikeMenu(classicMain))
        assertFalse(UssdMenuMatcher.looksLikeMenu("Enter amount"))
    }
}

class ConfirmDetectorTest {

    private val yesNo = """
        Send money to JANE DOE 0712345678
        Ksh 100.00
        1. Yes
        2. No
    """.trimIndent()

    @Test
    fun detects_yes_no_confirm() {
        val yes = ConfirmDetector.resolveYes(yesNo)
        val no = ConfirmDetector.resolveNo(yesNo)
        assertEquals("yes digit", "1", yes)
        assertEquals("no digit", "2", no)
        assertTrue(
            "expected yes/no confirm; yes=$yes no=$no options=${UssdMenuMatcher.parseOptions(yesNo)}",
            ConfirmDetector.isYesNoConfirm(yesNo)
        )
    }

    @Test
    fun ignores_ordinary_menu() {
        assertFalse(
            ConfirmDetector.isYesNoConfirm(
                "1. Send Money\n2. Withdraw Cash\n3. Buy Goods"
            )
        )
    }

    @Test
    fun summary_drops_menu_lines() {
        val summary = ConfirmDetector.summaryLines(yesNo)
        assertTrue(summary.contains("JANE DOE"))
        assertFalse(summary.contains("1. Yes"))
    }
}

class FulizaPromptTest {

    private val offer = """
        Insufficient M-PESA balance to send Ksh500.00.
        Fuliza M-PESA amount is Ksh380.00
        Access fee Ksh3.80
        1. Accept
        2. Decline
    """.trimIndent()

    @Test
    fun detects_fuliza_accept_decline() {
        assertTrue(FulizaPrompt.isOffer(offer))
        assertEquals("1", ConfirmDetector.resolveYes(offer))
        assertEquals("2", ConfirmDetector.resolveNo(offer))
        val summary = ConfirmDetector.summaryLines(offer)
        assertTrue(summary.contains("Fuliza"))
        assertTrue(summary.contains("380"))
        assertFalse(summary.contains("1. Accept"))
    }

    @Test
    fun ignores_fuliza_submenu() {
        val menu = """
            Fuliza M-PESA
            1. Opt In
            2. Check Balance
            3. Opt Out
        """.trimIndent()
        assertFalse(FulizaPrompt.isOffer(menu))
        assertEquals(
            "1",
            UssdMenuMatcher.resolveChoice(
                menu,
                labels = listOf("opt in"),
                excludeLabels = listOf("opt out")
            )
        )
    }

    @Test
    fun ordinary_yes_no_is_not_a_fuliza_offer() {
        assertFalse(
            FulizaPrompt.isOffer(
                "Send money to JANE\nKsh 100.00\n1. Yes\n2. No"
            )
        )
    }
}

class PinDetectorTest {

    @Test
    fun detects_enter_pin() {
        assertTrue(PinDetector.isPinPrompt("Enter M-PESA PIN"))
        assertTrue(PinDetector.isPinPrompt("Please enter your PIN to complete"))
    }

    @Test
    fun detects_masked_field() {
        assertTrue(PinDetector.isPinPrompt("****"))
    }

    @Test
    fun ignores_ordinary_menu() {
        assertFalse(PinDetector.isPinPrompt("1. Send Money\n2. Withdraw Cash"))
        assertFalse(PinDetector.isPinPrompt("Enter amount"))
    }
}
