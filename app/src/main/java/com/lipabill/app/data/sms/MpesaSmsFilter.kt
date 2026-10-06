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
        val normalized = confirmedBody(body) ?: return false
        return MPESA_BALANCE.containsMatchIn(normalized)
    }

    /** Fuliza draw SMS. Kept apart from wallet receipts so one history cannot hide the other. */
    fun isFulizaConfirmation(body: String?): Boolean {
        val normalized = confirmedBody(body) ?: return false
        return normalized.contains("fuliza") && normalized.contains("outstanding")
    }

    /** Lowercased confirmation text, or null when the SMS is not a Confirmed message. */
    private fun confirmedBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        if (!body.contains("Confirmed", ignoreCase = true)) return null
        return body.lowercase().replace("-", "")
    }

    /** Wallet receipt: confirmed M-PESA balance line, and not a Fuliza draw. */
    fun isMpesaWalletConfirmation(body: String?): Boolean =
        isTransactionConfirmation(body) && !isFulizaConfirmation(body)
}
