package com.lipabill.app.ussd

import com.lipabill.app.testTransaction
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentWalletTest {

    @Test
    fun kenya_plmn_and_names_map_to_wallets() {
        assertEquals(
            PaymentWallet.SAFARICOM_MPESA,
            PaymentWallet.detect(AppRegion.KENYA, "Safaricom", mcc = 639, mnc = 2)
        )
        assertEquals(
            PaymentWallet.AIRTEL_KENYA,
            PaymentWallet.detect(AppRegion.KENYA, "Airtel", mcc = 639, mnc = 3)
        )
        assertNull(PaymentWallet.detect(AppRegion.KENYA, "Airtel", mcc = 641, mnc = 6))
    }

    @Test
    fun uganda_plmn_maps_airtel_and_mtn() {
        assertEquals(
            PaymentWallet.AIRTEL_UGANDA,
            PaymentWallet.detect(AppRegion.UGANDA, "Airtel", mcc = 641, mnc = 6)
        )
        assertEquals(
            PaymentWallet.MTN_UGANDA,
            PaymentWallet.detect(AppRegion.UGANDA, "MTN", mcc = 641, mnc = 10)
        )
        assertNull(PaymentWallet.detect(AppRegion.UGANDA, "Safaricom", mcc = 639, mnc = 2))
    }

    @Test
    fun ussd_codes_match_research() {
        assertEquals("*334#", PaymentWallet.SAFARICOM_MPESA.ussdCode)
        assertEquals("*334#", PaymentWallet.AIRTEL_KENYA.ussdCode)
        assertEquals("*185#", PaymentWallet.AIRTEL_UGANDA.ussdCode)
        assertEquals("*165#", PaymentWallet.MTN_UGANDA.ussdCode)
    }

    @Test
    fun resolve_prefers_saved_eligible_sim() {
        val lines = listOf(
            sim(1, "Safaricom", 639, 2),
            sim(2, "Airtel", 639, 3)
        )
        val target = PaymentDialResolver.resolve(AppRegion.KENYA, lines, preferredSubscriptionId = 2)
        assertNotNull(target)
        assertEquals(2, target!!.subscriptionId)
        assertEquals(PaymentWallet.AIRTEL_KENYA, target.wallet)
    }

    @Test
    fun resolve_blocks_region_mismatch() {
        val lines = listOf(sim(1, "Safaricom", 639, 2))
        assertNull(
            PaymentDialResolver.resolve(AppRegion.UGANDA, lines, preferredSubscriptionId = 1)
        )
    }

    @Test
    fun airtel_kenya_plan_excludes_safaricom_lipa_targets() {
        val plan = AirtelKenyaMenuBuilder.build(
            tx(TransactionType.BUY_GOODS, phone = "123456"),
            amountOverride = 100.0
        )
        assertNotNull(plan)
        assertEquals("*334#", plan!!.ussdCode)
        assertEquals(PaymentWallet.AIRTEL_KENYA, plan.wallet)
        val menus = plan.actions.filterIsInstance<UssdAction.ChooseMenu>()
        // Real Airtel menu: Paybill&Tills → Buy Goods → M-pesa Till (never the Airtel Money Till).
        assertTrue(menus.first().labels.any { it.contains("paybill&tills", ignoreCase = true) })
        assertTrue(menus.first().excludeLabels.any { it.contains("lipa na m-pesa") })
        assertTrue(menus.last().labels.any { it.contains("m-pesa till", ignoreCase = true) })
        assertTrue(menus.last().excludeLabels.any { it.contains("airtel money till") })
        assertFalse(plan.watchFuliza)
    }

    @Test
    fun airtel_uganda_uses_185() {
        val plan = AirtelUgandaMenuBuilder.build(
            tx(TransactionType.SENT, phone = "0772123456"),
            amountOverride = 5000.0
        )
        assertNotNull(plan)
        assertEquals("*185#", plan!!.ussdCode)
        assertTrue(
            plan.actions.filterIsInstance<UssdAction.ChooseMenu>()
                .flatMap { it.excludeLabels }
                .any { it.contains("lipa na m-pesa") }
        )
    }

    @Test
    fun mtn_uganda_uses_165() {
        val plan = MtnUgandaMenuBuilder.build(
            tx(TransactionType.SENT, phone = "0772123456"),
            amountOverride = 5000.0
        )
        assertNotNull(plan)
        assertEquals("*165#", plan!!.ussdCode)
        assertEquals(PaymentWallet.MTN_UGANDA, plan.wallet)
    }

    @Test
    fun wallet_menu_plans_routes_by_wallet() {
        val sent = tx(TransactionType.SENT, phone = "0712345678")
        assertEquals(
            PaymentWallet.SAFARICOM_MPESA,
            WalletMenuPlans.build(PaymentWallet.SAFARICOM_MPESA, sent, 10.0)!!.wallet
        )
        assertEquals(
            "*185#",
            WalletMenuPlans.build(
                PaymentWallet.AIRTEL_UGANDA,
                tx(TransactionType.SENT, phone = "0772123456"),
                10.0
            )!!.ussdCode
        )
    }

    @Test
    fun uganda_can_send_pay() {
        assertTrue(AppRegion.UGANDA.canSendPay)
        assertFalse(AppRegion.TANZANIA.canSendPay)
        assertFalse(AppRegion.RWANDA.canSendPay)
    }

    @Test
    fun builders_reject_zero_amount_and_unsupported_types() {
        assertNull(
            AirtelKenyaMenuBuilder.build(
                tx(TransactionType.SENT, phone = "0712345678"),
                amountOverride = 0.0
            )
        )
        assertNull(
            WalletMenuPlans.build(
                PaymentWallet.AIRTEL_UGANDA,
                tx(TransactionType.RECEIVED, phone = "0772123456"),
                100.0
            )
        )
        assertNull(
            WalletMenuPlans.build(
                PaymentWallet.MTN_UGANDA,
                tx(TransactionType.SENT, phone = null),
                100.0
            )
        )
        assertNull(
            WalletMenuPlans.build(
                PaymentWallet.SAFARICOM_MPESA,
                tx(TransactionType.WITHDRAW, phone = "0712345678"),
                100.0
            )
        )
    }

    @Test
    fun resolve_falls_back_when_preferred_sim_ineligible() {
        val lines = listOf(
            sim(1, "Safaricom", 639, 2),
            sim(2, "Airtel", 639, 3)
        )
        // Preferred id missing → first eligible for region.
        val target = PaymentDialResolver.resolve(
            AppRegion.KENYA,
            lines,
            preferredSubscriptionId = 99
        )
        assertNotNull(target)
        assertEquals(PaymentWallet.SAFARICOM_MPESA, target!!.wallet)
    }

    @Test
    fun resolve_empty_lines_is_null() {
        assertNull(
            PaymentDialResolver.resolve(
                AppRegion.KENYA,
                emptyList(),
                preferredSubscriptionId = 1
            )
        )
    }

    @Test
    fun dual_sim_kenya_switch_preference_returns_airtel() {
        val lines = listOf(
            sim(1, "Safaricom", 639, 2),
            sim(2, "Airtel", 639, 3)
        )
        val preferredAirtel = PaymentDialResolver.resolve(
            AppRegion.KENYA,
            lines,
            preferredSubscriptionId = 2
        )
        assertEquals(PaymentWallet.AIRTEL_KENYA, preferredAirtel!!.wallet)
        assertEquals(2, preferredAirtel.subscriptionId)

        val switched = PaymentDialResolver.resolve(
            AppRegion.KENYA,
            lines,
            preferredSubscriptionId = 1
        )
        assertEquals(PaymentWallet.SAFARICOM_MPESA, switched!!.wallet)
        assertEquals(1, switched.subscriptionId)
    }

    @Test
    fun dual_safaricom_requires_explicit_line_pick() {
        val lines = listOf(
            sim(1, "Safaricom", 639, 2),
            sim(2, "Safaricom", 639, 2)
        )
        assertTrue(PaymentDialResolver.requiresExplicitLinePick(AppRegion.KENYA, lines))
        val groups = PaymentDialResolver.sameWalletSiblingGroups(AppRegion.KENYA, lines)
        assertEquals(1, groups.size)
        assertEquals(PaymentWallet.SAFARICOM_MPESA, groups.single().wallet)
        assertEquals(listOf(1, 2), groups.single().lines.map { it.subscriptionId })
        assertEquals(
            "Two Safaricom lines — pick which pays.",
            PaymentDialResolver.explicitLinePickMessage(PaymentWallet.SAFARICOM_MPESA)
        )
        val sibling = PaymentDialResolver.unusedSibling(
            AppRegion.KENYA,
            lines,
            triedSubscriptionId = 1
        )
        assertEquals(2, sibling!!.subscriptionId)
    }

    @Test
    fun safaricom_plus_airtel_does_not_require_explicit_pick() {
        val lines = listOf(
            sim(1, "Safaricom", 639, 2),
            sim(2, "Airtel", 639, 3)
        )
        assertFalse(PaymentDialResolver.requiresExplicitLinePick(AppRegion.KENYA, lines))
        assertTrue(PaymentDialResolver.sameWalletSiblingGroups(AppRegion.KENYA, lines).isEmpty())
    }

    @Test
    fun settings_enabled_wallets_gate_send_pay_lines() {
        val lines = listOf(
            sim(1, "Safaricom", 639, 2),
            sim(2, "Airtel", 639, 3)
        )
        assertEquals(
            listOf(1),
            PaymentDialResolver.eligibleLines(
                AppRegion.KENYA,
                lines,
                enabled = setOf(MoneySource.MPESA, MoneySource.KCB)
            ).map { it.subscriptionId }
        )
        assertEquals(
            PaymentWallet.SAFARICOM_MPESA,
            PaymentDialResolver.resolve(
                AppRegion.KENYA,
                lines,
                preferredSubscriptionId = 2,
                enabled = setOf(MoneySource.MPESA)
            )!!.wallet
        )
    }

    @Test
    fun bank_money_source_never_in_dial_eligibility() {
        // Banks are ledger-only: eligibility is SIM/MNO wallet detection, not MoneySource.
        val lines = listOf(sim(1, "Safaricom", 639, 2))
        val eligible = PaymentDialResolver.eligibleLines(AppRegion.KENYA, lines)
        assertTrue(eligible.isNotEmpty())
        assertNull(
            PaymentWallet.preferForSource(
                AppRegion.KENYA,
                MoneySource.KCB,
                available = emptyList()
            )
        )
        assertEquals(
            PaymentWallet.SAFARICOM_MPESA,
            PaymentWallet.preferForSource(
                AppRegion.KENYA,
                MoneySource.KCB,
                available = listOf(PaymentWallet.SAFARICOM_MPESA)
            )
        )
        PaymentWallet.entries.forEach { wallet ->
            assertFalse(
                "Bank ledger sources must not map to dial wallets as badge-only banks",
                wallet.ledgerSource.name.contains("KCB") ||
                    wallet.ledgerSource.name.contains("EQUITY") ||
                    wallet.ledgerSource.name.contains("COOP")
            )
        }
    }

    @Test
    fun empty_or_wrong_country_sim_null_resolve_and_missing_message() {
        assertNull(
            PaymentDialResolver.resolve(
                AppRegion.KENYA,
                emptyList(),
                preferredSubscriptionId = -1
            )
        )
        assertEquals(
            "Insert a Safaricom or Airtel Kenya SIM to pay.",
            PaymentDialResolver.missingPaySimMessage(AppRegion.KENYA)
        )
        val ugandaOnly = listOf(sim(1, "MTN", 641, 10))
        assertNull(
            PaymentDialResolver.resolve(
                AppRegion.KENYA,
                ugandaOnly,
                preferredSubscriptionId = 1
            )
        )
        assertTrue(PaymentDialResolver.eligibleLines(AppRegion.KENYA, ugandaOnly).isEmpty())
        assertEquals(
            "Insert an Airtel or MTN Uganda SIM to pay.",
            PaymentDialResolver.missingPaySimMessage(AppRegion.UGANDA)
        )
        assertEquals(
            "Turn on M-Pesa or Airtel Money in Ledger to Send and Pay.",
            PaymentDialResolver.payUnavailableMessage(
                AppRegion.KENYA,
                setOf(com.lipabill.app.data.model.MoneySource.KCB)
            )
        )
        assertEquals(
            "Insert a Safaricom or Airtel Kenya SIM to pay.",
            PaymentDialResolver.payUnavailableMessage(
                AppRegion.KENYA,
                setOf(com.lipabill.app.data.model.MoneySource.MPESA)
            )
        )
    }

    private fun sim(id: Int, carrier: String, mcc: Int, mnc: Int) = SimLine(
        subscriptionId = id,
        simSlotIndex = id - 1,
        displayName = carrier,
        carrierName = carrier,
        numberHint = null,
        label = "SIM $id · $carrier",
        isSafaricom = carrier.contains("Safaricom", ignoreCase = true),
        mcc = mcc,
        mnc = mnc
    )

    private fun tx(
        type: TransactionType,
        phone: String? = "0712345678",
        source: MoneySource = MoneySource.MPESA
    ) = testTransaction(
        id = 1,
        type = type,
        counterpartyName = "Test",
        counterpartyPhone = phone,
        rawBody = "Account 99",
        source = source
    )
}
