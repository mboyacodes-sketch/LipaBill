package com.lipabill.app.ui.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType

/** Safaricom DIY reverse window — slightly under 24h so the option doesn't linger. */
internal const val REVERSE_WINDOW_MS = 23L * 60 * 60 * 1000

/**
 * Opens Messages to Safaricom reverse short code 456 with the original
 * confirmation SMS body. Incoming credits cannot be reversed. Outgoing
 * payments stay valid within [REVERSE_WINDOW_MS].
 */
fun canRequestMpesaReverse(
    tx: MpesaTransaction,
    nowMs: Long = System.currentTimeMillis()
): Boolean {
    if (!tx.type.isOutgoing() || tx.type == TransactionType.SAVINGS || tx.type == TransactionType.AIRTIME) {
        return false
    }
    if (tx.rawBody.isBlank()) return false
    val age = nowMs - tx.timestampMillis
    return age in 0 until REVERSE_WINDOW_MS
}

fun openMpesaReverseSms(context: android.content.Context, tx: MpesaTransaction) {
    if (!canRequestMpesaReverse(tx)) {
        Toast.makeText(
            context,
            "Reversal is only available within about 24 hours of the payment",
            Toast.LENGTH_LONG
        ).show()
        return
    }
    val body = tx.rawBody.trim()
    if (body.isEmpty()) {
        Toast.makeText(
            context,
            "No original SMS on this receipt to send to 456",
            Toast.LENGTH_LONG
        ).show()
        return
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:456")
        putExtra("sms_body", body)
        putExtra("android.intent.extra.TEXT", body)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "Couldn’t open Messages", Toast.LENGTH_SHORT).show()
    }
}

/** Receipt code only. The SMS body can include the balance. */
internal fun shareTextFor(tx: MpesaTransaction): String = tx.code
