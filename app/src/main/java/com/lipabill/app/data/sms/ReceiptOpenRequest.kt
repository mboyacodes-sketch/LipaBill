package com.lipabill.app.data.sms

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Which payment a notification should open.
 * The row id is the fast path. The M-Pesa code still finds the row if that id is missing.
 */
data class ReceiptOpenRequest(
    val id: Long?,
    val code: String?
) {
    fun deliveryKey(): String = "${id ?: 0}:${code.orEmpty()}"
}

private const val RECEIPT_PREFIX = "lipabill://receipt/"

/** M-Pesa confirmation codes are short alphanumeric tokens. */
private val RECEIPT_CODE = Regex("^[A-Za-z0-9]{6,16}$")

internal fun normalizeReceiptCode(raw: String?): String? =
    raw?.trim()?.takeIf { RECEIPT_CODE.matches(it) }

fun receiptOpenData(id: Long, code: String): String {
    val encoded = URLEncoder.encode(code, Charsets.UTF_8.name())
    return "$RECEIPT_PREFIX$id?code=$encoded"
}

/**
 * Reads a receipt target from notification extras and from `lipabill://receipt/{id}?code=`.
 * A launcher intent, which has neither, returns null.
 */
fun receiptOpenRequestFrom(
    extraId: Long,
    extraCode: String?,
    dataString: String?
): ReceiptOpenRequest? {
    var id = extraId.takeIf { it > 0L }
    var code = normalizeReceiptCode(extraCode)
    val raw = dataString?.trim().orEmpty()
    if (raw.startsWith(RECEIPT_PREFIX)) {
        val rest = raw.removePrefix(RECEIPT_PREFIX)
        val path = rest.substringBefore('?').substringBefore('#')
        val query = rest.substringAfter('?', "")
        if (id == null) {
            id = path.toLongOrNull()?.takeIf { it > 0L }
        }
        if (code == null) {
            code = query.split('&')
                .firstNotNullOfOrNull { part ->
                    val key = part.substringBefore('=')
                    if (key != "code") return@firstNotNullOfOrNull null
                    normalizeReceiptCode(
                        URLDecoder.decode(part.substringAfter('=', ""), Charsets.UTF_8.name())
                    )
                }
        }
    }
    if (id == null && code == null) return null
    return ReceiptOpenRequest(id, code)
}
