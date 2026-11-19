package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.testTransaction
import com.lipabill.app.viewmodel.CategoryBudget
import com.lipabill.app.viewmodel.GoalProgress
import com.lipabill.app.viewmodel.SavingsGoal
import com.lipabill.app.viewmodel.encodeBudgets
import com.lipabill.app.viewmodel.encodeGoals
import com.lipabill.app.viewmodel.parseBudgets
import com.lipabill.app.viewmodel.parseGoals
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyMomentsTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.parse("2026-11-18")
    private fun at(date: String) = LocalDate.parse(date).atTime(12, 0).toInstant(zone).toEpochMilli()
    private var n = 0
    private fun food(date: String, amount: Double = 1000.0) =
        testTransaction(code = "F${n++}", type = TransactionType.BUY_GOODS, amount = amount, counterpartyName = "GLOVO KENYA", timestampMillis = at(date))

    @Test
    fun budgetCrossingEightyPercentFiresOnce() {
        val earlier = listOf(food("2026-11-02", 3000.0), food("2026-11-09", 3000.0))
        val tx = food("2026-11-18", 1000.0) // 6,000 → 7,000 of 8,000 crosses 80%
        val budgets = listOf(CategoryBudget(SpendCategory.FOOD, 8000.0))
        val moments = MoneyMoments.evaluate(tx, earlier, budgets, emptyList(), emptyMap(), emptySet(), today, zone)
        val budget = moments.single { it.key.startsWith("budget") }
        assertEquals("budget80:food:2026-11", budget.key)
        assertTrue(MoneyMoments.evaluate(tx, earlier, budgets, emptyList(), emptyMap(), setOf(budget.key), today, zone)
            .none { it.key == budget.key })
    }

    @Test
    fun budgetUsedUpWins() {
        val earlier = listOf(food("2026-11-02", 7500.0))
        val moments = MoneyMoments.evaluate(
            food("2026-11-18", 1000.0), earlier, listOf(CategoryBudget(SpendCategory.FOOD, 8000.0)),
            emptyList(), emptyMap(), emptySet(), today, zone
        )
        assertTrue(moments.any { it.key == "budget100:food:2026-11" })
    }

    @Test
    fun fourthFoodDeliveryThisWeek() {
        val earlier = listOf(food("2026-11-13"), food("2026-11-15"), food("2026-11-17"))
        val moments = MoneyMoments.evaluate(food("2026-11-18"), earlier, emptyList(), emptyList(), emptyMap(), emptySet(), today, zone)
        assertEquals("Food delivery #4 this week", moments.single().title)
        // Three in a week is not a nudge yet.
        assertTrue(MoneyMoments.evaluate(food("2026-11-18"), earlier.take(2), emptyList(), emptyList(), emptyMap(), emptySet(), today, zone).isEmpty())
    }

    @Test
    fun paydaySuggestsSetAsideForAGoal() {
        val small = listOf(
            testTransaction(code = "R1", type = TransactionType.RECEIVED, amount = 500.0, timestampMillis = at("2026-10-10")),
            testTransaction(code = "R2", type = TransactionType.RECEIVED, amount = 800.0, timestampMillis = at("2026-11-02"))
        )
        val salary = testTransaction(code = "S1", type = TransactionType.RECEIVED, amount = 60000.0, timestampMillis = at("2026-11-18"))
        val goal = GoalProgress(SavingsGoal("g", "School fees", 30000.0, 0L, LocalDate.parse("2027-01-13")), 6000.0, 3000.0)
        val moment = MoneyMoments.evaluate(salary, small, emptyList(), listOf(goal), emptyMap(), emptySet(), today, zone).single()
        assertEquals("payday:2026-11", moment.key)
        assertEquals("Set aside KSh 12,000 for School fees before it gets spent?", moment.body)
    }

    @Test
    fun ordinaryReceiptIsNotPayday() {
        val small = listOf(testTransaction(code = "R1", type = TransactionType.RECEIVED, amount = 4000.0, timestampMillis = at("2026-11-02")))
        val tx = testTransaction(code = "R2", type = TransactionType.RECEIVED, amount = 6000.0, timestampMillis = at("2026-11-18"))
        assertTrue(MoneyMoments.evaluate(tx, small, emptyList(), emptyList(), emptyMap(), emptySet(), today, zone).isEmpty())
    }

    @Test
    fun budgetsAndGoalsRoundTrip() {
        val budgets = listOf(CategoryBudget(SpendCategory.FOOD, 8000.0), CategoryBudget(SpendCategory.TRANSPORT, 3500.0))
        assertEquals(budgets, parseBudgets(encodeBudgets(budgets)))
        val goals = listOf(
            SavingsGoal("a", "School fees", 30000.0, 123L, LocalDate.parse("2027-01-13")),
            SavingsGoal("b", "Trip", 5000.0, 456L)
        )
        assertEquals(goals, parseGoals(encodeGoals(goals)))
        assertTrue(parseGoals("not json").isEmpty())
    }

    @Test
    fun regularBillThatWentUp() {
        fun dstv(code: String, date: String, amount: Double) =
            testTransaction(code = code, type = TransactionType.PAYBILL, amount = amount, counterpartyName = "DSTV", timestampMillis = at(date))
        val earlier = listOf(dstv("D1", "2026-08-10", 2000.0), dstv("D2", "2026-09-10", 2000.0), dstv("D3", "2026-10-10", 2000.0))
        val moment = MoneyMoments.evaluate(dstv("D4", "2026-11-10", 2200.0), earlier, emptyList(), emptyList(), emptyMap(), emptySet(), today, zone).single()
        assertEquals("DSTV went up KSh 200", moment.title)
        assertTrue(MoneyMoments.evaluate(dstv("D4", "2026-11-10", 2000.0), earlier, emptyList(), emptyList(), emptyMap(), emptySet(), today, zone).isEmpty())
    }
}
