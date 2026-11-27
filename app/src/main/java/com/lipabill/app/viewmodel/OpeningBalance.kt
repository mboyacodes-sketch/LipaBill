package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sum of each rail's last known SMS balance strictly before [before] (local midnight).
 * Fuliza outstanding is never treated as a wallet/bank opening. Missing balance for a rail
 * contributes nothing — never invent. [sources] null = every source present in [ledger].
 */
fun openingBalanceBefore(
    ledger: List<MpesaTransaction>,
    before: LocalDate,
    zone: ZoneId,
    sources: Set<MoneySource>? = null
): Double {
    val cutoff = before.atStartOfDay(zone).toInstant().toEpochMilli()
    val latest = HashMap<MoneySource, Pair<Long, Double>>()
    for (tx in ledger) {
        if (tx.type == TransactionType.FULIZA) continue
        val bal = tx.balance ?: continue
        if (tx.timestampMillis >= cutoff) continue
        if (sources != null && tx.source !in sources) continue
        val prev = latest[tx.source]
        if (prev == null || tx.timestampMillis > prev.first) {
            latest[tx.source] = tx.timestampMillis to bal
        }
    }
    return latest.values.sumOf { it.second }
}
