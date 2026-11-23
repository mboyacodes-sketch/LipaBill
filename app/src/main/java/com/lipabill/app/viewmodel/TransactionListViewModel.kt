package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.SetupBlocker
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.repository.LinkedPayeeRenameResult
import com.lipabill.app.data.repository.EngagementRepository
import com.lipabill.app.engage.ChallengeTemplates
import com.lipabill.app.engage.SpendCategory
import com.lipabill.app.engage.resolveSpendCategory
import com.lipabill.app.ui.util.formatActivityTime
import com.lipabill.app.ui.util.inCategoryOrder
import com.lipabill.app.ussd.PaymentWallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    /** Calendar month in progress: 1st → today. Default Insights filter. */
    THIS_MONTH,
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
    /** Top people (Send Money / person-like), separate from [topBusinesses]. */
    val topPeople: List<CounterpartySpend> = emptyList(),
    /** Top shops, bills and companies in the period. */
    val topBusinesses: List<CounterpartySpend> = emptyList(),
    val peopleSpendTotal: Double = 0.0,
    val businessSpendTotal: Double = 0.0,
    val txnCount: Int = 0,
    val range: AnalyticsRange = AnalyticsRange.THIS_MONTH,
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
    val storyNamesPayee: Boolean = false,
    val quietDays: Int = 0,
    val quietStreak: Int = 0,
    val busiestLabel: String? = null,
    val busiestTotal: Double = 0.0,
    /** Current-month pulse grid; the UI pages other months from the flow maps. */
    val pulse: List<PulseCell> = emptyList(),
    /** Whole-ledger in/out by day so past months on the calendar prefill correctly. */
    val inflowByDate: Map<LocalDate, Double> = emptyMap(),
    val outflowByDate: Map<LocalDate, Double> = emptyMap(),
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
    val periodBars: List<PeriodBar> = emptyList(),
    /**
     * False on the [analyticsSummary] placeholder until the first aggregate finishes.
     * Insights uses this for [com.lipabill.app.ui.components.InsightsSkeleton].
     */
    val isReady: Boolean = false,
    /** Spend categories that moved most against the previous period (empty when not comparable). */
    val categoryChanges: List<CategoryChange> = emptyList(),
    /** Regular bills that rose, are new or stopped (whole history, not just this period). */
    val regularChanges: List<RegularChange> = emptyList(),
    /** Average visit at usual merchants, now against before. */
    val priceTrends: List<PriceTrend> = emptyList(),
    /** Pending “Potential recurring payment” rows (confirm / dismiss). */
    val potentialRecurring: List<com.lipabill.app.engage.RecurringPattern> = emptyList()
)

/** Choices the user made about payees and badges, kept in secure prefs. */
data class InsightMarks(
    val essential: Set<String> = emptySet(),
    val watched: Set<String> = emptySet(),
    val earnedBadges: Set<String> = emptySet(),
    val recurringChoices: Map<String, com.lipabill.app.engage.RecurringChoice> = emptyMap()
)

/** The challenge Insights shows: active, or ended in the last few days. */
data class InsightChallenge(
    val title: String,
    val kind: ChallengeCardKind,
    val day: Int,
    val totalDays: Int,
    /** Usual spend avoided so far. Null when the challenge is not about spending. */
    val kept: Double?,
    val startDate: LocalDate,
    /** Row that broke a skip challenge — opens the receipt from Insights. */
    val breakingTransactionId: Long? = null,
    /** Room row id — Done dismisses the completed card (same prefs as Habits). */
    val challengeId: Long = 0L
)

/** Outcome of editing a payee name and/or spend category on a receipt. */
data class PayeeEditResult(
    val linkedCount: Int,
    val renamedCount: Int
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
    /** Wallets the balance pill can switch between (one per network with a SIM line). */
    val balanceWallets: List<MoneySource> = emptyList(),
    /** Cards on the home balance stack, front card = [balanceWallet]. */
    val walletCards: List<WalletCardUi> = emptyList(),
    /** Source whose balance the pill shows. */
    val balanceWallet: MoneySource? = null,
    /** Source Send & Pay will dial (preferred SIM), when known. */
    val dialWallet: MoneySource? = null,
    /** Newest balance per source. */
    val walletBalances: Map<MoneySource, Double> = emptyMap(),
    val analytics: AnalyticsSummary = AnalyticsSummary(),
    val searchQuery: String = "",
    val typeFilter: TransactionType? = null,
    val availableTypes: List<TransactionType> = emptyList(),
    val sourceFilter: MoneySource? = null,
    val availableSources: List<MoneySource> = emptyList(),
    val showSendPay: Boolean = true,
    val showFuliza: Boolean = true,
    /** payeeKey → SpendCategory.id from secure prefs. */
    val spendCategoryOverrides: Map<String, String> = emptyMap(),
    /** True until the first ledger emission — drives skeleton placeholders. */
    val isLoading: Boolean = true
) {
    fun shownBalance(): Double? = account.displayedBalance(latestBalance, fulizaOutstanding)

    /** Any wallet balance funds Send/Pay: the pill may be on a second SIM with no balance yet. */
    private val anyWalletBalance: Double?
        get() = latestBalance ?: walletBalances.entries.firstOrNull { it.key.isWallet }?.value

    fun canFundPayment(): Boolean =
        showSendPay && account.canFundPayment(anyWalletBalance, fulizaOutstanding)

    fun unfundedPaymentMessage(): String =
        account.unfundedPaymentMessage(anyWalletBalance, fulizaOutstanding)
}

/** One Home feed row with display fields computed off the composition path. */
data class FeedRow(
    val tx: MpesaTransaction,
    val spendCategory: SpendCategory? = null,
    val timeLabel: String = ""
) {
    val id: Long get() = tx.id
}

data class DayGroup(
    val label: String,
    val date: LocalDate,
    val items: List<FeedRow>
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
    /**
     * False until the first SMS backfill attempt finishes (or SMS is denied).
     * Drives [TransactionListUiState.isLoading] / HomeSkeleton — not "Room emitted once".
     */
    private val ledgerHydrated = MutableStateFlow(false)
    private var hydrateJob: Job? = null
    private val searchQuery = MutableStateFlow("")
    private val typeFilter = MutableStateFlow<TransactionType?>(null)
    private val sourceFilter = MutableStateFlow<MoneySource?>(null)
    /** Insights rails: empty = All, else exactly one source. Separate from Home's history chip. */
    private val insightsRailsFlow = MutableStateFlow<Set<MoneySource>>(emptySet())
    val insightsRails: StateFlow<Set<MoneySource>> = insightsRailsFlow.asStateFlow()
    /** Balance-pill MNO pick — does not move the History chips. */
    private val pillWallet = MutableStateFlow<MoneySource?>(null)
    private val walletAccount = MutableStateFlow(WalletAccount.MPESA)
    private val analyticsRange = MutableStateFlow(AnalyticsRange.THIS_MONTH)
    private val analyticsYear = MutableStateFlow<Int?>(null)
    private val customStart = MutableStateFlow<LocalDate?>(null)
    private val customEnd = MutableStateFlow<LocalDate?>(null)

    /** Full ledgers for Insights / challenge — only while those screens subscribe. */
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
        app.earnedBadges,
        app.recurringPatternChoices
    ) { essential, watched, badges, recurring ->
        InsightMarks(essential, watched, badges, recurring)
    }

    private val rails = combine(
        app.appRegion,
        app.enabledSources,
        app.simLines,
        app.activeDialWallet
    ) { region, enabled, lines, active ->
        val sources = enabled.ifEmpty { region.sources }
        Rails(region, sources, balanceWalletChoices(region, sources, lines), active?.ledgerSource, lines)
    }

    /** Newest balance per source, read outside the Home row cap. */
    val walletBalances: StateFlow<Map<MoneySource, Double>> = repo.observeLatestBalances()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val railsAndBalances = combine(rails, walletBalances, repo.observeSourceCounts()) { r, balances, counts ->
        RailState(r, balances, counts)
    }

    @OptIn(FlowPreview::class)
    private val debouncedSearch: StateFlow<String> = searchQuery
        .debounce(SEARCH_DEBOUNCE_MS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Home rows load a page at a time; scrolling near the end asks for the next page. */
    private val homePage = MutableStateFlow(HOME_PAGE_SIZE)

    /**
     * Home window, read from the database for the picked sources only (or the one source chip
     * in focus), so every row for that wallet or bank can be reached. Search reads the whole ledger.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val mpesaHome = combine(
        app.appRegion,
        app.enabledSources,
        sourceFilter,
        debouncedSearch,
        homePage
    ) { region, enabled, filter, query, page ->
        val catalog = enabled.ifEmpty { region.sources }
        HomeWindow(
            sources = if (filter != null) setOf(filter) else catalog,
            limit = if (query.isBlank()) page else SEARCH_WINDOW
        )
    }
        .distinctUntilChanged()
        .flatMapLatest { repo.observeLedgerWindow(it.sources, it.limit) }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val fulizaHome = homePage
        .flatMapLatest { repo.observeWalletList(fuliza = true, limit = it) }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    /** True while the Home list may have older rows to load. */
    val canLoadMoreHistory: StateFlow<Boolean> = combine(
        mpesaHome,
        fulizaHome,
        walletAccount,
        homePage,
        debouncedSearch
    ) { mpesa, fuliza, account, page, query ->
        query.isBlank() && (if (account == WalletAccount.FULIZA) fuliza.size else mpesa.size) >= page
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Next page of Home history (called as the list nears its end). */
    fun loadMoreHistory() {
        if (canLoadMoreHistory.value) homePage.value += HOME_PAGE_SIZE
    }

    private fun walletBaseOf(
        mpesa: List<MpesaTransaction>,
        fuliza: List<MpesaTransaction>,
        account: WalletAccount,
        requestedSources: Set<MoneySource>,
        railsAndBalances: RailState,
        pillPick: MoneySource?
    ): WalletBase {
        val (rails, balances, counts) = railsAndBalances
        val region = rails.region
        val sources = rails.sources
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
            catalog = sources,
            // Whole-ledger counts: chip order stays put when the Home window narrows to one source.
            counts = counts.takeIf { it.isNotEmpty() && selected == WalletAccount.MPESA }
        )
        // Empty set = all rails; otherwise filter to the picked source(s).
        val activeSources = requestedSources.filterTo(LinkedHashSet()) { it in availableSources }
        val activeSource = activeSources.singleOrNull()
        val sourced = if (activeSources.isEmpty()) {
            ledger
        } else {
            ledger.filter { it.source in activeSources }
        }
        val balanceSource = resolveBalanceSource(
            active = rails.activeWallet,
            choices = rails.balanceWallets,
            pillPick = pillPick,
            primary = region.primaryWallet,
            enabled = sources
        )
        val dialWalletOn = PaymentWallet.forRegion(region)
            .any { it.ledgerSource in sources }
        return WalletBase(
            sourced = sourced,
            mpesaBalance = balanceSource?.let { balances[it] },
            balanceWallet = balanceSource,
            dialWallet = rails.activeWallet,
            balanceWallets = rails.balanceWallets,
            walletCards = walletCards(region, rails.balanceWallets, balanceSource, rails.lines, balances),
            walletBalances = balances,
            fulizaStatus = latestFulizaStatus(regionalFuliza),
            account = selected,
            sourceFilter = activeSource,
            sourceFilters = activeSources,
            availableSources = availableSources,
            showSendPay = region.canSendPay && dialWalletOn,
            showFuliza = region.hasFuliza && MoneySource.MPESA in sources
        )
    }

    /**
     * Home ledger + balance from the capped window.
     * Search/type stay off Insights so typing does not rebuild analytics.
     */
    private val homeWalletBase: StateFlow<WalletBase> = combine(
        mpesaHome,
        fulizaHome,
        walletAccount,
        sourceFilter,
        combine(railsAndBalances, pillWallet) { rails, pill -> rails to pill }
    ) { mpesa, fuliza, account, requestedSource, railsPill ->
        val (railBalances, pill) = railsPill
        walletBaseOf(mpesa, fuliza, account, setOfNotNull(requestedSource), railBalances, pill)
    }
        .flowOn(Dispatchers.Default)
        // Eager so enabling a rail in Settings updates home chips before you return.
        .stateIn(viewModelScope, SharingStarted.Eagerly, WalletBase())

    /** Full ledger for Insights — only while Insights (or challenge) is subscribed. */
    private val insightsWalletBase: StateFlow<WalletBase> = combine(
        mpesaLedger,
        fulizaLedger,
        walletAccount,
        insightsRailsFlow,
        combine(railsAndBalances, pillWallet) { rails, pill -> rails to pill }
    ) { mpesa, fuliza, account, picked, railsPill ->
        val (railBalances, pill) = railsPill
        walletBaseOf(mpesa, fuliza, account, picked, railBalances, pill)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletBase())

    /** Home list grouping — ignores analytics range / insight marks. */
    private val listBody: StateFlow<TransactionListUiState> = combine(
        homeWalletBase,
        combine(searchQuery, debouncedSearch) { query, debounced -> query to debounced },
        typeFilter,
        hasSmsPermission,
        combine(scan, ledgerHydrated) { s, hydrated -> s to hydrated }
    ) { wallet, queries, requestedType, smsOk, scanHydrated ->
        val (query, debouncedQuery) = queries
        val available = wallet.sourced.map { it.type }.inCategoryOrder()
        val activeType = requestedType?.takeIf { it in available }
        val visible = wallet.sourced.filter { tx ->
            tx.matchesLedgerQuery(debouncedQuery) &&
                (activeType == null || tx.type == activeType)
        }
        val (s, hydrated) = scanHydrated
        val (scanning, message) = s
        TransactionListUiState(
            grouped = groupByDay(visible),
            isScanning = scanning,
            scanMessage = message,
            hasSmsPermission = smsOk,
            latestBalance = wallet.mpesaBalance,
            balanceWallets = wallet.balanceWallets,
            walletCards = wallet.walletCards,
            balanceWallet = wallet.balanceWallet,
            dialWallet = wallet.dialWallet,
            walletBalances = wallet.walletBalances,
            fulizaOutstanding = wallet.fulizaStatus?.outstanding,
            fulizaDue = wallet.fulizaStatus?.dueDate,
            account = wallet.account,
            searchQuery = query,
            typeFilter = activeType,
            availableTypes = available,
            sourceFilter = wallet.sourceFilter,
            availableSources = wallet.availableSources,
            showSendPay = wallet.showSendPay,
            showFuliza = wallet.showFuliza,
            isLoading = !hydrated
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TransactionListUiState())

    /**
     * Insights summary — full ledger, only while Insights is open.
     * Not folded into [uiState] so Home never pins this work.
     */
    val analyticsSummary: StateFlow<AnalyticsSummary> = combine(
        insightsWalletBase,
        rangeState,
        insightMarks,
        app.spendCategoryOverrides
    ) { wallet, range, marks, overrides ->
        buildAnalyticsSummary(wallet.sourced, range, wallet.account, marks, overrides)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsSummary())

    /** Home feed state — list + category overrides applied to row display fields. */
    val uiState: StateFlow<TransactionListUiState> = combine(
        listBody,
        app.spendCategoryOverrides
    ) { list, overrides ->
        list.copy(
            spendCategoryOverrides = overrides,
            grouped = applySpendOverrides(list.grouped, overrides)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TransactionListUiState())

    /** Balance-only for money sheets — avoids recomposing on list/search churn. */
    val latestBalance: StateFlow<Double?> = listBody
        .map { it.latestBalance }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setTypeFilter(type: TransactionType?) {
        typeFilter.value = type
    }

    fun setSourceFilter(source: MoneySource?) {
        homePage.value = HOME_PAGE_SIZE
        sourceFilter.value = source
    }

    fun setWalletAccount(account: WalletAccount) {
        homePage.value = HOME_PAGE_SIZE
        walletAccount.value = account
        typeFilter.value = null
        sourceFilter.value = null
        // Fuliza lives on M-Pesa — pin the pill/dial back to Safaricom when opening it.
        if (account == WalletAccount.FULIZA) {
            pillWallet.value = MoneySource.MPESA
            app.setActiveWallet(MoneySource.MPESA)
        }
    }

    /**
     * Balance pill wallet chip: shows that rail’s balance only. History chips stay put.
     * Dial follows when a matching SIM is in the phone.
     */
    fun setBalanceWallet(source: MoneySource) {
        walletAccount.value = WalletAccount.MPESA
        typeFilter.value = null
        pillWallet.value = source
        if (!app.setActiveWallet(source)) {
            scanMessage.value =
                "Showing ${source.label} balance — insert or allow that SIM to dial Send & Pay."
        }
    }

    /** Insights “All” chip: ledger picture with no source filter. */
    fun selectAllLedgerRails() {
        if (walletAccount.value != WalletAccount.MPESA) {
            setWalletAccount(WalletAccount.MPESA)
        }
        insightsRailsFlow.value = emptySet()
    }

    /**
     * Insights rail chip: select only [source] (one at a time). Use [selectAllLedgerRails] for All.
     */
    fun selectLedgerSource(source: MoneySource) {
        if (walletAccount.value != WalletAccount.MPESA) {
            setWalletAccount(WalletAccount.MPESA)
        }
        insightsRailsFlow.value = setOf(source)
    }

    fun selectFulizaLedger() {
        setWalletAccount(WalletAccount.FULIZA)
    }

    fun setAnalyticsRange(range: AnalyticsRange) {
        analyticsRange.value = range
        if (range != AnalyticsRange.YEAR) {
            analyticsYear.value = null
        }
        if (range != AnalyticsRange.CUSTOM) {
            customStart.value = null
            customEnd.value = null
        }
    }

    fun setAnalyticsYear(year: Int) {
        analyticsRange.value = AnalyticsRange.YEAR
        analyticsYear.value = year
        customStart.value = null
        customEnd.value = null
    }

    fun setCustomDateRange(start: LocalDate, end: LocalDate) {
        val (from, to) = if (start.isAfter(end)) end to start else start to end
        customStart.value = from
        customEnd.value = to
        analyticsRange.value = AnalyticsRange.CUSTOM
        analyticsYear.value = null
    }

    /**
     * Calendar swipe / Today: bind Insights (and the habit card) to that calendar month.
     * Current month stays [THIS_MONTH] (1st → today); past months use the full month.
     */
    fun setAnalyticsMonth(month: YearMonth) {
        val today = LocalDate.now(ZoneId.systemDefault())
        val todayMonth = YearMonth.from(today)
        if (month.isAfter(todayMonth)) return
        if (month == todayMonth) {
            setAnalyticsRange(AnalyticsRange.THIS_MONTH)
            return
        }
        setCustomDateRange(month.atDay(1), month.atEndOfMonth())
    }

    /** Leaving Insights: clear period / type filters and return to the current month. */
    fun resetInsightsFilters() {
        insightsRailsFlow.value = emptySet()
        analyticsRange.value = AnalyticsRange.THIS_MONTH
        analyticsYear.value = null
        customStart.value = null
        customEnd.value = null
        typeFilter.value = null
    }

    fun setSmsPermission(granted: Boolean) {
        hasSmsPermission.value = granted
        if (!granted) {
            // No inbox to import — show empty home, not an endless skeleton.
            ledgerHydrated.value = true
            return
        }
        (getApplication() as LipaBillApp).smsInboxSyncWatcher.ensureStarted()
        if (hydrateJob?.isActive == true) return
        if (ledgerHydrated.value) {
            viewModelScope.launch { repo.backfillIfNeeded() }
            return
        }
        hydrateJob = viewModelScope.launch {
            try {
                repo.backfillIfNeeded()
            } finally {
                ledgerHydrated.value = true
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
                // Full history: newly enabled rails sit behind the high-water mark.
                val inserted = repo.rescanInbox(forceFullHistory = true)
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

    /**
     * Safe to spend, budget pace and goals, for Home and Insights. Reads a capped window of
     * the ledger without SMS bodies (months of history, enough for bills and payday).
     */
    /** Capped, body-free ledger the plan and Wrapped share (one DB subscription). */
    private val planLedger = repo.observeWalletList(fuliza = false, limit = PLAN_WINDOW)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Last month told as story cards; null when there is no history for it. */
    /** The plan's ledger narrowed to the Insights rails (all when none are picked). */
    private val planLedgerForRails = combine(planLedger, insightsRailsFlow) { ledger, rails ->
        if (rails.isEmpty()) ledger else ledger.filter { it.source in rails }
    }

    val wrapped: StateFlow<MonthlyWrapped?> = combine(planLedgerForRails, app.spendCategoryOverrides) { ledger, overrides ->
        buildWrapped(wrappedMonth(java.time.LocalDate.now()), ledger, overrides)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val moneyPlan: StateFlow<MoneyPlanUi> = combine(
        planLedgerForRails,
        combine(walletBalances, rails, insightsRailsFlow) { balances, r, picked ->
            // Safe to spend counts only the picked wallets when rails are picked; banks alone
            // have no wallet balance to spend from, so it steps aside.
            val wallets = if (picked.isEmpty()) r.balanceWallets else r.balanceWallets.filter { it in picked }
            val banksOnly = picked.isNotEmpty() && picked.none { it.isWallet }
            Triple(balances, r.copy(balanceWallets = wallets), banksOnly)
        },
        combine(app.budgets, app.goals) { budgets, goals -> budgets to goals },
        app.spendCategoryOverrides,
        insightMarks
    ) { ledger, balancesRails, budgetsGoals, overrides, marks ->
        val (balances, r, banksOnly) = balancesRails
        val (budgets, goals) = budgetsGoals
        val plan = buildMoneyPlan(ledger, balances, r.balanceWallets, r.region.primaryWallet, budgets, goals, overrides, marks)
        if (banksOnly) plan.copy(safe = null) else plan
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyPlanUi())

    fun setBudget(category: SpendCategory, monthlyLimit: Double?) = app.setBudget(category, monthlyLimit)

    fun saveGoal(goal: SavingsGoal) = app.saveGoal(goal)

    fun deleteGoal(id: String) = app.deleteGoal(id)

    fun setPayeeEssential(key: String, essential: Boolean) {
        app.setPayeeEssential(key, essential)
    }

    fun setPayeeWatched(key: String, watched: Boolean) {
        app.setPayeeWatched(key, watched)
    }

    fun setRecurringChoice(key: String, confirmed: Boolean) {
        app.setRecurringChoice(
            key,
            if (confirmed) {
                com.lipabill.app.engage.RecurringChoice.Confirmed
            } else {
                com.lipabill.app.engage.RecurringChoice.Dismissed
            }
        )
    }

    fun spendCategoryFor(tx: MpesaTransaction): SpendCategory =
        resolveSpendCategory(tx, payeeKey(tx), app.spendCategoryOverrides.value)

    suspend fun countLinkedPayees(tx: MpesaTransaction): Int = repo.countLinkedPayees(tx)

    /**
     * Apply name and/or category to every payment sharing [tx]'s payee key.
     */
    suspend fun editPayee(
        tx: MpesaTransaction,
        newName: String?,
        category: SpendCategory?
    ): PayeeEditResult {
        val previousKey = payeeKey(tx)
        val rename: LinkedPayeeRenameResult = repo.renameLinkedPayee(tx, newName)
        val finalKey = rename.payeeKey ?: previousKey
        when {
            category != null && finalKey != null -> {
                if (previousKey != null && previousKey != finalKey) {
                    app.setSpendCategoryOverride(previousKey, null)
                }
                app.setSpendCategoryOverride(finalKey, category)
            }
            previousKey != null && finalKey != null && previousKey != finalKey -> {
                app.moveSpendCategoryOverride(previousKey, finalKey)
            }
        }
        return PayeeEditResult(
            linkedCount = rename.linkedCount,
            renamedCount = rename.renamedCount
        )
    }

    fun recordBadges(ids: Set<String>) {
        app.recordBadges(ids)
    }

    private val acknowledgedCompletedId =
        MutableStateFlow(app.securePreferences.acknowledgedCompletedChallengeId)

    val challenge: StateFlow<InsightChallenge?> = combine(
        mpesaLedger,
        app.engagement.observeLatest(),
        app.engagement.observeActive(),
        acknowledgedCompletedId
    ) { ledger, latest, active, ackId ->
        val now = System.currentTimeMillis()
        val primary = pickPrimaryChallenge(latest, active, ackId, now)
        buildInsightChallenge(ledger, primary, now, ackId)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _challengeMessage = MutableStateFlow<String?>(null)
    val challengeMessage: StateFlow<String?> = _challengeMessage.asStateFlow()

    fun clearChallengeMessage() {
        _challengeMessage.value = null
    }

    /** Re-checks challenge status against new texts; Insights calls this when it opens. */
    fun refreshChallenge() {
        acknowledgedCompletedId.value = app.securePreferences.acknowledgedCompletedChallengeId
        viewModelScope.launch {
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
        }
    }

    /** Hide a completed challenge card (shared with Habits complete acknowledgment). */
    fun acknowledgeCompletedChallenge() {
        val card = challenge.value ?: return
        if (card.kind != ChallengeCardKind.COMPLETED || card.challengeId == 0L) return
        app.securePreferences.acknowledgedCompletedChallengeId = card.challengeId
        acknowledgedCompletedId.value = card.challengeId
    }

    fun startSkipChallenge(payeeName: String) {
        viewModelScope.launch {
            val templateId = ChallengeTemplates.skipPayee(payeeName).id
            val active = app.engagement.listActive()
            if (active.any { it.templateId == templateId }) {
                _challengeMessage.value = "That challenge is already running."
                return@launch
            }
            if (active.size >= EngagementRepository.MAX_ACTIVE) {
                _challengeMessage.value = "You already have 3 challenges running."
                return@launch
            }
            val started = app.engagement.startSkipPayeeChallenge(payeeName)
            if (started == null) {
                _challengeMessage.value = "Couldn't start that challenge. Try again."
                return@launch
            }
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
            else app.engagementScheduler.scheduleChallengeSweep()
        }
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

        return txs
            .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (date, items) ->
                val label = when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> date.format(DAY_GROUP_FORMAT)
                }
                DayGroup(
                    label = label,
                    date = date,
                    items = items.map { tx ->
                        FeedRow(
                            tx = tx,
                            timeLabel = formatActivityTime(tx.timestampMillis)
                        )
                    }
                )
            }
    }

    private fun applySpendOverrides(
        grouped: List<DayGroup>,
        overrides: Map<String, String>
    ): List<DayGroup> {
        if (grouped.isEmpty()) return grouped
        return grouped.map { day ->
            day.copy(
                items = day.items.map { row ->
                    val spend = resolveSpendCategory(row.tx, payeeKey(row.tx), overrides)
                    if (spend == row.spendCategory) row else row.copy(spendCategory = spend)
                }
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        /** Ledger rows the money plan reads: months of history for bills and payday. */
        const val PLAN_WINDOW = 3000
        const val HOME_PAGE_SIZE = 80
        /** Search spans the whole picked ledger. */
        const val SEARCH_WINDOW = 100_000
        val DAY_GROUP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy")
    }

    private data class HomeWindow(val sources: Set<MoneySource>, val limit: Int)

    private data class RailState(
        val rails: Rails,
        val balances: Map<MoneySource, Double>,
        val counts: Map<MoneySource, Int>
    )

    private data class Rails(
        val region: AppRegion,
        val sources: Set<MoneySource>,
        val balanceWallets: List<MoneySource>,
        val activeWallet: MoneySource?,
        val lines: List<com.lipabill.app.ussd.SimLine>
    )

    private data class WalletBase(
        val sourced: List<MpesaTransaction> = emptyList(),
        val mpesaBalance: Double? = null,
        val balanceWallet: MoneySource? = null,
        val dialWallet: MoneySource? = null,
        val balanceWallets: List<MoneySource> = emptyList(),
        val walletCards: List<WalletCardUi> = emptyList(),
        val walletBalances: Map<MoneySource, Double> = emptyMap(),
        val fulizaStatus: FulizaStatus? = null,
        val account: WalletAccount = WalletAccount.MPESA,
        val sourceFilter: MoneySource? = null,
        /** Every rail in the filter (Insights can pick several). */
        val sourceFilters: Set<MoneySource> = emptySet(),
        val availableSources: List<MoneySource> = emptyList(),
        val showSendPay: Boolean = true,
        val showFuliza: Boolean = true
    )

}

/**
 * History chips for the Settings-enabled catalog only (primary first, then
 * activity, then label). Rails turned off in Settings stay off even if old
 * SMS for them are still on the ledger; empty history still lists the catalog.
 */
internal fun availableMoneySources(
    txs: List<MpesaTransaction>,
    primary: MoneySource,
    catalog: Collection<MoneySource>,
    counts: Map<MoneySource, Int>? = null
): List<MoneySource> {
    if (catalog.isEmpty()) return emptyList()
    val activity = counts ?: txs.groupingBy { it.source }.eachCount()
    return catalog.distinct().sortedWith(
        compareByDescending<MoneySource> { it == primary }
            .thenByDescending { activity[it] ?: 0 }
            .thenBy { it.label }
    )
}
