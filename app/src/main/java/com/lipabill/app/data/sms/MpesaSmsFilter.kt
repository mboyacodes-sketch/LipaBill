package com.lipabill.app.data.sms

/**
 * True only when the SMS sender/header is M-Pesa itself
 * (e.g. "MPESA", "M-PESA") — not marketing or other numbers
 * that merely contain those letters.
 */
object MpesaSmsFilter {

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
     * Real transaction confirmations include both "Confirmed" and an
     * "M-PESA balance" line. Promo / PIN / other MPESA texts lack these.
     */
    fun isTransactionConfirmation(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val hasConfirmed = body.contains("Confirmed", ignoreCase = true)
        // "M-PESA balance", "MPESA balance", "M-Pesa balance", …
        val normalized = body.lowercase().replace("-", "")
        val hasMpesaBalance = normalized.contains("mpesa balance")
        return hasConfirmed && hasMpesaBalance
    }
}
