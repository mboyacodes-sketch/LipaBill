package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.data.repository.TransactionRepository
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.isOutgoing
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
    val analytics: AnalyticsSummary = AnalyticsSummary(),
    val frequentContacts: List<SendContact> = emptyList(),
    val searchQuery: String = ""
)

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
            transactions,
            hasSmsPermission,
            scan,
            analyticsSource,
            rangeState
        ) { txs, smsOk, s, analyticsTxs, range ->
            val (scanning, message) = s
            TransactionListUiState(
                grouped = groupByDay(txs),
                isScanning = scanning,
                scanMessage = message,
                hasSmsPermission = smsOk,
                latestBalance = analyticsTxs.firstOrNull { it.balance != null }?.balance
                    ?: txs.firstOrNull { it.balance != null }?.balance,
                analytics = buildAnalytics(analyticsTxs, range)
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
        rangeState: AnalyticsRangeState
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

        var income = 0.0
        var expense = 0.0
        filtered.forEach { tx ->
            val amount = tx.amount ?: return@forEach
            if (tx.type.isOutgoing()) expense += amount else income += amount
        }

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

        val series = buildSeries(filtered, from, to, rangeState.range, zone)

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
        zone: ZoneId
    ): List<AnalyticsPoint> {
        if (txs.isEmpty() && range == AnalyticsRange.ALL) return emptyList()

        val dayFmt = DateTimeFormatter.ofPattern("d MMM")
        val monthFmt = DateTimeFormatter.ofPattern("MMM")
        val monthYearFmt = DateTimeFormatter.ofPattern("MMM yy")

        val byDate = txs.groupBy {
            Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate()
        }

        fun totalsFor(dates: List<LocalDate>): Pair<Double, Double> {
            var income = 0.0
            var expense = 0.0
            dates.forEach { date ->
                byDate[date].orEmpty().forEach { tx ->
                    val amount = tx.amount ?: return@forEach
                    if (tx.type.isOutgoing()) expense += amount else income += amount
                }
            }
            return income to expense
        }

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

    private data class AnalyticsRangeState(
        val range: AnalyticsRange,
        val year: Int?,
        val customStart: LocalDate?,
        val customEnd: LocalDate?
    )
}
