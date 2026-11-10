package com.lipabill.app.data.export

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.viewmodel.reversedCodes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val csvDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val csvTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val HEADER = listOf(
    "Date", "Time", "Code", "Type", "Direction", "Counterparty", "Phone",
    "Amount", "Fee", "Balance", "Reversed"
)

/**
 * RFC 4180 CSV of [txs], oldest first, for an accountant or a tax question.
 * Counterparty text comes from SMS, so cells that a spreadsheet would run as a
 * formula are prefixed with an apostrophe.
 */
fun ledgerCsv(txs: List<MpesaTransaction>, zone: ZoneId): String {
    val undone = reversedCodes(txs)
    val out = StringBuilder()
    out.append(HEADER.joinToString(",") { csvCell(it) }).append("\r\n")
    for (tx in txs.sortedBy { it.timestampMillis }) {
        val at = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone)
        val reversed = when {
            tx.type == TransactionType.REVERSED -> "reversal"
            tx.code.uppercase(Locale.US) in undone -> "yes"
            else -> ""
        }
        val row = listOf(
            at.format(csvDate),
            at.format(csvTime),
            tx.code,
            tx.type.displayLabel(),
            if (tx.type.isOutgoing()) "out" else "in",
            tx.counterpartyName.orEmpty(),
            tx.counterpartyPhone.orEmpty(),
            csvAmount(tx.amount),
            csvAmount(tx.cost),
            csvAmount(tx.balance),
            reversed
        )
        out.append(row.joinToString(",") { csvCell(it) }).append("\r\n")
    }
    return out.toString()
}

private fun csvAmount(value: Double?): String =
    value?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()

internal fun csvCell(raw: String): String {
    val guarded = if (raw.isNotEmpty() && raw[0] in "=+-@\t\r") "'$raw" else raw
    val needsQuotes = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    return if (needsQuotes) "\"" + guarded.replace("\"", "\"\"") + "\"" else guarded
}
