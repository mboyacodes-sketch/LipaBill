package com.lipabill.app.data.model

/** How many wallets/banks can sit on the picture at once. */
const val MAX_ENABLED_LEDGER_SOURCES = 5

/**
 * Which wallets and banks the ledger reads. Missing storage means every rail
 * in the country (existing installs). An explicit set is intersected with
 * that catalog; empty falls back to the primary wallet.
 * New picks are capped at [MAX_ENABLED_LEDGER_SOURCES] via [toggleLedgerSource].
 */
fun resolvedLedgerSources(
    region: AppRegion,
    stored: Set<MoneySource>?
): Set<MoneySource> {
    val catalog = region.sources
    if (stored == null) return catalog
    return stored.filter { it in catalog }.toSet().ifEmpty { setOf(region.primaryWallet) }
}

/** Primary wallet, then other wallets, then banks by label. */
fun catalogSourceComparator(region: AppRegion): Comparator<MoneySource> =
    compareByDescending<MoneySource> { it == region.primaryWallet }
        .thenByDescending { it.isWallet }
        .thenBy { it.label }

/** Keeps the primary wallet and other wallets first when clipping to the cap. */
fun clipLedgerSources(
    sources: Set<MoneySource>,
    region: AppRegion,
    max: Int = MAX_ENABLED_LEDGER_SOURCES
): Set<MoneySource> {
    if (sources.size <= max) return sources
    return sources.sortedWith(catalogSourceComparator(region)).take(max).toSet()
}

/**
 * First-run / upgrade default rails: primary wallet, one other MNO wallet when present,
 * then common banks — so SMS history is not M-Pesa-only under the 5-rail cap.
 */
fun defaultLedgerSources(
    region: AppRegion,
    max: Int = MAX_ENABLED_LEDGER_SOURCES
): Set<MoneySource> {
    val catalog = region.sources
    val primary = region.primaryWallet
    val otherWallets = catalog
        .filter { it.isWallet && it != primary }
        .sortedBy { it.label }
    // Local retail banks first (only those in [catalog] apply), then shared names.
    val bankPriority = listOf(
        MoneySource.CENTENARY,
        MoneySource.CRDB,
        MoneySource.NMB,
        MoneySource.BK,
        MoneySource.KCB,
        MoneySource.EQUITY,
        MoneySource.COOP,
        MoneySource.NCBA,
        MoneySource.ABSA,
        MoneySource.STANBIC,
        MoneySource.FAMILY,
        MoneySource.I_AND_M
    )
    val banks = bankPriority.filter { it in catalog } +
        catalog.filter { !it.isWallet && it !in bankPriority }.sortedBy { it.label }
    val picks = ArrayList<MoneySource>(max)
    if (primary in catalog) picks += primary
    otherWallets.firstOrNull()?.let { picks += it }
    for (bank in banks) {
        if (picks.size >= max) break
        picks += bank
    }
    for (wallet in otherWallets.drop(1)) {
        if (picks.size >= max) break
        picks += wallet
    }
    return picks.toSet().ifEmpty { setOf(primary) }
}

fun parseStoredSources(raw: String?): Set<MoneySource>? {
    if (raw == null) return null
    return raw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { runCatching { MoneySource.valueOf(it) }.getOrNull() }
        .toSet()
}

fun serializeLedgerSources(sources: Set<MoneySource>): String =
    sources.joinToString(",") { it.name }

fun providerCatalog(region: AppRegion): List<MoneySource> =
    region.sources.sortedWith(catalogSourceComparator(region))

fun groupedProviderCatalog(region: AppRegion): List<Pair<String, List<MoneySource>>> {
    val catalog = providerCatalog(region)
    val wallets = catalog.filter { it.isWallet }
    val banks = catalog.filterNot { it.isWallet }
    return buildList {
        if (wallets.isNotEmpty()) add("Wallets" to wallets)
        if (banks.isNotEmpty()) add("Banks" to banks)
    }
}

fun toggleLedgerSource(
    current: Set<MoneySource>,
    source: MoneySource,
    catalog: Set<MoneySource>,
    max: Int = MAX_ENABLED_LEDGER_SOURCES
): Set<MoneySource> {
    val inCatalog = current.filter { it in catalog }.toSet()
    if (source !in catalog) return inCatalog
    if (source in inCatalog) {
        val next = inCatalog - source
        return next.ifEmpty { inCatalog }
    }
    if (inCatalog.size >= max) return inCatalog
    return inCatalog + source
}

/**
 * Rails shown in the chip row. Prefers the active filter when the list is capped.
 */
fun visibleLedgerSources(
    sources: List<MoneySource>,
    selected: MoneySource?,
    max: Int = MAX_ENABLED_LEDGER_SOURCES
): List<MoneySource> {
    if (sources.size <= max) return sources
    val preferred = selected?.takeIf { it in sources }
    val rest = sources.filter { it != preferred }
    return buildList {
        if (preferred != null) add(preferred)
        addAll(rest.take(max - size))
    }
}
