package com.lipabill.app.data.sms

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.parser.ConfirmationAmountParsing
import java.util.regex.Pattern

/**
 * Wallet/bank SMS that report a payment did not settle.
 * Not ledger credits — used to reject false confirmations and void a mistaken row.
 */
object FailureSms {

    private val FAILURE_BODY = Regex(
        """\b(?:transaction\s+failed|failed|unsuccessful|not\s+successful|was\s+not\s+successful|declined|timed?\s*out|could\s+not\s+(?:be\s+)?completed|unable\s+to\s+(?:process|complete)|insufficient\s+funds|do\s+not\s+have\s+sufficient)\b""",
        RegexOption.IGNORE_CASE
    )

    private val AMOUNT = Pattern.compile(
        "(?:kshs?|kes|tzs|tshs?|ugx|ushs?|ugsh|rwf|frw)[\\s.]?(${ConfirmationAmountParsing.AMOUNT_CORE})",
        Pattern.CASE_INSENSITIVE
    )

    private val DIGITS = Regex("""\d{6,}""")

    fun isFailure(body: String?): Boolean {
        if (body.isNullOrBlank()) return false
        return FAILURE_BODY.containsMatchIn(body)
    }

    fun extractAmount(body: String?, region: AppRegion = AppRegion.DEFAULT): Double? {
        if (body.isNullOrBlank()) return null
        if (!region.matchesCurrency(body) &&
            AppRegion.entries.none { it.matchesCurrency(body) }
        ) {
            return null
        }
        val normalized = ConfirmationAmountParsing.normalizeBody(body)
        return ConfirmationAmountParsing.extractDouble(AMOUNT, normalized)
    }

    /** Long digit runs (paybill, till, phone) mentioned in the failure text. */
    fun extractPartyDigits(body: String?): Set<String> {
        if (body.isNullOrBlank()) return emptySet()
        return DIGITS.findAll(body).mapTo(HashSet()) { it.value }
    }
}
