package com.lipabill.app

import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType

/**
 * One place to build ledger rows in tests. Every field has a neutral default, so a test only
 * names what it is about.
 */
fun testTransaction(
    id: Long = 0,
    code: String = "ABC123",
    type: TransactionType = TransactionType.SENT,
    amount: Double? = 100.0,
    counterpartyName: String? = null,
    counterpartyPhone: String? = null,
    timestampMillis: Long = 0L,
    balance: Double? = null,
    cost: Double? = null,
    rawBody: String = "",
    source: MoneySource = MoneySource.MPESA
) = MpesaTransaction(
    id = id,
    code = code,
    type = type,
    amount = amount,
    counterpartyName = counterpartyName,
    counterpartyPhone = counterpartyPhone,
    timestampMillis = timestampMillis,
    balance = balance,
    cost = cost,
    rawBody = rawBody,
    source = source
)
