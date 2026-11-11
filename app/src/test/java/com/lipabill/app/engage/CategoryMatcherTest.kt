package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryMatcherTest {

    private fun tx(
        type: TransactionType,
        name: String?,
        cents: Long = 100_00,
        fee: Long = 0L
    ) = ChallengeTransaction(1L, 0L, name, type, cents, fee)

    @Test
    fun food_delivery_matches_messy_sms_names() {
        assertTrue(CategoryMatcher.matches(CategoryRules.foodDelivery, "GLOVO KE"))
        assertTrue(CategoryMatcher.matches(CategoryRules.foodDelivery, "UberEats Nairobi"))
        assertTrue(CategoryMatcher.matches(CategoryRules.foodDelivery, "BOLT  FOOD"))
        assertFalse(CategoryMatcher.matches(CategoryRules.foodDelivery, "Java House"))
        assertFalse(CategoryMatcher.matches(CategoryRules.foodDelivery, null as String?))
    }

    @Test
    fun everyday_essentials_cover_groceries_and_utilities() {
        assertTrue(CategoryMatcher.matches(CategoryRules.everydayEssentials, "Naivas Westlands"))
        assertTrue(CategoryMatcher.matches(CategoryRules.everydayEssentials, "Kenya Power"))
        assertTrue(CategoryMatcher.matches(CategoryRules.everydayEssentials, "Nairobi Water"))
        assertFalse(CategoryMatcher.matches(CategoryRules.everydayEssentials, "Artcaffe"))
    }

    @Test
    fun discretionary_skips_essentials_and_zero_amounts() {
        assertTrue(CategoryMatcher.matches(CategoryRules.discretionary, tx(TransactionType.BUY_GOODS, "Java House")))
        assertFalse(CategoryMatcher.matches(CategoryRules.discretionary, tx(TransactionType.BUY_GOODS, "Naivas")))
        assertTrue(CategoryMatcher.matches(CategoryRules.discretionary, tx(TransactionType.SENT, "Glovo")))
        assertFalse(CategoryMatcher.matches(CategoryRules.discretionary, tx(TransactionType.BUY_GOODS, "Java", cents = 0)))
    }

    @Test
    fun fee_like_breaks_on_fee_or_fuliza() {
        assertTrue(CategoryMatcher.matches(CategoryRules.feeLike, tx(TransactionType.FULIZA, null)))
        assertTrue(CategoryMatcher.matches(CategoryRules.feeLike, tx(TransactionType.SENT, "Jane", fee = 7_00)))
        assertFalse(CategoryMatcher.matches(CategoryRules.feeLike, tx(TransactionType.SENT, "Jane", fee = 0)))
    }

    @Test
    fun payee_prefix_rule_and_by_id() {
        val rule = CategoryRules.byId("payee:Java House")!!
        assertTrue(CategoryMatcher.matches(rule, "JAVA HOUSE THIKA"))
        assertFalse(CategoryMatcher.matches(rule, "Artcaffe"))
        assertNull(CategoryRules.byId("payee:"))
        assertEquals(CategoryRules.foodDelivery, CategoryRules.byId("food_delivery"))
    }

    @Test
    fun any_outgoing_includes_airtime_but_not_received() {
        assertTrue(CategoryMatcher.matches(CategoryRules.anyOutgoing, tx(TransactionType.AIRTIME, "Airtime")))
        assertFalse(CategoryMatcher.matches(CategoryRules.anyOutgoing, tx(TransactionType.RECEIVED, "Boss")))
    }
}
