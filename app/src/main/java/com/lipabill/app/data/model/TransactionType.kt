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
    /** Fuliza overdraft draw. Outstanding sits on the Fuliza balance, not M-PESA. */
    FULIZA,
    /** Money moved from M-PESA into the user's own savings, such as M-Shwari. */
    SAVINGS,
    /** Airtime bought from the M-PESA balance. */
    AIRTIME,
    UNKNOWN;

    /** Money leaving the M-PESA wallet. Unrecognized receipts stay on the expense side. */
    fun isOutgoing(): Boolean = when (this) {
        SENT,
        PAYBILL,
        BUY_GOODS,
        POCHI,
        WITHDRAW,
        SAVINGS,
        AIRTIME,
        UNKNOWN -> true
        RECEIVED,
        DEPOSIT,
        REVERSED,
        FULIZA -> false
    }
}
