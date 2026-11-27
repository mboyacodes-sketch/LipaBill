package com.lipabill.app.viewmodel

import com.lipabill.app.ui.util.formatKsh

/**
 * The "Copy text" summary for a Wrapped story. Leaves out every amount when [showAmounts]
 * is off and every payee name when [hideNames] is on, so it is safe to paste anywhere.
 */
fun wrapShareText(
    cards: WrapCards,
    periodLabel: String,
    personalityTitle: String,
    showAmounts: Boolean,
    hideNames: Boolean
): String = buildString {
    append("My $periodLabel on LipaBill: ")
    append("${cards.txnCount} transaction${if (cards.txnCount == 1) "" else "s"}")
    append(" on ${cards.activeDays} of ${cards.coveredDays} days.")
    if (showAmounts) {
        append("\nIn ${formatKsh(cards.moneyIn)} · Out ${formatKsh(cards.moneyOut)}")
        if (cards.fees > 0.0) append(" · Fees ${formatKsh(cards.fees)}")
        append(".")
    }
    if (!hideNames) {
        cards.topSpots.firstOrNull()?.let { append("\nTop spot: ${it.name}.") }
        cards.topPeople.firstOrNull()?.let { append("\nSent most to: ${it.name}.") }
    }
    cards.fuliza?.let { append("\nFuliza: ${it.count} time${if (it.count == 1) "" else "s"}.") }
    append("\nMoney personality: $personalityTitle.")
}
