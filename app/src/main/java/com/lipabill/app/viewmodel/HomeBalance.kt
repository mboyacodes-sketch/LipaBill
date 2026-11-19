package com.lipabill.app.viewmodel

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.ussd.LineGatedSources
import com.lipabill.app.ussd.PaymentWallet
import com.lipabill.app.ussd.SimLine

/**
 * Wallet cards on home: enabled wallets whose SIM is in this phone (only SIM-linked wallets
 * are active), primary first.
 */
internal fun balanceWalletChoices(
    region: AppRegion,
    enabled: Set<MoneySource>,
    lines: List<SimLine>
): List<MoneySource> {
    val onPhone = LineGatedSources.onPhone(region, lines)
    return LineGatedSources.simBound(region)
        .filter { it in enabled && it in onPhone }
        .sortedWith(compareByDescending<MoneySource> { it == region.primaryWallet }.thenBy { it.label })
}

/**
 * Whose balance the pill shows. Pill pick wins (independent of history chips); then the
 * active dial wallet while it is still a choice; then the first enabled MNO / primary.
 */
internal fun resolveBalanceSource(
    active: MoneySource?,
    choices: List<MoneySource>,
    pillPick: MoneySource?,
    primary: MoneySource
): MoneySource =
    pillPick?.takeIf { it in choices }
        ?: active?.takeIf { it in choices }
        ?: choices.firstOrNull()
        ?: primary

/**
 * Balance a Send/Pay/Repeat sheet shows: the wallet it will dial, never another SIM's figure.
 * Before a wallet is resolved, falls back to the home balance.
 */
fun sheetBalance(
    dialWallet: PaymentWallet?,
    balances: Map<MoneySource, Double>,
    homeBalance: Double?
): Double? = if (dialWallet != null) balances[dialWallet.ledgerSource] else homeBalance


/** One wallet card on the home balance stack. */
data class WalletCardUi(
    val source: MoneySource,
    /** "Safaricom · SIM 1"; null when the phone hasn't shared its lines. */
    val lineLabel: String?,
    /** Last four digits of the line's number, when the SIM exposes it. */
    val lastDigits: String?,
    val balance: Double?,
    /** Carrier ("Safaricom"); null without a line. */
    val network: String? = null,
    /** 1-based SIM slot; null without a line. */
    val simSlot: Int? = null
)

/**
 * Cards for the home stack: one per wallet with an active line; a single card for the
 * balance source when there are none (no SIM access yet, or a bank filter in focus).
 */
internal fun walletCards(
    region: AppRegion,
    choices: List<MoneySource>,
    shown: MoneySource,
    lines: List<SimLine>,
    balances: Map<MoneySource, Double>
): List<WalletCardUi> {
    val sources = if (shown in choices) choices else listOf(shown)
    return sources.map { source ->
        val line = lines.firstOrNull { LineGatedSources.sourceFor(region, it) == source }
        WalletCardUi(
            source = source,
            lineLabel = line?.let { "${networkOf(region, it)} · SIM ${it.simSlotIndex + 1}" },
            lastDigits = line?.numberHint?.filter(Char::isDigit)?.takeLast(4)?.takeIf { it.length == 4 },
            balance = balances[source],
            network = line?.let { networkOf(region, it) },
            simSlot = line?.simSlotIndex?.plus(1)
        )
    }
}

/** The wallet network's brand for [line], falling back to what the SIM reports. */
private fun networkOf(region: AppRegion, line: SimLine): String =
    LineGatedSources.sourceFor(region, line)?.let { LineGatedSources.networkName(region, it) } ?: line.carrierName
