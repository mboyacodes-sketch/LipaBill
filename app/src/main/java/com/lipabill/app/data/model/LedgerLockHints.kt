package com.lipabill.app.data.model

import com.lipabill.app.ussd.LineGatedSources
import com.lipabill.app.ussd.SimLine

/**
 * Ledger picker captions for wallets with no dial line.
 * SMS can still be turned on; Send/Pay need the SIM — so bank-only first-run
 * setups can enable an MNO later without a reinstall.
 */
fun lockedLedgerSources(
    region: AppRegion,
    lines: List<SimLine>,
    phoneAccess: Boolean
): Map<MoneySource, String> =
    LineGatedSources.locked(region, lines, phoneAccess).mapValues { (source, _) ->
        ledgerLockHint(region, source, phoneAccess)
    }

fun ledgerLockHint(
    region: AppRegion,
    source: MoneySource,
    phoneAccess: Boolean
): String {
    val network = LineGatedSources.networkName(region, source)
    return if (phoneAccess) {
        "SMS still imports without this SIM. Send and Pay need an active $network line."
    } else {
        "SMS still imports. Allow phone access for $network Send and Pay."
    }
}
