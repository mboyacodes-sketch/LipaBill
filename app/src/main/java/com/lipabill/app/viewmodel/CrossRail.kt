package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import java.util.Locale
import kotlin.math.abs

/**
 * One payment can reach the ledger twice when two rails both text about it:
 *
 * - [Kind.TWIN]: same direction on two rails (a bank "paid via M-PESA" advisory next to the
 *   M-PESA receipt). It is one payment, so the bank copy must not count again.
 * - [Kind.OWN_TRANSFER]: opposite directions (bank → M-PESA top-up, M-PESA → own bank paybill).
 *   The user's own money moved; neither leg is spend or income.
 *
 * Save-time rules in ConfirmationIngestion already drop the common bank mirrors. This
 * read-time pass catches what they miss, so Home, Insights, Habits and Wrapped agree.
 */
data class CrossRailPair(val keep: MpesaTransaction, val other: MpesaTransaction, val kind: Kind) {
    enum class Kind { TWIN, OWN_TRANSFER }
}

/** Two legs of one payment land within this window. */
internal const val CROSS_RAIL_WINDOW_MS = 10L * 60L * 1000L

/** Without a name hint the legs must be this close to count as one payment. */
internal const val CROSS_RAIL_TIGHT_MS = 3L * 60L * 1000L

private fun eligible(tx: MpesaTransaction): Boolean =
    (tx.amount ?: 0.0) > 0.0 &&
        tx.type != TransactionType.FULIZA &&
        tx.type != TransactionType.REVERSED &&
        tx.type != TransactionType.UNKNOWN

private fun aliases(source: MoneySource): List<String> {
    val base = source.label.lowercase(Locale.US)
    return when (source) {
        MoneySource.MPESA -> listOf("m-pesa", "mpesa", "safaricom")
        else -> listOf(base, base.replace(" ", ""), source.name.lowercase(Locale.US).replace('_', ' '))
    }
}

/** True when one leg's counterparty names the other leg's rail ("KCB", "M-PESA", "Equity"). */
private fun namesTheOtherRail(a: MpesaTransaction, b: MpesaTransaction): Boolean {
    fun mentions(tx: MpesaTransaction, rail: MoneySource): Boolean {
        val name = tx.counterpartyName?.lowercase(Locale.US) ?: return false
        return aliases(rail).any { it.length >= 3 && name.contains(it) }
    }
    return mentions(a, b.source) || mentions(b, a.source)
}

/**
 * Pairs rows from different rails that are almost surely one payment: same amount (to the
 * shilling), close in time, and either very close or naming the other rail. Each row joins
 * at most one pair; closest-in-time pairs win.
 */
fun findCrossRailPairs(txs: List<MpesaTransaction>): List<CrossRailPair> {
    val rows = txs.filter(::eligible).sortedBy { it.timestampMillis }
    data class Candidate(val a: MpesaTransaction, val b: MpesaTransaction, val gap: Long)
    val candidates = mutableListOf<Candidate>()
    for (i in rows.indices) {
        val a = rows[i]
        var j = i + 1
        while (j < rows.size && rows[j].timestampMillis - a.timestampMillis <= CROSS_RAIL_WINDOW_MS) {
            val b = rows[j]
            j++
            if (a.source == b.source) continue
            if (abs((a.amount ?: 0.0) - (b.amount ?: 0.0)) >= 1.0) continue
            val gap = b.timestampMillis - a.timestampMillis
            if (gap > CROSS_RAIL_TIGHT_MS && !namesTheOtherRail(a, b)) continue
            candidates += Candidate(a, b, gap)
        }
    }
    val used = HashSet<MpesaTransaction>()
    val pairs = mutableListOf<CrossRailPair>()
    for (c in candidates.sortedBy { it.gap }) {
        if (c.a in used || c.b in used) continue
        used += c.a
        used += c.b
        val sameDirection = c.a.type.isOutgoing() == c.b.type.isOutgoing()
        // Keep the wallet receipt: it carries the payee, the fee and the balance.
        val (keep, other) = if (c.b.source.isWallet && !c.a.source.isWallet) c.b to c.a else c.a to c.b
        pairs += CrossRailPair(keep, other, if (sameDirection) CrossRailPair.Kind.TWIN else CrossRailPair.Kind.OWN_TRANSFER)
    }
    return pairs
}

/**
 * Ledger as the money pictures should count it: bank copies of a wallet payment dropped,
 * and both legs of a transfer between the user's own accounts marked [MpesaTransaction.ownTransfer].
 */
fun reconcileCrossRail(txs: List<MpesaTransaction>): List<MpesaTransaction> {
    val pairs = findCrossRailPairs(txs)
    if (pairs.isEmpty()) return txs
    val drop = HashSet<MpesaTransaction>()
    val own = HashSet<MpesaTransaction>()
    for (p in pairs) {
        when (p.kind) {
            CrossRailPair.Kind.TWIN -> drop += p.other
            CrossRailPair.Kind.OWN_TRANSFER -> {
                own += p.keep
                own += p.other
            }
        }
    }
    return txs.mapNotNull { tx ->
        when (tx) {
            in drop -> null
            in own -> tx.copy(ownTransfer = true)
            else -> tx
        }
    }
}
