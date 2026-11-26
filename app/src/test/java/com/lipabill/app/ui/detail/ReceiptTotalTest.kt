package com.lipabill.app.ui.detail

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.testTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptTotalTest {

    @Test
    fun money_out_includes_the_fee() {
        assertEquals(1013.0, receiptTotal(testTransaction(type = TransactionType.SENT, amount = 1000.0, cost = 13.0))!!, 0.0)
        assertEquals(1520.0, receiptTotal(testTransaction(type = TransactionType.PAYBILL, amount = 1500.0, cost = 20.0))!!, 0.0)
        assertEquals(3434.0, receiptTotal(testTransaction(type = TransactionType.WITHDRAW, amount = 3400.0, cost = 34.0))!!, 0.0)
    }

    @Test
    fun free_or_unknown_fee_is_just_the_amount() {
        assertEquals(90.0, receiptTotal(testTransaction(type = TransactionType.BUY_GOODS, amount = 90.0, cost = 0.0))!!, 0.0)
        assertEquals(90.0, receiptTotal(testTransaction(type = TransactionType.BUY_GOODS, amount = 90.0, cost = null))!!, 0.0)
    }

    @Test
    fun money_in_is_the_amount_received() {
        assertEquals(500.0, receiptTotal(testTransaction(type = TransactionType.RECEIVED, amount = 500.0, cost = 5.0))!!, 0.0)
        assertEquals(500.0, receiptTotal(testTransaction(type = TransactionType.DEPOSIT, amount = 500.0))!!, 0.0)
    }

    @Test
    fun missing_amount_stays_missing() {
        assertNull(receiptTotal(testTransaction(amount = null, cost = 13.0)))
    }
}
