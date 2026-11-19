package com.lipabill.app.engage

import com.lipabill.app.testTransaction
import com.lipabill.app.data.local.dao.PayeeLinkRow
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.viewmodel.idsMatchingPayeeKey
import com.lipabill.app.viewmodel.payeeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendCategoryTest {

    private fun tx(
        type: TransactionType,
        name: String? = null,
        phone: String? = null,
        id: Long = 1L
    ) = testTransaction(
        id = id,
        code = "ABC",
        type = type,
        counterpartyName = name,
        counterpartyPhone = phone
    )

    @Test
    fun auto_glovo_is_food() {
        assertEquals(SpendCategory.FOOD, autoSpendCategory(TransactionType.BUY_GOODS, "GLOVO KE"))
    }

    @Test
    fun auto_kplc_is_bills() {
        assertEquals(SpendCategory.BILLS, autoSpendCategory(TransactionType.PAYBILL, "KPLC Prepaid"))
    }

    @Test
    fun auto_person_send_is_people() {
        assertEquals(SpendCategory.PEOPLE, autoSpendCategory(TransactionType.SENT, "Jane Wanjiku"))
    }

    @Test
    fun auto_airtime_and_received() {
        assertEquals(SpendCategory.AIRTIME, autoSpendCategory(TransactionType.AIRTIME, "Airtime Purchase"))
        assertEquals(SpendCategory.INCOME, autoSpendCategory(TransactionType.RECEIVED, "Boss"))
    }

    @Test
    fun auto_uber_ride_is_transport_not_food() {
        assertEquals(SpendCategory.TRANSPORT, autoSpendCategory(TransactionType.BUY_GOODS, "Uber"))
        assertEquals(SpendCategory.FOOD, autoSpendCategory(TransactionType.BUY_GOODS, "Uber Eats"))
    }

    @Test
    fun auto_naivas_is_essentials() {
        assertEquals(SpendCategory.ESSENTIALS, autoSpendCategory(TransactionType.BUY_GOODS, "Naivas Westlands"))
    }

    @Test
    fun override_wins_over_auto() {
        val payment = tx(TransactionType.BUY_GOODS, "GLOVO KE")
        val key = payeeKey(payment)!!
        val resolved = resolveSpendCategory(
            payment,
            key,
            mapOf(key to SpendCategory.SHOPPING.id)
        )
        assertEquals(SpendCategory.SHOPPING, resolved)
    }

    @Test
    fun encode_and_parse_overrides() {
        val encoded = setOf(
            encodeSpendCategoryOverride("till:123456", SpendCategory.FOOD),
            "bad",
            "x="
        )
        val map = parseSpendCategoryOverrides(encoded)
        assertEquals(SpendCategory.FOOD.id, map["till:123456"])
        assertEquals(1, map.size)
    }

    @Test
    fun linked_ids_match_phone_key() {
        val rows = listOf(
            PayeeLinkRow(1, "Jane", "0712345678", TransactionType.SENT),
            PayeeLinkRow(2, "Jane W", "254712345678", TransactionType.SENT),
            PayeeLinkRow(3, "Other", "0799999999", TransactionType.SENT)
        )
        val key = payeeKey("Jane", "0712345678")!!
        assertTrue(key.startsWith("tel:"))
        assertEquals(listOf(1L, 2L), idsMatchingPayeeKey(rows, key))
    }

    @Test
    fun linked_ids_match_folded_merchant_name() {
        val rows = listOf(
            PayeeLinkRow(10, "Naivas Westlands", null, TransactionType.BUY_GOODS),
            PayeeLinkRow(11, "NAIVAS  WESTLANDS", null, TransactionType.BUY_GOODS),
            PayeeLinkRow(12, "Quickmart", null, TransactionType.BUY_GOODS)
        )
        val key = payeeKey("Naivas Westlands", null)!!
        assertEquals(listOf(10L, 11L), idsMatchingPayeeKey(rows, key))
    }
}
