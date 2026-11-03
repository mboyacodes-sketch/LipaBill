package com.lipabill.app.data

import com.lipabill.app.data.local.entity.TransactionEntity
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType

/**
 * Fake rows for debug captures. Names, phones, and codes are sample copy —
 * not inbox SMS, not a PIN, not a real confirmation.
 *
 * Maestro appId: `com.lipabill.app` — see `.maestro/promo.yaml`
 * Home: Send, Pay, Insights, Passes, Transactions
 * Wizard: Next, Continue with Kenya, Keep these on the picture,
 * Continue without SMS, Not now, Continue without
 * Maestro may walk the DEBUG demo payment (steps → PIN → Confirmed) —
 * that path never dials USSD and never stores PIN digits.
 */
object DemoLedger {

    /** Request QR destination. Matches Ada Demo; allowed by promo/scan-pii.sh. */
    const val REQUEST_PHONE = "0700000001"

    /**
     * Sample SGR booking SMS for debug Passes seed / Maestro stills.
     * Matches [com.lipabill.app.data.tickets.BookingConfirmationParser] examples.
     */
    const val SGR_BOOKING_SMS =
        "Booking Confirmed. Ref No: KRCX1Y2Z3. Train: Nairobi to Mombasa E2. " +
            "Date: 20/09/2026. Time: 08:00 AM. Seat: Coach 5, Seat 42. Amount Paid: KES 1,500."

    fun rows(nowMillis: Long): List<TransactionEntity> {
        val day = 24L * 60 * 60 * 1000
        return listOf(
            row(
                code = "DEMO01",
                type = TransactionType.RECEIVED,
                amount = 500.0,
                name = "Ada Demo",
                phone = "0700000001",
                at = nowMillis - 2 * 60 * 60 * 1000,
                balance = 12_450.0,
                source = MoneySource.MPESA
            ),
            row(
                code = "DEMO02",
                type = TransactionType.SENT,
                amount = 200.0,
                name = "Sam Sample",
                phone = "0700000002",
                at = nowMillis - day,
                balance = 11_950.0,
                cost = 7.0,
                source = MoneySource.MPESA
            ),
            row(
                code = "DEMO03",
                type = TransactionType.PAYBILL,
                amount = 1_200.0,
                name = "Demo Paybill",
                phone = "888888",
                at = nowMillis - 2 * day,
                balance = 10_750.0,
                cost = 22.0,
                source = MoneySource.MPESA
            ),
            row(
                code = "DEMO04",
                type = TransactionType.BUY_GOODS,
                amount = 350.0,
                name = "Demo Grocers",
                phone = "123456",
                at = nowMillis - 3 * day,
                balance = 10_400.0,
                cost = 6.0,
                source = MoneySource.MPESA
            ),
            row(
                code = "DEMO05",
                type = TransactionType.RECEIVED,
                amount = 2_000.0,
                name = "Sample Salary",
                phone = null,
                at = nowMillis - 4 * day,
                balance = 10_900.0,
                source = MoneySource.KCB
            ),
            row(
                code = "DEMO06",
                type = TransactionType.SENT,
                amount = 150.0,
                name = "Airtel Sample",
                phone = "0700000003",
                at = nowMillis - 5 * day,
                balance = 800.0,
                source = MoneySource.AIRTEL_MONEY
            )
        )
    }

    private fun row(
        code: String,
        type: TransactionType,
        amount: Double,
        name: String,
        phone: String?,
        at: Long,
        balance: Double?,
        cost: Double? = null,
        source: MoneySource
    ) = TransactionEntity(
        code = code,
        type = type,
        amount = amount,
        counterpartyName = name,
        counterpartyPhone = phone,
        timestampMillis = at,
        balance = balance,
        cost = cost,
        rawBody = "Demo confirmation $code. Not a real message. Not a PIN.",
        source = source
    )
}
