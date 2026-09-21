package com.lipabill.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class RealSmsProbeTest {
    @Test
    fun real_coop_50k_sms() {
        val body =
            "UILR779F4Z Confirmed.You have received Ksh50,000.00 from CO-OPBANK CORPORATE M-PESA PAYMENTS 5186899 on 21/9/26 at 4:35 PM New M-PESA balance is Ksh50,333.28.  See all your balances now https://saf.cx/kWQpy."
        val tx = MpesaSmsParser.parse(body, 1789997778028L)
        println("code=${tx.code} type=${tx.type} amount=${tx.amount} balance=${tx.balance} cost=${tx.cost} name=${tx.counterpartyName}")
        assertEquals(50000.0, tx.amount!!, 0.001)
        assertEquals(50333.28, tx.balance!!, 0.001)
    }

    @Test
    fun real_prior_balance_333() {
        val body =
            "UILR77862J Confirmed. Ksh65.00 sent to Equity Paybill Account for account 0721374585 on 21/9/26 at 10:56 AM New M-PESA balance is Ksh333.28. Transaction cost, Ksh0.00.Amount you can transact within the day is 498,935.00. See all your balances now https://saf.cx/kWQpy"
        val tx = MpesaSmsParser.parse(body, 1789977407974L)
        println("amount=${tx.amount} balance=${tx.balance} cost=${tx.cost}")
        assertEquals(65.0, tx.amount!!, 0.001)
        assertEquals(333.28, tx.balance!!, 0.001)
    }
}
