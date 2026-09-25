package com.lipabill.app.ui.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.ui.util.formatTimestamp

/** Safaricom DIY reverse window — slightly under 24h so the option doesn't linger. */
internal const val REVERSE_WINDOW_MS = 23L * 60 * 60 * 1000

/**
 * Opens Messages to Safaricom reverse short code 456 with the original
 * confirmation SMS body. Only valid within [REVERSE_WINDOW_MS] of the payment.
 */
fun canRequestMpesaReverse(
    tx: MpesaTransaction,
    nowMs: Long = System.currentTimeMillis()
): Boolean {
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

/**
 * Prefer the original SMS up through the date/time (`on d/m/yy at h:mm AM/PM`),
 * dropping balance / cost / anything after. Falls back to a short structured line
 * when the body is missing or has no timestamp.
 */
internal fun shareTextFor(tx: MpesaTransaction): String {
    truncateAtTimestamp(tx.rawBody)?.let { return it }
    if (tx.rawBody.isNotBlank()) {
        return tx.rawBody.trim()
    }
    return buildString {
        append("Ref: ").append(tx.code).append('\n')
        append("To: ").append(tx.counterpartyName ?: "—").append('\n')
        append("Date: ").append(formatTimestamp(tx.timestampMillis))
    }
}

private val MpesaBodyTimestampEnd = Regex(
    """(?i)on\s+\d{1,2}/\d{1,2}/\d{2,4}\s+at\s+\d{1,2}[:.]\d{2}(?:\s*[AP]M)?\.?"""
)

internal fun truncateAtTimestamp(rawBody: String): String? {
    if (rawBody.isBlank()) return null
    val match = MpesaBodyTimestampEnd.find(rawBody) ?: return null
    return rawBody.substring(0, match.range.last + 1).trimEnd()
}
