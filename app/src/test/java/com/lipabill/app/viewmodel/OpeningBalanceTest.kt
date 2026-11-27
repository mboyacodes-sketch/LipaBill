package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.testTransaction
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class OpeningBalanceTest {

    private val zone = ZoneOffset.UTC
    private fun at(date: String, hour: Int = 12): Long =
        LocalDate.parse(date).atTime(hour, 0).toInstant(zone).toEpochMilli()

    private fun tx(
        date: String,
        balance: Double?,
        source: MoneySource = MoneySource.MPESA,
        type: TransactionType = TransactionType.SENT,
        amount: Double = 100.0,
        hour: Int = 12
    ) = testTransaction(
        code = "B$date$source",
        type = type,
        amount = amount,
        timestampMillis = at(date, hour),
        balance = balance,
        source = source
    )

    @Test
    fun picks_last_balance_before_the_first() {
        val ledger = listOf(
            tx("2026-10-20", 8_000.0),
            tx("2026-10-31", 12_450.0, hour = 18),
            tx("2026-11-01", 11_000.0, hour = 9),
            tx("2026-11-05", 9_000.0)
        )
        assertEquals(
            12_450.0,
            openingBalanceBefore(ledger, LocalDate.parse("2026-11-01"), zone),
            0.01
        )
    }

    @Test
    fun ignores_fuliza_and_sums_enabled_rails() {
        val ledger = listOf(
            tx("2026-10-30", 5_000.0, MoneySource.MPESA),
            tx("2026-10-30", 20_000.0, MoneySource.KCB, type = TransactionType.DEPOSIT),
            tx("2026-10-30", 3_000.0, MoneySource.MPESA, type = TransactionType.FULIZA),
            // No Airtel balance before November — contributes 0.
            tx("2026-11-02", 500.0, MoneySource.AIRTEL_MONEY)
        )
        assertEquals(
            25_000.0,
            openingBalanceBefore(ledger, LocalDate.parse("2026-11-01"), zone),
            0.01
        )
        assertEquals(
            5_000.0,
            openingBalanceBefore(
                ledger,
                LocalDate.parse("2026-11-01"),
                zone,
                sources = setOf(MoneySource.MPESA)
            ),
            0.01
        )
    }

    @Test
    fun returns_zero_when_no_prior_balance() {
        val ledger = listOf(
            tx("2026-11-02", 1_000.0),
            tx("2026-10-15", null)
        )
        assertEquals(0.0, openingBalanceBefore(ledger, LocalDate.parse("2026-11-01"), zone), 0.01)
        assertEquals(0.0, openingBalanceBefore(emptyList(), LocalDate.parse("2026-11-01"), zone), 0.01)
    }
}
