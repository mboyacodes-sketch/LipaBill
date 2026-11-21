package com.lipabill.app.engage

import com.lipabill.app.testTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.viewmodel.detectRegulars
import com.lipabill.app.viewmodel.payeeKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * JVM fixtures for Insights/Habits rule chain (no Room / SMS parse).
 * Rows are ChallengeTransaction-shaped; expectations cover identity, regulars, categories.
 */
@RunWith(Parameterized::class)
class LedgerInsightsFixtureTest(private val fixtureName: String) {

    @Test
    fun ledgerRulesMatchExpect() {
        val raw = javaClass.classLoader!!
            .getResourceAsStream("ledger_insights/$fixtureName.json")!!
            .bufferedReader()
            .readText()
        val root = JSONObject(raw)
        when (fixtureName) {
            "category_fragments" -> assertCategories(root)
            "two_day_till_not_regular" -> assertDetectRegulars(root)
            "food_peak_restaurant" -> assertHabitsDetect(root)
            else -> assertHabitRegulars(root)
        }
    }

    private fun assertCategories(root: JSONObject) {
        val cases = root.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val type = TransactionType.valueOf(c.getString("type"))
            val name = c.getString("name")
            val expected = SpendCategory.fromId(c.getString("category"))!!
            assertEquals("$type $name", expected, autoSpendCategory(type, name))
        }
    }

    private fun assertHabitRegulars(root: JSONObject) {
        val zone = ZoneId.of(root.optString("zone", "Africa/Nairobi"))
        val now = Instant.parse(root.getString("now")).toEpochMilli()
        val essentials = jsonStringSet(root.optJSONArray("essentials"))
        val rows = jsonRows(root.getJSONArray("rows"))
        val expect = root.getJSONObject("expect")

        expect.optString("payeeKey").takeIf { it.isNotEmpty() }?.let { key ->
            val sample = rows.first()
            assertEquals(key, payeeKey(sample.counterpartyName, sample.counterpartyPhone))
        }

        val frequent = HabitPatterns.regulars(rows, now, zone, essentialKeys = essentials)
        val expectedNames = jsonStringList(expect.optJSONArray("frequentPayees"))
        assertEquals(expectedNames, frequent.map { it.payee })
        if (expectedNames.isEmpty()) {
            assertTrue(frequent.isEmpty())
        }
    }

    private fun assertHabitsDetect(root: JSONObject) {
        val zone = ZoneId.of(root.optString("zone", "Africa/Nairobi"))
        val now = Instant.parse(root.getString("now")).toEpochMilli()
        val rows = jsonRows(root.getJSONArray("rows"))
        val expect = root.getJSONObject("expect")
        val patterns = HabitPatterns.detect(rows, now, zone)
        val ids = patterns.map { it.id }
        val expectedIds = jsonStringList(expect.optJSONArray("habitIds"))
        expectedIds.forEach { id ->
            assertTrue("missing habit $id in $ids", id in ids)
        }
        val frequent = HabitPatterns.regulars(rows, now, zone)
        assertEquals(jsonStringList(expect.optJSONArray("frequentPayees")), frequent.map { it.payee })
    }

    private fun assertDetectRegulars(root: JSONObject) {
        val zone = ZoneId.of(root.optString("zone", "Africa/Nairobi"))
        val today = LocalDate.parse(root.getString("now"))
        val arr = root.getJSONArray("detectRegulars")
        val txs = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val day = LocalDate.parse(o.getString("at"))
            testTransaction(
                id = (i + 1).toLong(),
                code = "R$i",
                type = TransactionType.valueOf(o.getString("type")),
                amount = o.getDouble("amount"),
                counterpartyName = o.getString("name"),
                timestampMillis = day.atStartOfDay(zone).toInstant().toEpochMilli()
            )
        }
        val keys = detectRegulars(txs, zone, today).map { it.key }
        assertEquals(jsonStringList(root.getJSONObject("expect").optJSONArray("regularKeys")), keys)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<String>> = listOf(
            "phone_essential_suppresses_shop",
            "same_day_not_frequent",
            "category_fragments",
            "food_peak_restaurant",
            "two_day_till_not_regular"
        ).map { arrayOf(it) }
    }
}

private fun jsonStringSet(arr: JSONArray?): Set<String> {
    if (arr == null) return emptySet()
    return (0 until arr.length()).mapTo(linkedSetOf()) { arr.getString(it) }
}

private fun jsonStringList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return List(arr.length()) { arr.getString(it) }
}

private fun jsonRows(arr: JSONArray): List<ChallengeTransaction> =
    List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        ChallengeTransaction(
            id = (i + 1).toLong(),
            timestampMillis = Instant.parse(o.getString("at")).toEpochMilli(),
            counterpartyName = o.optString("name").takeIf { it.isNotEmpty() },
            type = TransactionType.valueOf(o.getString("type")),
            amountCents = o.getLong("cents"),
            counterpartyPhone = o.optString("phone").takeIf { it.isNotEmpty() }
        )
    }
