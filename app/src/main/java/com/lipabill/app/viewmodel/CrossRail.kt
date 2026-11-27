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
 * read-time pass catches what they miss so Insights, Habits, Wrapped, and Home **All**
 * agree on one row per payment. A single Home History chip still shows that rail raw
 * (see [homeHistoryLedger]) so bank statements stay visible when you filter to a bank.
 */
data class CrossRailPair(val keep: MpesaTransaction, val other: MpesaTransaction, val kind: Kind) {
    enum class Kind { TWIN, OWN_TRANSFER }
}

/** Amount-only matches (no rail named) must land within this window. */
internal const val CROSS_RAIL_WINDOW_MS = 10L * 60L * 1000L

/** When one leg names the other rail, bank SMS can arrive much later. */
internal const val CROSS_RAIL_NAMED_WINDOW_MS = 60L * 60L * 1000L

/** Same-direction twins without a name hint must be this close. */
internal const val CROSS_RAIL_TIGHT_MS = 3L * 60L * 1000L

private fun eligible(tx: MpesaTransaction): Boolean =
    (tx.amount ?: 0.0) > 0.0 &&
        tx.type != TransactionType.FULIZA &&
        tx.type != TransactionType.REVERSED &&
        tx.type != TransactionType.UNKNOWN

private fun aliases(source: MoneySource): List<String> {
    val base = source.label.lowercase(Locale.US)
    val compact = base.replace(" ", "").replace("-", "")
    val fromName = source.name.lowercase(Locale.US).replace('_', ' ')
    return when (source) {
        MoneySource.MPESA -> listOf("m-pesa", "mpesa", "safaricom")
        MoneySource.AIRTEL_MONEY -> listOf("airtel money", "airtelmoney", "airtel")
        MoneySource.COOP -> listOf("co-op", "coop", "cooperative", "co-operative")
        MoneySource.EQUITY -> listOf("equity", "eqbank", "equitybank")
        MoneySource.KCB -> listOf("kcb", "kcb bank")
        MoneySource.I_AND_M -> listOf("i&m", "imbank", "i and m")
        else -> listOf(base, compact, fromName).distinct()
    }
}

/** True when one leg's counterparty names the other leg's rail ("KCB", "M-PESA", "Equity"). */
internal fun namesTheOtherRail(a: MpesaTransaction, b: MpesaTransaction): Boolean {
    fun mentions(tx: MpesaTransaction, rail: MoneySource): Boolean {
        val name = tx.counterpartyName?.lowercase(Locale.US) ?: return false
        return aliases(rail).any { it.length >= 3 && name.contains(it) }
    }
    return mentions(a, b.source) || mentions(b, a.source)
}

/**
 * Same principal, or one rail quoting principal+fee while the other splits them.
 * A 1-shilling slack covers rounding in bank advisories.
 */
internal fun crossRailAmountsMatch(a: MpesaTransaction, b: MpesaTransaction): Boolean {
    val aa = a.amount ?: return false
    val bb = b.amount ?: return false
    if (abs(aa - bb) < 1.0) return true
    val aFee = a.cost ?: 0.0
    val bFee = b.cost ?: 0.0
    if (aFee >= 1.0 && abs(aa + aFee - bb) < 1.0) return true
    if (bFee >= 1.0 && abs(bb + bFee - aa) < 1.0) return true
    return false
}

/**
 * Pairs rows from different rails that are almost surely one payment: matching amounts
 * (to the shilling, or principal vs principal+fee), close in time, and either very close
 * same-direction or naming the other rail. Opposite-direction (own transfer) always needs
 * a name hint so a coincidence does not wipe income and spend. Each row joins at most one
 * pair; closest-in-time pairs win.
 */
fun findCrossRailPairs(txs: List<MpesaTransaction>): List<CrossRailPair> {
    val rows = txs.filter(::eligible).sortedBy { it.timestampMillis }
    data class Candidate(val a: MpesaTransaction, val b: MpesaTransaction, val gap: Long)
    val candidates = mutableListOf<Candidate>()
    for (i in rows.indices) {
        val a = rows[i]
        var j = i + 1
        while (j < rows.size && rows[j].timestampMillis - a.timestampMillis <= CROSS_RAIL_NAMED_WINDOW_MS) {
            val b = rows[j]
            j++
            if (a.source == b.source) continue
            if (!crossRailAmountsMatch(a, b)) continue
            val gap = b.timestampMillis - a.timestampMillis
            val named = namesTheOtherRail(a, b)
            val sameDirection = a.type.isOutgoing() == b.type.isOutgoing()
            val ok = when {
                // Own-account moves must name the other rail — never amount-only.
                !sameDirection -> named
                // Same-direction twin: close without a name, or named within the hour.
                named -> true
                gap <= CROSS_RAIL_TIGHT_MS -> true
                else -> false
            }
            if (!ok) continue
            // Anonymous amount-only same-direction already limited to TIGHT above.
            // Named pairs may use the full named window; nothing else may.
            if (!named && gap > CROSS_RAIL_WINDOW_MS) continue
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
        // Prefer the region's primary-style wallet when both legs are wallets.
        val (keep, other) = when {
            c.b.source.isWallet && !c.a.source.isWallet -> c.b to c.a
            c.a.source.isWallet && !c.b.source.isWallet -> c.a to c.b
            c.a.source == MoneySource.MPESA && c.b.source.isWallet -> c.a to c.b
            c.b.source == MoneySource.MPESA && c.a.source.isWallet -> c.b to c.a
            else -> c.a to c.b
        }
        pairs += CrossRailPair(
            keep, other,
            if (sameDirection) CrossRailPair.Kind.TWIN else CrossRailPair.Kind.OWN_TRANSFER
        )
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

/**
 * Ledger ready for money-in / money-out pictures: reversals netted, cross-rail twins dropped,
 * own-account transfers marked. Safe to call on an already-reconciled list.
 */
fun moneyPictureLedger(txs: List<MpesaTransaction>): List<MpesaTransaction> =
    reconcileCrossRail(withoutReversals(txs))

/**
 * Home history rows: drop reversed originals always. Drop bank/wallet twins only when the
 * History chip is **All** — a single bank or wallet chip must still show every statement for
 * that rail (including advisories that twin an M-PESA receipt).
 */
fun homeHistoryLedger(
    rows: List<MpesaTransaction>,
    undone: Set<String>,
    chip: MoneySource?
): List<MpesaTransaction> {
    val cleaned = withoutReversedOriginals(rows, undone)
    return if (chip == null) reconcileCrossRail(cleaned) else cleaned
}
