package com.lipabill.app.data.model

/**
 * Which wallets and banks the ledger reads. Missing storage means every rail
 * in the country (existing installs). An explicit set is intersected with
 * that catalog; empty falls back to the primary wallet.
 */
fun resolvedLedgerSources(
    region: AppRegion,
    stored: Set<MoneySource>?
): Set<MoneySource> {
    val catalog = region.sources
    if (stored == null) return catalog
    val kept = stored.filter { it in catalog }.toSet()
    return kept.ifEmpty { setOf(region.primaryWallet) }
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
    region.sources.sortedWith(
        compareByDescending<MoneySource> { it == region.primaryWallet }
            .thenByDescending { it.isWallet }
            .thenBy { it.label }
    )

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
    catalog: Set<MoneySource>
): Set<MoneySource> {
    val inCatalog = current.filter { it in catalog }.toSet()
    if (source !in catalog) return inCatalog
    val next = if (source in inCatalog) inCatalog - source else inCatalog + source
    return next.ifEmpty { inCatalog }
}
