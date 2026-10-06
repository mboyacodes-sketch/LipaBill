package com.lipabill.app.data.sms

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatKes

/** Title and body for a newly saved M-Pesa confirmation. */
fun ledgerNotificationCopy(tx: MpesaTransaction): Pair<String, String> {
    val title = "${tx.type.displayLabel()} · Ksh ${formatKes(tx.amount)}"
    val who = tx.counterpartyName?.trim()?.takeIf { it.isNotEmpty() }
        ?: tx.counterpartyPhone?.trim()?.takeIf { it.isNotEmpty() }
    val body = when {
        who == null -> "M-Pesa confirmation"
        tx.type == TransactionType.FULIZA -> who
        tx.type.isOutgoing() -> "To $who"
        else -> "From $who"
    }
    return title to body
}
