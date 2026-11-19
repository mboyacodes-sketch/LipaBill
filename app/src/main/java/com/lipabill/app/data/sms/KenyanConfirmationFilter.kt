package com.lipabill.app.data.sms

import com.lipabill.app.data.model.AppRegion

/**
 * Shared gate for wallet and bank confirmation SMS that are not Kenya Safaricom
 * M-Pesa. Kenya M-Pesa stays on [MpesaSmsFilter] (Confirmed + M-PESA balance).
 * Used for every other East African rail, including Vodacom M-Pesa in Tanzania.
 */
object KenyanConfirmationFilter {

    private val OTP = Regex(
        """\b(otp|verification code|one[- ]time(?:\s+password)?|do not share this code|pin is|authori[sz]ation code|activation code)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Movement verbs in any tense banks use ("deposited", "withdrawn", "transfer"). */
    private val MOVEMENT = Regex(
        """\b(credited|debited|sent|received|paid|withdraw(?:n|al|s)?|deposit(?:ed|s)?|transfer(?:red)?|reversed|purchase[d]?|bought|processed)\b""",
        RegexOption.IGNORE_CASE
    )

    /** "Your transaction / M-Pesa payment of KES … to … was successful" (NCBA and similar). */
    private val SUCCESSFUL_TRANSACTION = Regex(
        """\b(?:transaction|payment)\s+of\b.{0,160}?\b(?:was|is|has\s+been)\s+successful""",
        RegexOption.IGNORE_CASE
    )

    /** "Dial *123# …" offers carry no receipt id or balance; real confirmations do. */
    private val DIAL_PROMPT = Regex("""\bdial\s*\*""", RegexOption.IGNORE_CASE)
    private val RECEIPT_MARK = Regex(
        """\b(tid|trans(?:action)?\s*id|ref(?:erence)?|receipt|bal(?:ance)?|completed|confirmed|successful(?:ly)?|transaction cost|fee)\b""",
        RegexOption.IGNORE_CASE
    )

    fun isOtpOrPin(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        // "<#>" opens Android SMS-retriever code messages ("You have initiated … Authorization code").
        return body.trimStart().startsWith("<#>") || OTP.containsMatchIn(body)
    }

    private fun isPromo(body: String): Boolean =
        DIAL_PROMPT.containsMatchIn(body) && !RECEIPT_MARK.containsMatchIn(body)

    fun isMoneyConfirmation(
        body: String?,
        region: AppRegion = AppRegion.DEFAULT
    ): Boolean {
        if (body.isNullOrBlank()) return false
        if (isOtpOrPin(body)) return false
        if (isPromo(body)) return false
        if (!region.matchesCurrency(body)) return false
        return MOVEMENT.containsMatchIn(body) ||
            SUCCESSFUL_TRANSACTION.containsMatchIn(body) ||
            body.contains("Confirmed", ignoreCase = true)
    }
}
