package com.lipabill.app.data.sms

import com.lipabill.app.data.model.ActiveRegion
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatKes

fun MpesaTransaction.ledgerKindLabel(): String =
    "${source.label} · ${type.displayLabel()}"

/** Title and body for a newly saved money confirmation. */
fun ledgerNotificationCopy(tx: MpesaTransaction): Pair<String, String> {
    val kind = tx.ledgerKindLabel()
    val title = "$kind · ${ActiveRegion.current.currencySymbol} ${formatKes(tx.amount)}"
    val who = tx.counterpartyName?.trim()?.takeIf { it.isNotEmpty() }
        ?: tx.counterpartyPhone?.trim()?.takeIf { it.isNotEmpty() }
    val body = when {
        who == null -> "Confirmation"
        tx.type == TransactionType.FULIZA -> who
        tx.type.isOutgoing() -> "To $who"
        else -> "From $who"
    }
    return title to body
}
