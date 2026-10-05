package com.lipabill.app.data.sms

/**
 * True only when the SMS sender/header is M-Pesa itself
 * (e.g. "MPESA", "M-PESA") — not marketing or other numbers
 * that merely contain those letters.
 */
object MpesaSmsFilter {

    /**
     * "M-PESA balance", reversal wording "M-PESA account balance",
     * and Fuliza "Fuliza M-PESA outstanding".
     */
    private val MPESA_BALANCE = Regex(
        "mpesa(?: account)? balance|fuliza mpesa outstanding"
    )

    fun isMpesaSender(address: String?): Boolean {
        if (address.isNullOrBlank()) return false
        val normalized = address.trim()
            .uppercase()
            .replace("-", "")
            .replace(" ", "")
            .replace("_", "")
        return normalized == "MPESA"
    }

    /**
     * Real transaction confirmations include both "Confirmed" and either an
     * "M-PESA balance" line or a Fuliza outstanding line. Promo / PIN / other
     * MPESA texts lack these.
     */
    fun isTransactionConfirmation(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val hasConfirmed = body.contains("Confirmed", ignoreCase = true)
        val normalized = body.lowercase().replace("-", "")
        return hasConfirmed && MPESA_BALANCE.containsMatchIn(normalized)
    }
}
