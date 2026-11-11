package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.SetupBlocker
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.local.entity.ChallengeEntity
import com.lipabill.app.engage.CategoryMatcher
import com.lipabill.app.engage.CategoryRules
import com.lipabill.app.engage.ChallengeClock
import com.lipabill.app.engage.ChallengeStatus
import com.lipabill.app.engage.ChallengeTemplates
import com.lipabill.app.ui.util.inCategoryOrder
import com.lipabill.app.ui.util.displayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Home balance and transaction list. Fuliza is a second ledger, not a payment method. */
enum class WalletAccount(
    val chipLabel: String,
    val balanceCaption: String,
    val balancePrefix: String,
    val ledgerTitle: String,
    val metricsTitle: String,
    val inflowLabel: String,
    val outflowLabel: String,
    val emptyHistoryTitle: String,
    val emptyHistoryDetail: String
) {
    /** Primary ledger (M-Pesa balance; other wallets/banks mix into history). */
    MPESA(
        chipLabel = "M-Pesa",
        balanceCaption = "Available balance",
        balancePrefix = "Available",
        ledgerTitle = "Transactions",
        metricsTitle = "Insights",
        inflowLabel = "Income",
        outflowLabel = "Expenses",
        emptyHistoryTitle = "Nothing in the picture yet",
        emptyHistoryDetail = "Pull down to import confirmation SMS from the wallets and banks you picked."
    ),
    FULIZA(
        chipLabel = "Fuliza",
        balanceCaption = "Fuliza outstanding",
        balancePrefix = "Outstanding",
        ledgerTitle = "Fuliza",
        metricsTitle = "Fuliza insights",
        inflowLabel = "Drawn",
        outflowLabel = "Access fees",
        emptyHistoryTitle = "No Fuliza messages yet",
        emptyHistoryDetail = "Fuliza confirmations from M-PESA show here."
    );

    /** Figure for the account that is selected. Null when there is no history. */
    fun displayedBalance(mpesaBalance: Double?, fulizaOutstanding: Double?): Double? = when (this) {
        MPESA -> mpesaBalance
        FULIZA -> fulizaOutstanding
    }

    /**
     * Send and Pay ride on M-PESA. Fuliza is the overdraft that covers a shortfall,
     * so a zero M-PESA balance, or a Fuliza balance we have already seen, can still start.
     */
    fun canFundPayment(mpesaBalance: Double?, fulizaOutstanding: Double?): Boolean =
        mpesaBalance != null || fulizaOutstanding != null

    /** Why Send and Pay are off when neither balance has arrived yet. */
    fun unfundedPaymentMessage(mpesaBalance: Double?, fulizaOutstanding: Double?): String =
        "Send and Pay need an M-PESA balance from your messages. There isn’t one yet."
}

enum class AnalyticsRange {
    DAYS_7,
    DAYS_30,
    DAYS_90,
    THIS_YEAR,
    YEAR,
    CUSTOM,
    ALL
}

data class TypeBreakdown(
    val type: TransactionType,
    val label: String,
    val count: Int,
    val total: Double
)

data class AnalyticsSummary(
    val incomeTotal: Double = 0.0,
    val expenseTotal: Double = 0.0,
    val dayCount: Long = 1L,
    val byType: List<TypeBreakdown> = emptyList(),
    val topCounterparties: List<CounterpartySpend> = emptyList(),
    val txnCount: Int = 0,
    val range: AnalyticsRange = AnalyticsRange.DAYS_30,
    val selectedYear: Int? = null,
    val availableYears: List<Int> = emptyList(),
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    /** Inclusive bounds for the selected period (named months for the UI). */
    val periodFrom: LocalDate? = null,
    val periodTo: LocalDate? = null,
    val rangeLabel: String = "Last 30 days",
    val hasLedger: Boolean = false,
    val storyHeadline: String = "",
    val storyDetail: String? = null,
    val quietDays: Int = 0,
    val quietStreak: Int = 0,
    val busiestLabel: String? = null,
    val busiestTotal: Double = 0.0,
    val pulse: List<PulseCell> = emptyList(),
    val keptHeadline: String? = null,
    val fees: FeeSummary = FeeSummary(),
    val regulars: List<RegularPayee> = emptyList(),
    val habit: SpendingHabit? = null,
    /** Every row in the period, reversals included, for PDF statement export. */
    val periodTransactions: List<MpesaTransaction> = emptyList(),
    /** "12 Oct – 10 Nov 2026 · 30 days", counting only days the ledger covers. */
    val heroLabel: String = "",
    val badges: List<InsightBadge> = emptyList(),
    /** Month or year bars for stretches longer than a month. */
    val periodBars: List<PeriodBar> = emptyList()
)

/** Choices the user made about payees and badges, kept in secure prefs. */
data class InsightMarks(
    val essential: Set<String> = emptySet(),
    val watched: Set<String> = emptySet(),
    val earnedBadges: Set<String> = emptySet()
)

/** The challenge Insights shows: active, or ended in the last few days. */
data class InsightChallenge(
    val title: String,
    val kind: ChallengeCardKind,
    val day: Int,
    val totalDays: Int,
    val kept: Double,
    val startDate: LocalDate
)

data class TransactionListUiState(
    val grouped: List<DayGroup> = emptyList(),
    val isScanning: Boolean = false,
    val scanMessage: String? = null,
    val hasSmsPermission: Boolean = false,
    val latestBalance: Double? = null,
    val fulizaOutstanding: Double? = null,
    /** Due date from the latest Fuliza draw text. */
    val fulizaDue: LocalDate? = null,
    val account: WalletAccount = WalletAccount.MPESA,
    val analytics: AnalyticsSummary = AnalyticsSummary(),
    val searchQuery: String = "",
    val typeFilter: TransactionType? = null,
    val availableTypes: List<TransactionType> = emptyList(),
    val sourceFilter: MoneySource? = null,
    val availableSources: List<MoneySource> = emptyList(),
    val showSendPay: Boolean = true,
    val showFuliza: Boolean = true
) {
    fun shownBalance(): Double? = account.displayedBalance(latestBalance, fulizaOutstanding)

    fun canFundPayment(): Boolean =
        showSendPay && account.canFundPayment(latestBalance, fulizaOutstanding)

    fun unfundedPaymentMessage(): String =
        account.unfundedPaymentMessage(latestBalance, fulizaOutstanding)
}

data class DayGroup(
    val label: String,
    val date: LocalDate,
    val items: List<MpesaTransaction>
)

class TransactionListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LipaBillApp
    private val repo = app.repository

    fun observeTransaction(id: Long): kotlinx.coroutines.flow.Flow<MpesaTransaction?> =
        repo.observeById(id)

    suspend fun resolveReceiptId(id: Long?, code: String?): Long? =
        repo.resolveReceiptId(id, code)

    private val isScanning = MutableStateFlow(false)
    private val scanMessage = MutableStateFlow<String?>(null)
    private val hasSmsPermission = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val typeFilter = MutableStateFlow<TransactionType?>(null)
    private val sourceFilter = MutableStateFlow<MoneySource?>(null)
    private val queryTypeSource = combine(searchQuery, typeFilter, sourceFilter) { query, type, source ->
        Triple(query, type, source)
    }
    private val walletAccount = MutableStateFlow(WalletAccount.MPESA)
    private val analyticsRange = MutableStateFlow(AnalyticsRange.DAYS_30)
    private val analyticsYear = MutableStateFlow<Int?>(null)
    private val customStart = MutableStateFlow<LocalDate?>(null)
    private val customEnd = MutableStateFlow<LocalDate?>(null)

    private val mpesaLedger = repo.observeWallet(fuliza = false)
    private val fulizaLedger = repo.observeWallet(fuliza = true)

    private val scan = combine(isScanning, scanMessage) { a, b -> a to b }
    private val rangeState = combine(
        analyticsRange,
        analyticsYear,
        customStart,
        customEnd
    ) { range, year, start, end ->
        AnalyticsRangeState(range, year, start, end)
    }

    private val insightMarks = combine(
        app.essentialPayees,
        app.watchedPayees,
        app.earnedBadges
    ) { essential, watched, badges -> InsightMarks(essential, watched, badges) }

    private val regionAndSources = combine(app.appRegion, app.enabledSources) { region, enabled ->
        region to enabled.ifEmpty { region.sources }
    }

    private val listCore: StateFlow<TransactionListUiState> = combine(
        combine(
            combine(
                mpesaLedger,
                fulizaLedger,
                walletAccount,
                queryTypeSource,
                regionAndSources
            ) { mpesa, fuliza, account, queryType, regionSources ->
                val (query, requestedType, requestedSource) = queryType
                val (region, sources) = regionSources
                val regional = mpesa.filter { it.source in sources }
                val regionalFuliza = if (region.hasFuliza) fuliza else emptyList()
                val selected =
                    if (account == WalletAccount.FULIZA && region.hasFuliza) {
                        WalletAccount.FULIZA
                    } else {
                        WalletAccount.MPESA
                    }
                val ledger = if (selected == WalletAccount.FULIZA) regionalFuliza else regional
                val availableSources = availableMoneySources(
                    txs = ledger,
                    primary = region.primaryWallet,
                    catalog = sources
                )
                val activeSource = requestedSource?.takeIf { it in availableSources }
                val sourced = if (activeSource == null) {
                    ledger
                } else {
                    ledger.filter { it.source == activeSource }
                }
                val balanceSource = activeSource ?: region.primaryWallet
                val available = sourced.map { it.type }.inCategoryOrder()
                val activeType = requestedType?.takeIf { it in available }
                WalletSlice(
                    visible = sourced.filter { tx ->
                        tx.matchesLedgerQuery(query) &&
                            (activeType == null || tx.type == activeType)
                    },
                    analytics = sourced,
                    mpesaBalance = regional.firstOrNull {
                        it.source == balanceSource && it.balance != null
                    }?.balance,
                    fulizaStatus = latestFulizaStatus(regionalFuliza),
                    account = selected,
                    typeFilter = activeType,
                    availableTypes = available,
                    sourceFilter = activeSource,
                    availableSources = availableSources,
                    showSendPay = region.canSendPay,
                    showFuliza = region.hasFuliza
                )
            },
            hasSmsPermission,
            scan,
            rangeState,
            insightMarks
        ) { wallet, smsOk, s, range, marks ->
            val (scanning, message) = s
            TransactionListUiState(
                grouped = groupByDay(wallet.visible),
                isScanning = scanning,
                scanMessage = message,
                hasSmsPermission = smsOk,
                latestBalance = wallet.mpesaBalance,
                fulizaOutstanding = wallet.fulizaStatus?.outstanding,
                fulizaDue = wallet.fulizaStatus?.dueDate,
                account = wallet.account,
                analytics = buildAnalyticsSummary(wallet.analytics, range, wallet.account, marks),
                typeFilter = wallet.typeFilter,
                availableTypes = wallet.availableTypes,
                sourceFilter = wallet.sourceFilter,
                availableSources = wallet.availableSources,
                showSendPay = wallet.showSendPay,
                showFuliza = wallet.showFuliza
            )
        },
        searchQuery
    ) { core, query ->
        core.copy(searchQuery = query)
    }
        .flowOn(Dispatchers.Default)
        // Eager so enabling a rail in Settings updates home chips before you return.
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            TransactionListUiState()
        )

    val uiState: StateFlow<TransactionListUiState> = listCore

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setTypeFilter(type: TransactionType?) {
        typeFilter.value = type
    }

    fun setSourceFilter(source: MoneySource?) {
        sourceFilter.value = source
    }

    fun setWalletAccount(account: WalletAccount) {
        walletAccount.value = account
        typeFilter.value = null
        sourceFilter.value = null
    }

    /** Insights “All” chip: ledger picture with no source filter. */
    fun selectAllLedgerRails() {
        if (walletAccount.value != WalletAccount.MPESA) {
            setWalletAccount(WalletAccount.MPESA)
        } else {
            sourceFilter.value = null
        }
    }

    /** Insights source chip: stay on the ledger and filter to [source]. */
    fun selectLedgerSource(source: MoneySource) {
        if (walletAccount.value != WalletAccount.MPESA) {
            setWalletAccount(WalletAccount.MPESA)
        }
        sourceFilter.value = source
    }

    fun selectFulizaLedger() {
        setWalletAccount(WalletAccount.FULIZA)
    }

    fun setAnalyticsRange(range: AnalyticsRange) {
        analyticsRange.value = range
        if (range != AnalyticsRange.YEAR) {
            analyticsYear.value = null
        }
    }

    fun setAnalyticsYear(year: Int) {
        analyticsRange.value = AnalyticsRange.YEAR
        analyticsYear.value = year
    }

    fun setCustomDateRange(start: LocalDate, end: LocalDate) {
        val (from, to) = if (start.isAfter(end)) end to start else start to end
        customStart.value = from
        customEnd.value = to
        analyticsRange.value = AnalyticsRange.CUSTOM
    }

    fun setSmsPermission(granted: Boolean) {
        hasSmsPermission.value = granted
        if (granted) {
            (getApplication() as LipaBillApp).smsInboxSyncWatcher.ensureStarted()
            viewModelScope.launch {
                repo.backfillIfNeeded()
            }
        }
    }

    fun rescanInbox() {
        viewModelScope.launch {
            isScanning.value = true
            scanMessage.value = null
            try {
                if (!hasSmsPermission.value) {
                    AppMetrics.setupBlocked(SetupBlocker.NoSms)
                    AppMetrics.ledgerRefreshFailed()
                    scanMessage.value = "SMS permission required to scan inbox"
                    return@launch
                }
                val inserted = repo.rescanInbox()
                AppMetrics.ledgerRefresh(inserted, includeUnchanged = true)
                scanMessage.value = if (inserted == 0) {
                    "Inbox scanned — no new confirmation messages"
                } else {
                    "Added $inserted new transaction${if (inserted == 1) "" else "s"}"
                }
            } catch (error: SecurityException) {
                AppMetrics.ledgerRefreshFailed(error)
                scanMessage.value = "SMS permission required to scan inbox"
            } catch (e: Exception) {
                AppMetrics.ledgerRefreshFailed(e)
                scanMessage.value = "Scan failed: ${e.message ?: "unknown error"}"
            } finally {
                isScanning.value = false
            }
        }
    }

    fun clearScanMessage() {
        scanMessage.value = null
    }

    fun setPayeeEssential(key: String, essential: Boolean) {
        app.setPayeeEssential(key, essential)
    }

    fun setPayeeWatched(key: String, watched: Boolean) {
        app.setPayeeWatched(key, watched)
    }

    fun recordBadges(ids: Set<String>) {
        app.recordBadges(ids)
    }

    val challenge: StateFlow<InsightChallenge?> = combine(
        mpesaLedger,
        app.engagement.observeLatest()
    ) { ledger, latest ->
        insightChallenge(ledger, latest, System.currentTimeMillis())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Re-checks challenge status against new texts; Insights calls this when it opens. */
    fun refreshChallenge() {
        viewModelScope.launch {
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
        }
    }

    fun startSkipChallenge(payeeName: String) {
        viewModelScope.launch {
            app.engagement.startSkipPayeeChallenge(payeeName) ?: return@launch
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
            else app.engagementScheduler.scheduleChallengeSweep()
        }
    }

    private fun insightChallenge(
        ledger: List<MpesaTransaction>,
        latest: ChallengeEntity?,
        now: Long
    ): InsightChallenge? {
        if (latest == null) return null
        val status = ChallengeStatus.fromStored(latest.status)
        if (status == ChallengeStatus.CANCELLED) return null
        val template = ChallengeTemplates.byId(latest.templateId) ?: return null
        val rule = CategoryRules.byId(template.categoryId) ?: return null
        val kind = challengeCardKind(status)
        if (kind != ChallengeCardKind.ACTIVE && now - latest.endAt > RECENT_CHALLENGE_MILLIS) return null
        val zone = ChallengeClock.zoneOf(latest.zoneId)
        val total = template.durationDays
        val day = if (kind == ChallengeCardKind.ACTIVE) {
            ChallengeClock.dayNumber(latest.startAt, latest.endAt, now, zone)
        } else {
            total
        }
        val baseline = averageDailySpend(ledger, beforeMillis = latest.startAt) { tx ->
            CategoryMatcher.matches(rule, tx.counterpartyName)
        }
        val cleanDays = when (kind) {
            ChallengeCardKind.ACTIVE -> day - 1
            ChallengeCardKind.COMPLETED -> total
            else -> 0
        }
        val payee = ChallengeTemplates.payeeOf(latest.templateId)
        return InsightChallenge(
            title = payee?.let { "Skip $it this week" } ?: "No food delivery for $total days",
            kind = kind,
            day = day,
            totalDays = total,
            kept = baseline * cleanDays.coerceAtLeast(0),
            startDate = ChallengeClock.localDate(latest.startAt, zone)
        )
    }

    private fun MpesaTransaction.matchesLedgerQuery(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return counterpartyName?.contains(q, ignoreCase = true) == true ||
            counterpartyPhone?.contains(q, ignoreCase = true) == true ||
            code.contains(q, ignoreCase = true) ||
            amount?.toString()?.contains(q, ignoreCase = true) == true
    }

    private fun groupByDay(txs: List<MpesaTransaction>): List<DayGroup> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val yesterday = today.minusDays(1)
        val formatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

        return txs
            .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, items) ->
                val label = when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> date.format(formatter)
                }
                DayGroup(label = label, date = date, items = items)
            }
    }

    private companion object {
        const val RECENT_CHALLENGE_MILLIS = 3L * 24L * 60L * 60L * 1000L
    }

    private data class WalletSlice(
        val visible: List<MpesaTransaction>,
        val analytics: List<MpesaTransaction>,
        val mpesaBalance: Double?,
        val fulizaStatus: FulizaStatus?,
        val account: WalletAccount,
        val typeFilter: TransactionType? = null,
        val availableTypes: List<TransactionType> = emptyList(),
        val sourceFilter: MoneySource? = null,
        val availableSources: List<MoneySource> = emptyList(),
        val showSendPay: Boolean = true,
        val showFuliza: Boolean = true
    )

}

/**
 * Region wallets first (primary, then activity, then label), plus any extra
 * rails that already landed in the ledger. Empty history still lists the catalog
 * so wallets and banks can be switched before those SMS are imported.
 */
internal fun availableMoneySources(
    txs: List<MpesaTransaction>,
    primary: MoneySource,
    catalog: Collection<MoneySource>
): List<MoneySource> {
    val counts = txs.groupingBy { it.source }.eachCount()
    return (catalog + counts.keys).distinct().sortedWith(
        compareByDescending<MoneySource> { it == primary }
            .thenByDescending { counts[it] ?: 0 }
            .thenBy { it.label }
    )
}
