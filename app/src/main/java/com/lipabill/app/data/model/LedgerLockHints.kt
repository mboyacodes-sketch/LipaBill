package com.lipabill.app.data.model

import com.lipabill.app.ussd.LineGatedSources
import com.lipabill.app.ussd.SimLine

/**
 * Ledger picker captions for wallets with no matching SIM when phone access is on.
 * Those chips stay off; banks remain free. Without phone access, nothing is locked yet.
 */
fun lockedLedgerSources(
    region: AppRegion,
    lines: List<SimLine>,
    phoneAccess: Boolean
): Map<MoneySource, String> {
    if (!phoneAccess) return emptyMap()
    return LineGatedSources.locked(region, lines, phoneAccess = true).mapValues { (source, _) ->
        ledgerLockHint(region, source, phoneAccess = true)
    }
}

fun ledgerLockHint(
    region: AppRegion,
    source: MoneySource,
    phoneAccess: Boolean
): String {
    val network = LineGatedSources.networkName(region, source)
    return if (phoneAccess) {
        "Needs a $network SIM in this phone."
    } else {
        "Allow phone access so LipaBill can match $network."
    }
}

/** Last-four (or two lines) from SIMs that carry each wallet — for picker chips. */
fun simWalletNumberHints(
    region: AppRegion,
    lines: List<SimLine>
): Map<MoneySource, String> {
    val bySource = linkedMapOf<MoneySource, MutableList<String>>()
    for (line in lines) {
        val source = LineGatedSources.sourceFor(region, line) ?: continue
        val last4 = line.numberHint
            ?.filter(Char::isDigit)
            ?.takeLast(4)
            ?.takeIf { it.length == 4 }
            ?: continue
        bySource.getOrPut(source) { mutableListOf() }.add("…$last4")
    }
    return bySource.mapValues { (_, parts) -> parts.distinct().joinToString(" / ") }
}
