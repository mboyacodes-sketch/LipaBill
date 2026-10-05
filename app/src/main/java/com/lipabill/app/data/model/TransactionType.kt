package com.lipabill.app.data.model

enum class TransactionType {
    SENT,
    RECEIVED,
    PAYBILL,
    BUY_GOODS,
    POCHI,
    WITHDRAW,
    DEPOSIT,
    /** A prior payment was reversed and the amount credited back. */
    REVERSED,
    UNKNOWN;

    /** Money leaving the wallet. Unrecognized receipts stay on the expense side. */
    fun isOutgoing(): Boolean = when (this) {
        SENT,
        PAYBILL,
        BUY_GOODS,
        POCHI,
        WITHDRAW,
        UNKNOWN -> true
        RECEIVED,
        DEPOSIT,
        REVERSED -> false
    }
}
