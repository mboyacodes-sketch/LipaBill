package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.engage.SpendCategory
import com.lipabill.app.testTransaction
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyPlanTest {

    private val zone = ZoneOffset.UTC
    private fun at(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun received(date: String, amount: Double) =
        testTransaction(type = TransactionType.RECEIVED, amount = amount, timestampMillis = at(date), counterpartyName = "EMPLOYER")

    private fun paid(date: String, amount: Double, name: String, type: TransactionType = TransactionType.BUY_GOODS) =
        testTransaction(type = type, amount = amount, timestampMillis = at(date), counterpartyName = name)

    @Test
    fun paydayFromMonthlySalary() {
        val ledger = listOf(
            received("2026-08-25", 60000.0), received("2026-08-10", 500.0),
            received("2026-09-26", 60000.0), received("2026-09-12", 800.0),
            received("2026-10-25", 61000.0), received("2026-10-03", 300.0)
        )
        assertEquals(LocalDate.parse("2026-11-25"), estimateNextPayday(ledger, LocalDate.parse("2026-11-18"), zone))
        // After this month's payday, the next one is next month.
        assertEquals(LocalDate.parse("2026-12-25"), estimateNextPayday(ledger, LocalDate.parse("2026-11-26"), zone))
    }

    @Test
    fun noPaydayWithoutAClearSalary() {
        val ledger = listOf(received("2026-09-02", 500.0), received("2026-10-15", 700.0), received("2026-10-20", 400.0))
        assertNull(estimateNextPayday(ledger, LocalDate.parse("2026-11-18"), zone))
    }

    @Test
    fun safeToSpendHoldsBackBillsDueBeforePayday() {
        val today = LocalDate.parse("2026-11-18")
        val rent = RegularPayee(
            key = "landlord", name = "LANDLORD", cadence = Cadence.MONTHLY, typicalAmount = 15000.0,
            monthlyCost = 15000.0, lastPaid = LocalDate.parse("2026-10-20"), nextDue = LocalDate.parse("2026-11-20"), payments = 4
        )
        val afterPayday = rent.copy(key = "dstv", name = "DSTV", typicalAmount = 2000.0, nextDue = LocalDate.parse("2026-11-28"))
        val safe = safeToSpend(25000.0, listOf(rent, afterPayday), LocalDate.parse("2026-11-25"), today)!!
        assertEquals(15000.0, safe.reservedForBills, 0.01)
        assertEquals(1, safe.billsDue)
        assertEquals(8, safe.daysLeft)
        assertEquals(10000.0 / 8, safe.perDay, 0.01)
        assertTrue(safe.paydayEstimated)
    }

    @Test
    fun safeToSpendFallsBackToMonthEndAndNeverGoesNegative() {
        val safe = safeToSpend(100.0, emptyList(), null, LocalDate.parse("2026-11-18"))!!
        assertEquals(LocalDate.parse("2026-11-30"), safe.until)
        assertEquals(13, safe.daysLeft)
        assertNull(safeToSpend(null, emptyList(), null, LocalDate.parse("2026-11-18")))
        val broke = safeToSpend(
            500.0,
            listOf(RegularPayee("k", "KPLC", Cadence.MONTHLY, 2000.0, 2000.0, LocalDate.parse("2026-10-20"), LocalDate.parse("2026-11-20"), 3)),
            null,
            LocalDate.parse("2026-11-18")
        )!!
        assertEquals(0.0, broke.perDay, 0.0)
    }

    @Test
    fun budgetPaceProjectsTheMonth() {
        val today = LocalDate.parse("2026-11-15")
        val ledger = listOf(
            paid("2026-11-03", 2000.0, "GLOVO"),
            paid("2026-11-10", 2500.0, "UBER EATS"),
            paid("2026-10-30", 9000.0, "GLOVO") // last month: not counted
        )
        val status = budgetStatuses(listOf(CategoryBudget(SpendCategory.FOOD, 8000.0)), ledger, emptyMap(), today, zone).single()
        assertEquals(4500.0, status.spent, 0.01)
        assertEquals(4500.0 / 15 * 30, status.projected, 0.01)
        assertEquals(BudgetPace.WATCH, status.pace)
        assertEquals(15, status.daysLeft)
    }

    @Test
    fun savingsAndCashOutAreNotSpend() {
        val spend = spendByCategory(
            listOf(
                testTransaction(type = TransactionType.SAVINGS, amount = 5000.0),
                testTransaction(type = TransactionType.WITHDRAW, amount = 3000.0),
                paid("2026-11-01", 400.0, "NAIVAS")
            ),
            emptyMap()
        )
        assertEquals(setOf(SpendCategory.ESSENTIALS), spend.keys)
    }

    @Test
    fun savingsFillGoalsOldestFirst() {
        val school = SavingsGoal("a", "School fees", 10000.0, at("2026-09-01"), LocalDate.parse("2027-01-10"))
        val trip = SavingsGoal("b", "Trip", 5000.0, at("2026-10-01"))
        val ledger = listOf(
            testTransaction(type = TransactionType.SAVINGS, amount = 3000.0, timestampMillis = at("2026-08-15")), // before any goal
            testTransaction(type = TransactionType.SAVINGS, amount = 6000.0, timestampMillis = at("2026-09-10")),
            testTransaction(type = TransactionType.SAVINGS, amount = 7000.0, timestampMillis = at("2026-10-10"))
        )
        val progress = goalProgress(listOf(school, trip), ledger, LocalDate.parse("2026-11-18"))
        assertEquals(10000.0, progress[0].saved, 0.01)
        assertTrue(progress[0].done)
        assertEquals(3000.0, progress[1].saved, 0.01)
        assertNull(progress[0].perWeekNeeded)
        assertNull(progress[1].perWeekNeeded)
    }

    @Test
    fun goalWithDeadlineSaysHowMuchPerWeek() {
        val goal = SavingsGoal("a", "Laptop", 7000.0, at("2026-11-01"), LocalDate.parse("2026-12-30"))
        val progress = goalProgress(listOf(goal), emptyList(), LocalDate.parse("2026-11-18")).single()
        assertNotNull(progress.perWeekNeeded)
        assertEquals(7000.0 / 6.0, progress.perWeekNeeded!!, 0.01)
    }

    @Test
    fun categoryChangesRankBiggestMoves() {
        val changes = categoryChanges(
            now = mapOf(SpendCategory.FOOD to 6000.0, SpendCategory.TRANSPORT to 1200.0),
            before = mapOf(SpendCategory.FOOD to 4000.0, SpendCategory.TRANSPORT to 1000.0, SpendCategory.SHOPPING to 3000.0)
        )
        assertEquals(listOf(SpendCategory.SHOPPING, SpendCategory.FOOD, SpendCategory.TRANSPORT), changes.map { it.category })
        assertEquals(50, changes[1].percent)
        assertEquals(-100, changes[0].percent)
    }
}
