package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

/**
 * Fee M-PESA charged on the past send closest in size to [amount]. Fees are banded,
 * so this is an estimate; null when no past send is near enough to trust.
 */
fun estimatedSendFee(history: List<MpesaTransaction>, amount: Double): Double? {
    if (amount <= 0.0) return null
    val nearest = history
        .filter { it.type == TransactionType.SENT && it.cost != null && (it.amount ?: 0.0) > 0.0 }
        .minByOrNull { abs((it.amount ?: 0.0) - amount) }
        ?: return null
    val gap = abs((nearest.amount ?: 0.0) - amount)
    return nearest.cost.takeIf { gap <= max(amount * 0.25, 100.0) }
}

/** Last nine digits: the same number whether written 07…, 2547… or +2547…. */
fun phoneKey(phone: String?): String? =
    phone?.filter { it.isDigit() }?.takeLast(9)?.takeIf { it.length == 9 }

/** Sends per [phoneKey] in the current calendar month. */
fun monthlySendCounts(
    history: List<MpesaTransaction>,
    today: LocalDate,
    zone: ZoneId
): Map<String, Int> {
    val month = YearMonth.from(today)
    return history
        .filter { tx ->
            tx.type == TransactionType.SENT &&
                YearMonth.from(Instant.ofEpochMilli(tx.timestampMillis).atZone(zone)) == month
        }
        .mapNotNull { phoneKey(it.counterpartyPhone) }
        .groupingBy { it }
        .eachCount()
}

/** Sends to [phone] in the current calendar month. */
fun sendsThisMonth(
    history: List<MpesaTransaction>,
    phone: String,
    today: LocalDate,
    zone: ZoneId
): Int {
    val key = phoneKey(phone) ?: return 0
    return monthlySendCounts(history, today, zone)[key] ?: 0
}

fun sentThisMonthCopy(count: Int): String = when (count) {
    0 -> "first send this month"
    1 -> "sent once this month"
    else -> "sent $count times this month"
}

/** "0712345678" → "07** *** 678": enough to recognise, not enough to read off a screen. */
fun maskPhone(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    val local = if (digits.startsWith("254") && digits.length == 12) "0" + digits.drop(3) else digits
    if (local.length < 7) return phone
    return local.take(2) + "** *** " + local.takeLast(3)
}
