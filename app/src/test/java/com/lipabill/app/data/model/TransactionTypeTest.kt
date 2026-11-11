package com.lipabill.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTypeTest {

    @Test
    fun outgoing_covers_spend_savings_airtime_and_unknown() {
        assertTrue(TransactionType.SENT.isOutgoing())
        assertTrue(TransactionType.PAYBILL.isOutgoing())
        assertTrue(TransactionType.BUY_GOODS.isOutgoing())
        assertTrue(TransactionType.POCHI.isOutgoing())
        assertTrue(TransactionType.WITHDRAW.isOutgoing())
        assertTrue(TransactionType.SAVINGS.isOutgoing())
        assertTrue(TransactionType.AIRTIME.isOutgoing())
        assertTrue(TransactionType.UNKNOWN.isOutgoing())
    }

    @Test
    fun inflow_and_credit_types_are_not_outgoing() {
        assertFalse(TransactionType.RECEIVED.isOutgoing())
        assertFalse(TransactionType.DEPOSIT.isOutgoing())
        assertFalse(TransactionType.REVERSED.isOutgoing())
        assertFalse(TransactionType.FULIZA.isOutgoing())
    }
}
