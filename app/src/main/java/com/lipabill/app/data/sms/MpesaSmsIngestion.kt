package com.lipabill.app.data.sms

import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.parser.MpesaSmsParser

/**
 * Shared SMS → ledger gate used by [MpesaSmsReceiver] and inbox backfill.
 *
 * Pipeline: normalize/validate sender → confirmation structure → parse.
 * Does not dial, open Accessibility, or trigger payments.
 */
object MpesaSmsIngestion {

    /**
     * True only when both the sender and body pass [MpesaSmsFilter].
     * A body that merely contains "Confirmed" is not enough.
     */
    fun shouldAccept(address: String?, body: String?): Boolean =
        MpesaSmsFilter.isMpesaSender(address) &&
            MpesaSmsFilter.isTransactionConfirmation(body)

    /**
     * Returns a parsed transaction when [shouldAccept] passes; otherwise null.
     * Never throws for malformed input.
     */
    fun acceptAndParse(
        address: String?,
        body: String?,
        timestampMillis: Long
    ): MpesaTransaction? {
        if (!shouldAccept(address, body)) return null
        val text = body ?: return null
        if (text.isBlank()) return null
        return runCatching {
            MpesaSmsParser.parse(text, timestampMillis)
        }.getOrNull()
    }
}
