package com.lipabill.app.data.sms

import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.parser.digitsOnly
import com.lipabill.app.ussd.PendingPaymentReceipt
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Picks which recent ledger row or open dial a failure SMS should undo.
 *
 * Same-rail voids match on amount (party digits boost). Cross-rail voids are
 * allowed only when the failure sender is unmapped or a wallet, the row is a
 * bank (or any rail if sender unmapped), and party digits agree — so an
 * M-Pesa "insufficient funds" can clear a mistaken Co-op deposit.
 */
object FailureLedgerMatch {

    const val AMOUNT_EPS = 0.051
    val TTL_MS: Long = TimeUnit.MINUTES.toMillis(45)

    fun findVoidCandidate(
        amount: Double,
        source: MoneySource?,
        partyDigits: Set<String>,
        candidates: List<MpesaTransaction>,
        failureAtMillis: Long
    ): MpesaTransaction? {
        val since = failureAtMillis - TTL_MS
        // Amount-only same-rail voids are unsafe when several rows share the amount.
        if (partyDigits.isEmpty()) {
            val amountHits = candidates.count { tx ->
                val rowAmount = tx.amount ?: return@count false
                abs(rowAmount - amount) <= AMOUNT_EPS &&
                    tx.timestampMillis >= since &&
                    tx.timestampMillis <= failureAtMillis + TimeUnit.MINUTES.toMillis(2)
            }
            if (amountHits > 1) return null
        }
        var best: MpesaTransaction? = null
        var bestScore = 0
        for (tx in candidates) {
            val score = scoreRow(amount, source, partyDigits, tx, since, failureAtMillis)
            if (score > bestScore) {
                best = tx
                bestScore = score
            }
        }
        return best
    }

    fun matchesOpenPayment(
        amount: Double,
        partyDigits: Set<String>,
        snapshot: PendingPaymentReceipt.Snapshot,
        failureAtMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (abs(amount - snapshot.amount) > AMOUNT_EPS) return false
        if (snapshot.startedAtMillis < failureAtMillis - TTL_MS) return false
        if (partyDigits.isEmpty()) return true
        val snapDigits = buildSet {
            digitsOnly(snapshot.counterpartyPhone)?.let { add(it) }
            snapshot.accountHint?.filter { it.isDigit() }?.takeIf { it.length >= 4 }?.let { add(it) }
        }
        if (snapDigits.isEmpty()) return true
        return partyDigits.any { fail ->
            snapDigits.any { snap -> digitsOverlap(fail, snap) }
        }
    }

    private fun scoreRow(
        amount: Double,
        source: MoneySource?,
        partyDigits: Set<String>,
        tx: MpesaTransaction,
        sinceMillis: Long,
        failureAtMillis: Long
    ): Int {
        val rowAmount = tx.amount ?: return 0
        if (abs(rowAmount - amount) > AMOUNT_EPS) return 0
        if (tx.timestampMillis < sinceMillis) return 0
        if (tx.timestampMillis > failureAtMillis + TimeUnit.MINUTES.toMillis(2)) return 0

        val sameRail = source != null && tx.source == source
        val crossRailOk = allowsCrossRail(source, tx.source, partyDigits, tx)
        if (!sameRail && !crossRailOk) return 0

        var score = if (sameRail) 3 else 1
        val partyHit = partyDigitsHit(partyDigits, tx)
        when {
            partyDigits.isNotEmpty() && partyHit -> score += 2
            // Cross-rail / unmapped always need a party hit (enforced in allowsCrossRail).
            sameRail -> Unit
        }
        return score
    }

    /**
     * Unmapped sender → any rail if party digits hit.
     * Wallet failure → bank row if party digits hit (M-Pesa fail clearing Co-op deposit).
     */
    private fun allowsCrossRail(
        failureSource: MoneySource?,
        rowSource: MoneySource,
        partyDigits: Set<String>,
        tx: MpesaTransaction
    ): Boolean {
        if (partyDigits.isEmpty()) return false
        if (!partyDigitsHit(partyDigits, tx)) return false
        if (failureSource == null) return true
        return failureSource.isWallet && !rowSource.isWallet
    }

    private fun partyDigitsHit(partyDigits: Set<String>, tx: MpesaTransaction): Boolean {
        if (partyDigits.isEmpty()) return false
        val rowDigits = buildSet {
            digitsOnly(tx.counterpartyPhone)?.let { add(it) }
            tx.counterpartyName?.filter { it.isDigit() }?.takeIf { it.length >= 6 }?.let { add(it) }
        }
        if (rowDigits.isEmpty()) return false
        return partyDigits.any { fail ->
            rowDigits.any { row -> digitsOverlap(fail, row) }
        }
    }

    private fun digitsOverlap(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length >= 6 && b.length >= 6 && (a.endsWith(b.takeLast(6)) || b.endsWith(a.takeLast(6)))) {
            return true
        }
        return a.contains(b) || b.contains(a)
    }
}
