package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.data.repository.TransactionRepository
import com.lipabill.app.ui.util.displayLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
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
    val emptyPeriodCopy: String,
    val emptyHistoryTitle: String,
    val emptyHistoryDetail: String
) {
    MPESA(
        chipLabel = "M-PESA",
        balanceCaption = "Available balance",
        balancePrefix = "Available",
        ledgerTitle = "Transactions",
        metricsTitle = "Metrics & Analytics",
        inflowLabel = "Income",
        outflowLabel = "Expenses",
        emptyPeriodCopy = "No transactions in this period.",
        emptyHistoryTitle = "No activity yet",
        emptyHistoryDetail = "Pull down to refresh and import M-Pesa SMS."
    ),
    FULIZA(
        chipLabel = "Fuliza",
        balanceCaption = "Fuliza outstanding",
        balancePrefix = "Outstanding",
        ledgerTitle = "Fuliza",
        metricsTitle = "Fuliza metrics",
        inflowLabel = "Drawn",
        outflowLabel = "Access fees",
        emptyPeriodCopy = "No Fuliza messages in this period.",
        emptyHistoryTitle = "No Fuliza messages yet",
        emptyHistoryDetail = "Fuliza confirmations from M-PESA show here."
    );

    val cashFlowCaption: String
        get() = "$inflowLabel vs ${outflowLabel.replaceFirstChar { it.lowercase() }} over time"

    /** Figure for the account that is selected. Null when there is no history. */
    fun displayedBalance(mpesaBalance: Double?, fulizaOutstanding: Double?): Double? = when (this) {
        MPESA -> mpesaBalance
        FULIZA -> fulizaOutstanding
    }

    /** Send and Pay need a positive figure on the selected account. Zero and dashes do not qualify. */
    fun canFundPayment(mpesaBalance: Double?, fulizaOutstanding: Double?): Boolean {
        val figure = displayedBalance(mpesaBalance, fulizaOutstanding)
        return figure != null && figure > 0.0
    }

    /** Why Send and Pay are off when this account has no usable figure. */
    fun unfundedPaymentMessage(mpesaBalance: Double?, fulizaOutstanding: Double?): String {
        val name = chipLabel
        val label = if (this == FULIZA) "outstanding amount" else "balance"
        val figure = displayedBalance(mpesaBalance, fulizaOutstanding)
        return if (figure == null) {
            "Send and Pay need a $name $label from your messages. There isn’t one yet."
        } else {
            "Send and Pay are off because your $name $label is Ksh 0.00."
        }
    }
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
    val label: String,
    val count: Int,
    val total: Double
)

data class AnalyticsPoint(
    val label: String,
    val income: Double,
    val expense: Double
)

data class AnalyticsSummary(
    val incomeTotal: Double = 0.0,
    val expenseTotal: Double = 0.0,
    val byType: List<TypeBreakdown> = emptyList(),
    val series: List<AnalyticsPoint> = emptyList(),
    val txnCount: Int = 0,
    val range: AnalyticsRange = AnalyticsRange.DAYS_30,
    val selectedYear: Int? = null,
    val availableYears: List<Int> = emptyList(),
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val rangeLabel: String = "Last 30 days"
)

data class TransactionListUiState(
    val grouped: List<DayGroup> = emptyList(),
    val isScanning: Boolean = false,
    val scanMessage: String? = null,
    val hasSmsPermission: Boolean = false,
    val latestBalance: Double? = null,
    val fulizaOutstanding: Double? = null,
    val account: WalletAccount = WalletAccount.MPESA,
    val analytics: AnalyticsSummary = AnalyticsSummary(),
    val frequentContacts: List<SendContact> = emptyList(),
    val searchQuery: String = ""
) {
    fun shownBalance(): Double? = account.displayedBalance(latestBalance, fulizaOutstanding)

    fun canFundPayment(): Boolean = account.canFundPayment(latestBalance, fulizaOutstanding)

    fun unfundedPaymentMessage(): String =
        account.unfundedPaymentMessage(latestBalance, fulizaOutstanding)
}

data class DayGroup(
    val label: String,
    val date: LocalDate,
    val items: List<MpesaTransaction>
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as LipaBillApp).repository

    fun observeTransaction(id: Long): kotlinx.coroutines.flow.Flow<MpesaTransaction?> =
        repo.observeById(id)

    private val isScanning = MutableStateFlow(false)
    private val scanMessage = MutableStateFlow<String?>(null)
    private val hasSmsPermission = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val walletAccount = MutableStateFlow(WalletAccount.MPESA)
    private val analyticsRange = MutableStateFlow(AnalyticsRange.DAYS_30)
    private val analyticsYear = MutableStateFlow<Int?>(null)
    private val customStart = MutableStateFlow<LocalDate?>(null)
    private val customEnd = MutableStateFlow<LocalDate?>(null)

    private val transactions = searchQuery.flatMapLatest { q ->
        repo.observeTransactions(
            query = q,
            type = null,
            limit = TransactionRepository.LIST_LIMIT
        )
    }

    private val analyticsSource = repo.observeTransactions(
        query = "",
        type = null,
        limit = TransactionRepository.ANALYTICS_LIMIT
    )

    private val scan = combine(isScanning, scanMessage) { a, b -> a to b }
    private val rangeState = combine(
        analyticsRange,
        analyticsYear,
        customStart,
        customEnd
    ) { range, year, start, end ->
        AnalyticsRangeState(range, year, start, end)
    }

    private val frequentContacts = repo.observeFrequentContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val listCore: StateFlow<TransactionListUiState> = combine(
        combine(
            combine(transactions, analyticsSource, walletAccount) { txs, analytics, account ->
                val mpesa = analytics.filter { it.type != TransactionType.FULIZA }
                val fuliza = analytics.filter { it.type == TransactionType.FULIZA }
                WalletSlice(
                    visible = txs.onWallet(account),
                    analytics = if (account == WalletAccount.FULIZA) fuliza else mpesa,
                    mpesaBalance = mpesa.firstOrNull { it.balance != null }?.balance,
                    fulizaOutstanding = fuliza.firstOrNull { it.balance != null }?.balance,
                    account = account
                )
            },
            hasSmsPermission,
            scan,
            rangeState
        ) { wallet, smsOk, s, range ->
            val (scanning, message) = s
            TransactionListUiState(
                grouped = groupByDay(wallet.visible),
                isScanning = scanning,
                scanMessage = message,
                hasSmsPermission = smsOk,
                latestBalance = wallet.mpesaBalance,
                fulizaOutstanding = wallet.fulizaOutstanding,
                account = wallet.account,
                analytics = buildAnalytics(wallet.analytics, range, wallet.account)
            )
        },
        searchQuery
    ) { core, query ->
        core.copy(searchQuery = query)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TransactionListUiState()
        )

    val uiState: StateFlow<TransactionListUiState> = combine(
        listCore,
        frequentContacts
    ) { core, frequent ->
        core.copy(frequentContacts = frequent)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TransactionListUiState()
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setWalletAccount(account: WalletAccount) {
        walletAccount.value = account
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
                val inserted = repo.rescanInbox()
                scanMessage.value = if (inserted == 0) {
                    "Inbox scanned — no new M-Pesa messages"
                } else {
                    "Added $inserted new transaction${if (inserted == 1) "" else "s"}"
                }
            } catch (_: SecurityException) {
                scanMessage.value = "SMS permission required to scan inbox"
            } catch (e: Exception) {
                scanMessage.value = "Scan failed: ${e.message ?: "unknown error"}"
            } finally {
                isScanning.value = false
            }
        }
    }

    fun clearScanMessage() {
        scanMessage.value = null
    }

    private fun buildAnalytics(
        txs: List<MpesaTransaction>,
        rangeState: AnalyticsRangeState,
        account: WalletAccount
    ): AnalyticsSummary {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val availableYears = txs
            .map { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).year }
            .distinct()
            .sortedDescending()

        val selectedYear = rangeState.year
            ?: availableYears.firstOrNull()
            ?: today.year

        val (from, to, label) = resolveBounds(
            range = rangeState.range,
            today = today,
            year = selectedYear,
            customStart = rangeState.customStart,
            customEnd = rangeState.customEnd
        )

        val filtered = txs.filter { tx ->
            val date = Instant.ofEpochMilli(tx.timestampMillis).atZone(zone).toLocalDate()
            !date.isBefore(from) && !date.isAfter(to)
        }

        val (income, expense) = flowTotals(filtered, account)

        val byType = filtered
            .groupBy { it.type }
            .map { (type, list) ->
                TypeBreakdown(
                    label = type.displayLabel(),
                    count = list.size,
                    total = list.sumOf { it.amount ?: 0.0 }
                )
            }
            .sortedByDescending { it.total }

        val series = buildSeries(filtered, from, to, rangeState.range, zone, account)

        return AnalyticsSummary(
            incomeTotal = income,
            expenseTotal = expense,
            byType = byType,
            series = series,
            txnCount = filtered.size,
            range = rangeState.range,
            selectedYear = if (rangeState.range == AnalyticsRange.YEAR) selectedYear else rangeState.year,
            availableYears = availableYears,
            customStart = rangeState.customStart,
            customEnd = rangeState.customEnd,
            rangeLabel = label
        )
    }

    private fun resolveBounds(
        range: AnalyticsRange,
        today: LocalDate,
        year: Int,
        customStart: LocalDate?,
        customEnd: LocalDate?
    ): Triple<LocalDate, LocalDate, String> = when (range) {
        AnalyticsRange.DAYS_7 -> Triple(today.minusDays(6), today, "Last 7 days")
        AnalyticsRange.DAYS_30 -> Triple(today.minusDays(29), today, "Last 30 days")
        AnalyticsRange.DAYS_90 -> Triple(today.minusDays(89), today, "Last 90 days")
        AnalyticsRange.THIS_YEAR -> Triple(
            LocalDate.of(today.year, 1, 1),
            today,
            "Year ${today.year}"
        )
        AnalyticsRange.YEAR -> Triple(
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 12, 31).coerceAtMost(today),
            "Year $year"
        )
        AnalyticsRange.CUSTOM -> {
            val start = customStart ?: today.minusDays(29)
            val end = customEnd ?: today
            val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
            Triple(start, end, "${start.format(fmt)} – ${end.format(fmt)}")
        }
        AnalyticsRange.ALL -> Triple(LocalDate.of(2010, 1, 1), today, "All time")
    }

    private fun buildSeries(
        txs: List<MpesaTransaction>,
        from: LocalDate,
        to: LocalDate,
        range: AnalyticsRange,
        zone: ZoneId,
        account: WalletAccount
    ): List<AnalyticsPoint> {
        if (txs.isEmpty() && range == AnalyticsRange.ALL) return emptyList()

        val dayFmt = DateTimeFormatter.ofPattern("d MMM")
        val monthFmt = DateTimeFormatter.ofPattern("MMM")
        val monthYearFmt = DateTimeFormatter.ofPattern("MMM yy")

        val byDate = txs.groupBy {
            Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
        }

        fun totalsFor(dates: List<LocalDate>): Pair<Double, Double> =
            flowTotals(dates.flatMap { byDate[it].orEmpty() }, account)

        val days = ChronoUnit.DAYS.between(from, to) + 1
        return when {
            range == AnalyticsRange.ALL && days > 400 -> {
                val startYm = YearMonth.from(from)
                val endYm = YearMonth.from(to)
                generateSequence(startYm) { ym ->
                    val next = ym.plusMonths(1)
                    if (next > endYm) null else next
                }.map { ym ->
                    val start = ym.atDay(1).coerceAtLeast(from)
                    val end = ym.atEndOfMonth().coerceAtMost(to)
                    val dates = generateSequence(start) { d ->
                        val n = d.plusDays(1)
                        if (n > end) null else n
                    }.toList()
                    val (inc, exp) = totalsFor(dates)
                    AnalyticsPoint(ym.format(monthYearFmt), inc, exp)
                }.toList()
            }
            days > 45 -> {
                val startYm = YearMonth.from(from)
                val endYm = YearMonth.from(to)
                generateSequence(startYm) { ym ->
                    val next = ym.plusMonths(1)
                    if (next > endYm) null else next
                }.map { ym ->
                    val start = ym.atDay(1).coerceAtLeast(from)
                    val end = ym.atEndOfMonth().coerceAtMost(to)
                    val dates = generateSequence(start) { d ->
                        val n = d.plusDays(1)
                        if (n > end) null else n
                    }.toList()
                    val (inc, exp) = totalsFor(dates)
                    val label = if (startYm.year == endYm.year) ym.format(monthFmt) else ym.format(monthYearFmt)
                    AnalyticsPoint(label, inc, exp)
                }.toList()
            }
            else -> {
                generateSequence(from) { d ->
                    val n = d.plusDays(1)
                    if (n > to) null else n
                }.map { date ->
                    val (inc, exp) = totalsFor(listOf(date))
                    AnalyticsPoint(date.format(dayFmt), inc, exp)
                }.toList()
            }
        }
    }

    /**
     * M-PESA: money in vs money out.
     * Fuliza: amount drawn vs access fees. Outstanding stays on the balance, not in these totals.
     */
    private fun flowTotals(
        txs: Iterable<MpesaTransaction>,
        account: WalletAccount
    ): Pair<Double, Double> {
        if (account == WalletAccount.FULIZA) {
            var drawn = 0.0
            var fees = 0.0
            for (tx in txs) {
                drawn += tx.amount ?: 0.0
                fees += tx.cost ?: 0.0
            }
            return drawn to fees
        }
        var income = 0.0
        var expense = 0.0
        for (tx in txs) {
            val amount = tx.amount ?: continue
            if (tx.type.isOutgoing()) expense += amount else income += amount
        }
        return income to expense
    }

    private fun List<MpesaTransaction>.onWallet(account: WalletAccount): List<MpesaTransaction> {
        val fuliza = account == WalletAccount.FULIZA
        return filter { (it.type == TransactionType.FULIZA) == fuliza }
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

    private data class WalletSlice(
        val visible: List<MpesaTransaction>,
        val analytics: List<MpesaTransaction>,
        val mpesaBalance: Double?,
        val fulizaOutstanding: Double?,
        val account: WalletAccount
    )

    private data class AnalyticsRangeState(
        val range: AnalyticsRange,
        val year: Int?,
        val customStart: LocalDate?,
        val customEnd: LocalDate?
    )
}
