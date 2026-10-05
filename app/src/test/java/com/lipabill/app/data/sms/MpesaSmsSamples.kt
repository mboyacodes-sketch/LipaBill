package com.lipabill.app.data.sms

/** Confirmation SMS shared by parser, filter, and ingestion tests. */
internal const val FULIZA_DRAW_100_SMS =
    "UI3E552R94 Confirmed. Fuliza M-PESA amount is Ksh 100.00. Access Fee charged Ksh 1.00. " +
        "Total Fuliza M-PESA outstanding amount is Ksh192.97 due on 02/10/26. " +
        "To check daily charges, Dial *334#OK Select Query Charges"

internal const val FULIZA_DRAW_40_SMS =
    "UI3E551JPG Confirmed. Fuliza M-PESA amount is Ksh 40.00. Access Fee charged Ksh 0.40. " +
        "Total Fuliza M-PESA outstanding amount is Ksh61.67 due on 02/10/26. " +
        "To check daily charges, Dial *334#OK Select Query Charges"

internal const val REVERSAL_JULIUS_SMS =
    "UJ4R7Q7R31 confirmed. Your original transaction UJ3R78QL2Z in favour of " +
        "JULIUS VIAZI SUPPLIER has been reversed successfully on 4/10/26 at 8:15 PM " +
        "and Ksh125.00 has been credited to your M-PESA account. " +
        "Your new M-PESA account balance is Ksh4,801.97."
