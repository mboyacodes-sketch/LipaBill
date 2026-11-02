package com.lipabill.app.data.sms

import com.lipabill.app.data.model.AppRegion

/**
 * Shared gate for wallet and bank confirmation SMS that are not Kenya Safaricom
 * M-Pesa. Kenya M-Pesa stays on [MpesaSmsFilter] (Confirmed + M-PESA balance).
 * Used for every other East African rail, including Vodacom M-Pesa in Tanzania.
 */
object KenyanConfirmationFilter {

    private val OTP = Regex(
        """\b(otp|verification code|one[- ]time(?:\s+password)?|do not share this code|pin is)\b""",
        RegexOption.IGNORE_CASE
    )

    private val MOVEMENT = Regex(
        """\b(credited|debited|sent|received|paid|withdraw|deposit|transferred|reversed|purchase[d]?|bought|processed)\b""",
        RegexOption.IGNORE_CASE
    )

    fun isOtpOrPin(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        return OTP.containsMatchIn(body)
    }

    fun isMoneyConfirmation(
        body: String?,
        region: AppRegion = AppRegion.DEFAULT
    ): Boolean {
        if (body.isNullOrBlank()) return false
        if (isOtpOrPin(body)) return false
        if (!region.matchesCurrency(body)) return false
        return MOVEMENT.containsMatchIn(body) ||
            body.contains("Confirmed", ignoreCase = true)
    }
}
