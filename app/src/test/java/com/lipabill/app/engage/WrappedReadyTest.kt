package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.testTransaction
import com.lipabill.app.viewmodel.monthToDateFlow
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WrappedReadyTest {

    private val nairobi = ZoneId.of("Africa/Nairobi")
    private fun at(s: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(s), nairobi)

    @Test
    fun nextRunIsTheFirstAtNine() {
        assertEquals(at("2026-12-01T09:00"), nextWrappedReady(at("2026-11-27T15:00")))
        assertEquals(at("2026-11-01T09:00"), nextWrappedReady(at("2026-11-01T08:59")))
        assertEquals(at("2026-12-01T09:00"), nextWrappedReady(at("2026-11-01T09:00")))
        assertEquals(at("2027-01-01T09:00"), nextWrappedReady(at("2026-12-15T10:00")))
    }

    @Test
    fun titleNamesTheMonth() {
        assertEquals("Your October is wrapped", wrappedReadyTitle(YearMonth.of(2026, 10)))
    }

    @Test
    fun monthToDateCountsOnlyThisMonth() {
        val zone = ZoneOffset.UTC
        fun tx(
            date: String,
            amount: Double,
            type: TransactionType,
            cost: Double? = null,
            balance: Double? = null
        ) = testTransaction(
            code = "M$date$amount", type = type, amount = amount, cost = cost, balance = balance,
            timestampMillis = LocalDate.parse(date).atTime(12, 0).toInstant(zone).toEpochMilli()
        )
        val ledger = listOf(
            tx("2026-10-30", 9_000.0, TransactionType.RECEIVED, balance = 12_450.0),
            tx("2026-11-02", 40_000.0, TransactionType.RECEIVED, balance = 52_450.0),
            tx("2026-11-10", 1_000.0, TransactionType.SENT, cost = 13.0, balance = 51_437.0),
            tx("2026-11-12", 500.0, TransactionType.FULIZA)
        )
        val flow = monthToDateFlow(ledger, LocalDate.parse("2026-11-27"), zone)!!
        assertEquals(12_450.0, flow.broughtForward, 0.01)
        assertEquals(40_000.0, flow.moneyIn, 0.01)
        assertEquals(1_013.0, flow.moneyOut, 0.01)
        assertEquals(12_450.0 + 40_000.0 - 1_013.0, flow.net, 0.01)
        assertNull(monthToDateFlow(ledger, LocalDate.parse("2026-12-03"), zone))
    }
}
