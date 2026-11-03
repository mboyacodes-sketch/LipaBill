package com.lipabill.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.LipaBillApp
import com.lipabill.app.ui.LipaChoiceChip
import com.lipabill.app.ui.SourceFilterRow
import com.lipabill.app.ui.components.BalanceAmountRow
import com.lipabill.app.ui.components.WalletAccountSwitch
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.OnPastel
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.theme.avatarPastel
import com.lipabill.app.ui.util.categoryDisc
import com.lipabill.app.ui.util.categoryInk
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.viewmodel.AnalyticsPoint
import com.lipabill.app.viewmodel.AnalyticsRange
import com.lipabill.app.viewmodel.CounterpartySpend
import com.lipabill.app.viewmodel.PulseCell
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.TypeBreakdown
import com.lipabill.app.viewmodel.WalletAccount
import com.lipabill.app.viewmodel.busiestDayLabel
import com.lipabill.app.viewmodel.calendarAmountLabel
import com.lipabill.app.viewmodel.dailyAverage
import com.lipabill.app.viewmodel.spendShareOfIncome
import com.lipabill.app.viewmodel.vsPriorCopy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(
    viewModel: TransactionListViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats = state.analytics
    val account = state.account
    val shownBalance = state.shownBalance()
    val net = stats.incomeTotal - stats.expenseTotal
    val netPrior = stats.previousIncomeTotal - stats.previousExpenseTotal
    val emptyPeriod = stats.txnCount == 0
    var showDatePicker by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as LipaBillApp

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = {
                        Text(
                            account.metricsTitle,
                            style = HomeType.greeting
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page)
        ) {
            if (onBack == null) {
                Text(
                    account.metricsTitle,
                    style = HomeType.greeting
                )
                Spacer(modifier = Modifier.height(Space.block))
            }

            InsightsSection(
                icon = Icons.Outlined.AccountBalance,
                title = account.balanceCaption
            ) {
                WalletAccountSwitch(
                    account = state.account,
                    onSelect = viewModel::setWalletAccount,
                    accounts = if (state.showFuliza) {
                        WalletAccount.entries
                    } else {
                        listOf(WalletAccount.MPESA)
                    }
                )
                Spacer(modifier = Modifier.height(Space.block))
                BalanceAmountRow(
                    balance = shownBalance,
                    amountStyle = HomeType.amount,
                    showEyeToggle = true
                )
            }

            InsightsSection(
                icon = Icons.Outlined.CalendarMonth,
                title = "Period",
                caption = stats.rangeLabel
            ) {
                RangeSegmentRow(
                    selected = stats.range,
                    onSelect = { viewModel.setAnalyticsRange(it) },
                    onCustom = { showDatePicker = true }
                )
                val yearMode = stats.range == AnalyticsRange.THIS_YEAR ||
                    stats.range == AnalyticsRange.YEAR
                if (yearMode && stats.availableYears.size > 1) {
                    Spacer(modifier = Modifier.height(Space.block))
                    HorizontalDivider(color = Hairline, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(Space.block))
                    YearChipRow(
                        years = stats.availableYears,
                        selectedYear = stats.selectedYear ?: LocalDate.now().year,
                        onSelect = { viewModel.setAnalyticsYear(it) }
                    )
                }
            }

            if (account != WalletAccount.FULIZA && state.availableSources.size > 1) {
                InsightsSection(
                    icon = Icons.Outlined.AccountBalanceWallet,
                    title = "Ledger",
                    caption = state.sourceFilter?.label ?: "All wallets and banks"
                ) {
                    SourceFilterRow(
                        sources = state.availableSources,
                        selected = state.sourceFilter,
                        onSelect = viewModel::setSourceFilter
                    )
                }
            }

            InsightsSection(
                icon = Icons.Outlined.AutoGraph,
                title = "What is shaping it"
            ) {
                StretchStoryCard(
                    headline = stats.storyHeadline,
                    detail = stats.storyDetail,
                    hasLedger = stats.hasLedger,
                    quietDays = stats.quietDays,
                    quietStreak = stats.quietStreak,
                    busiestLabel = stats.busiestLabel,
                    busiestTotal = stats.busiestTotal,
                    pulse = stats.pulse,
                    fuliza = account == WalletAccount.FULIZA
                )
            }

            if (stats.hasLedger && !emptyPeriod) {
                InsightsSection(
                    icon = Icons.Outlined.Payments,
                    title = "This stretch"
                ) {
                    PeriodNetCard(
                        net = net,
                        vsPrior = vsPriorCopy(net, netPrior, stats.comparable),
                        vsPriorColor = deltaColor(net, netPrior, moreIsGood = true),
                        spendShare = if (account == WalletAccount.MPESA) {
                            spendShareOfIncome(stats.expenseTotal, stats.incomeTotal)
                        } else {
                            null
                        }
                    )
                    Spacer(modifier = Modifier.height(Space.block))
                    HorizontalDivider(color = Hairline, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(Space.block))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.block)
                    ) {
                        MetricStatCard(
                            title = account.inflowLabel,
                            amount = stats.incomeTotal,
                            tint = Income,
                            icon = Icons.Outlined.SouthWest,
                            changeLabel = vsPriorCopy(
                                stats.incomeTotal,
                                stats.previousIncomeTotal,
                                stats.comparable
                            ),
                            changeColor = deltaColor(
                                stats.incomeTotal,
                                stats.previousIncomeTotal,
                                moreIsGood = true
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(56.dp)
                                .background(Hairline)
                        )
                        MetricStatCard(
                            title = account.outflowLabel,
                            amount = stats.expenseTotal,
                            tint = Expense,
                            icon = Icons.Outlined.NorthEast,
                            changeLabel = vsPriorCopy(
                                stats.expenseTotal,
                                stats.previousExpenseTotal,
                                stats.comparable
                            ),
                            changeColor = deltaColor(
                                stats.expenseTotal,
                                stats.previousExpenseTotal,
                                moreIsGood = false
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(Space.block))
                    HorizontalDivider(color = Hairline, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(Space.block))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.block)
                    ) {
                        MetricTile(
                            title = "Avg / day",
                            value = formatKes(dailyAverage(stats.expenseTotal, stats.dayCount)),
                            valueColor = Ink,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(Hairline)
                        )
                        MetricTile(
                            title = "Transactions",
                            value = stats.txnCount.toString(),
                            valueColor = Ink,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                InsightsSection(
                    icon = Icons.AutoMirrored.Outlined.ShowChart,
                    title = "Cash flow",
                    caption = account.cashFlowCaption
                ) {
                    CashFlowChartCard(
                        series = stats.series,
                        inflowLabel = account.inflowLabel,
                        outflowLabel = account.outflowLabel
                    )
                }

                InsightsSection(
                    icon = Icons.Outlined.Category,
                    title = "By type",
                    caption = if (state.typeFilter == null) {
                        "Tap a slice or a type to focus"
                    } else {
                        stats.byType.firstOrNull { it.type == state.typeFilter }?.label
                            ?: "Tap again to clear"
                    }
                ) {
                    TypeBreakdownCard(
                        rows = stats.byType,
                        selected = state.typeFilter,
                        onSelect = viewModel::setTypeFilter
                    )
                }

                if (stats.topCounterparties.isNotEmpty()) {
                    InsightsSection(
                        icon = Icons.Outlined.Groups,
                        title = "Top people",
                        caption = "Tap someone to see their share of outgoing"
                    ) {
                        TopCounterpartiesCard(rows = stats.topCounterparties)
                    }
                }
            }
            Spacer(modifier = Modifier.height(Space.section))
        }
    }

    if (showDatePicker) {
        MetricsDateRangePicker(
            initialStart = stats.customStart,
            initialEnd = stats.customEnd,
            onDismiss = { showDatePicker = false },
            onConfirm = { start, end ->
                viewModel.setCustomDateRange(start, end)
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun RangeSegmentRow(
    selected: AnalyticsRange,
    onSelect: (AnalyticsRange) -> Unit,
    onCustom: () -> Unit
) {
    val presets = listOf(
        AnalyticsRange.DAYS_7 to "Week",
        AnalyticsRange.DAYS_30 to "Month",
        AnalyticsRange.THIS_YEAR to "Year",
        AnalyticsRange.ALL to "All"
    )
    val yearOn = selected == AnalyticsRange.THIS_YEAR || selected == AnalyticsRange.YEAR
    val customOn = selected == AnalyticsRange.CUSTOM
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(shape)
                .border(1.dp, Hairline, shape)
                .background(Canvas)
        ) {
            presets.forEachIndexed { index, (range, label) ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Hairline)
                    )
                }
                val on = if (range == AnalyticsRange.THIS_YEAR) yearOn else selected == range
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(range) }
                        .background(if (on) Accent else Color.Transparent)
                ) {
                    Text(
                        text = label,
                        style = HomeType.label,
                        color = if (on) OnAccent else Ink,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(shape)
                .border(1.dp, if (customOn) Accent else Hairline, shape)
                .background(if (customOn) Accent else Canvas)
                .clickable(onClick = onCustom)
        ) {
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = "Custom dates",
                tint = if (customOn) OnAccent else Accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun YearChipRow(
    years: List<Int>,
    selectedYear: Int?,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        years.forEach { year ->
            LipaChoiceChip(
                label = year.toString(),
                selected = year == selectedYear,
                onClick = { onSelect(year) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricsDateRangePicker(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    val zone = ZoneOffset.UTC
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart
            ?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
        initialSelectedEndDateMillis = initialEnd
            ?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val startMs = state.selectedStartDateMillis ?: return@TextButton
                    val endMs = state.selectedEndDateMillis ?: state.selectedStartDateMillis
                        ?: return@TextButton
                    val start = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
                    val end = Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate()
                    onConfirm(start, end)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    "Select date range",
                    modifier = Modifier.padding(start = Space.page, end = Space.block, top = Space.block)
                )
            },
            headline = null,
            showModeToggle = false,
            modifier = Modifier.height(460.dp)
        )
    }
}

@Composable
private fun PeriodNetCard(
    net: Double,
    vsPrior: String?,
    vsPriorColor: Color,
    spendShare: Double?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Net this period", style = HomeType.label, color = Mute)
        Spacer(modifier = Modifier.height(Space.gap))
        Text(
            text = if (net < 0) "−${formatKes(abs(net))}" else formatKes(net),
            style = HomeType.greeting,
            color = if (net >= 0) Income else Expense,
            modifier = Modifier.recordingPrivacyCover()
        )
        if (vsPrior != null) {
            Spacer(modifier = Modifier.height(Space.tight))
            Text(vsPrior, style = HomeType.caption, color = vsPriorColor)
        }
        if (spendShare != null) {
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "Spent ${(spendShare * 100).roundToInt()}% of income",
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
    }
}

@Composable
private fun StretchStoryCard(
    headline: String,
    detail: String?,
    hasLedger: Boolean,
    quietDays: Int,
    quietStreak: Int,
    busiestLabel: String?,
    busiestTotal: Double,
    pulse: List<PulseCell>,
    fuliza: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(headline, style = HomeType.greeting, color = Ink)
        if (detail != null) {
            Spacer(modifier = Modifier.height(Space.tight))
            Text(detail, style = HomeType.body, color = Mute)
        }
        if (pulse.isNotEmpty()) {
            Spacer(modifier = Modifier.height(Space.block))
            PulseGrid(cells = pulse, fuliza = fuliza)
        }
        if (hasLedger) {
            Spacer(modifier = Modifier.height(Space.block))
            HorizontalDivider(color = Hairline, thickness = 1.dp)
            Spacer(modifier = Modifier.height(Space.block))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RhythmStat(
                    title = if (fuliza) "No-draw days" else "Quiet days",
                    value = quietDays.toString(),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = Space.gap)
                        .width(1.dp)
                        .height(36.dp)
                        .background(Hairline)
                )
                RhythmStat(
                    title = "In a row",
                    value = quietStreak.toString(),
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = Space.gap)
                        .width(1.dp)
                        .height(36.dp)
                        .background(Hairline)
                )
                RhythmStat(
                    title = "Busiest",
                    value = busiestLabel ?: "—",
                    detail = busiestLabel?.let { formatKes(busiestTotal) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PulseGrid(cells: List<PulseCell>, fuliza: Boolean) {
    var selected by remember(cells) {
        mutableStateOf(
            cells.firstOrNull { it.isToday && it.inRange }
                ?: cells.lastOrNull { it.inRange && it.hasOutflow }
                ?: cells.lastOrNull { it.inRange }
        )
    }
    val weeks = cells.chunked(7)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                    Text(
                        text = day,
                        style = HomeType.caption,
                        color = Mute,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    week.forEach { cell ->
                        PulseDayCell(
                            cell = cell,
                            selected = cell.date == selected?.date,
                            onSelect = { if (cell.inRange) selected = cell },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(7 - week.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        val day = selected
        if (day != null && day.inRange) {
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = if (day.isToday) "Today" else busiestDayLabel(day.date),
                style = HomeType.caption,
                color = Mute
            )
            Spacer(modifier = Modifier.height(Space.tight))
            if (day.hasInflow) {
                Text(
                    text = "+${formatKes(day.inflow)} received",
                    style = HomeType.body,
                    color = Income,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
            if (day.hasOutflow) {
                Text(
                    text = "−${formatKes(day.outflow)} ${if (fuliza) "drawn" else "spent"}",
                    style = HomeType.body,
                    color = Expense,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
            if (!day.hasInflow && !day.hasOutflow) {
                Text("Nothing moved this day", style = HomeType.body, color = Mute)
            }
        }
    }
}

@Composable
private fun PulseDayCell(
    cell: PulseCell,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val fill = when {
        !cell.inRange -> Color.Transparent
        selected -> SoftBlue
        else -> Color.Transparent
    }
    val dateColor = if (cell.inRange) Ink else Hairline
    val outline = when {
        selected && cell.inRange -> Ink
        cell.isToday && cell.inRange -> Accent
        cell.inRange -> Hairline
        else -> Color.Transparent
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .then(if (outline != Color.Transparent) Modifier.border(1.dp, outline, shape) else Modifier)
            .clickable(enabled = cell.inRange, onClick = onSelect)
            .padding(top = 6.dp, bottom = 6.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = HomeType.label,
            color = dateColor
        )
        if (cell.inRange) {
            Spacer(modifier = Modifier.height(2.dp))
            Column(
                modifier = Modifier.recordingPrivacyCover(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val received = calendarAmountLabel(cell.inflow)
                val spent = calendarAmountLabel(cell.outflow)
                if (received.isNotEmpty()) {
                    Text(
                        text = received,
                        style = HomeType.caption,
                        color = Income,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                if (spent.isNotEmpty()) {
                    Text(
                        text = spent,
                        style = HomeType.caption,
                        color = Expense,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
                if (received.isEmpty() && spent.isEmpty()) {
                    Text("—", style = HomeType.caption, color = Mute)
                }
            }
        }
    }
}

@Composable
private fun RhythmStat(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null
) {
    Column(modifier = modifier) {
        Text(title, style = HomeType.caption, color = Mute)
        Spacer(modifier = Modifier.height(Space.tight))
        Text(
            text = value,
            style = HomeType.rowTitle,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover()
        )
        if (detail != null) {
            Text(
                text = detail,
                style = HomeType.caption,
                color = Mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
    }
}

@Composable
private fun CashFlowChartCard(
    series: List<AnalyticsPoint>,
    inflowLabel: String,
    outflowLabel: String
) {
    val maxY = remember(series) {
        max(series.maxOfOrNull { max(it.income, it.expense) } ?: 0.0, 1.0)
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.chip)
        ) {
            LegendDot(color = Income, label = inflowLabel)
            LegendDot(color = Expense, label = outflowLabel)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatChartAxis(maxY),
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
        Spacer(modifier = Modifier.height(Space.block))
        if (series.isEmpty() || series.all { it.income == 0.0 && it.expense == 0.0 }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No data for this period", style = HomeType.body, color = Mute)
            }
        } else {
            IncomeExpenseBarChart(
                series = series,
                maxY = maxY,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .recordingPrivacyCover()
            )
            Spacer(modifier = Modifier.height(Space.gap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(series.first().label, style = HomeType.caption, color = Mute)
                if (series.size > 1) {
                    Text(series.last().label, style = HomeType.caption, color = Mute)
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(Space.gap))
        Text(label, style = HomeType.caption, color = Mute)
    }
}

@Composable
private fun IncomeExpenseBarChart(
    series: List<AnalyticsPoint>,
    maxY: Double,
    modifier: Modifier = Modifier
) {
    val grid = Hairline
    val inflow = Income
    val outflow = Expense
    Canvas(modifier = modifier) {
        val leftPad = 4.dp.toPx()
        val rightPad = 4.dp.toPx()
        val topPad = 8.dp.toPx()
        val bottomPad = 4.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad
        val baseline = topPad + chartH
        for (i in 0..3) {
            val y = topPad + chartH * i / 3f
            drawLine(
                color = grid,
                start = Offset(leftPad, y),
                end = Offset(leftPad + chartW, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        val n = series.size.coerceAtLeast(1)
        val slot = chartW / n
        val pairGap = 2.dp.toPx()
        val barW = ((slot - pairGap) * 0.38f).coerceIn(3.dp.toPx(), 14.dp.toPx())
        val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        series.forEachIndexed { i, point ->
            val cx = leftPad + slot * i + slot / 2f
            val inH = ((point.income / maxY).toFloat().coerceIn(0f, 1f) * chartH)
            val outH = ((point.expense / maxY).toFloat().coerceIn(0f, 1f) * chartH)
            if (inH > 0.5f) {
                drawRoundRect(
                    color = inflow,
                    topLeft = Offset(cx - barW - pairGap / 2f, baseline - inH),
                    size = Size(barW, inH),
                    cornerRadius = radius
                )
            }
            if (outH > 0.5f) {
                drawRoundRect(
                    color = outflow,
                    topLeft = Offset(cx + pairGap / 2f, baseline - outH),
                    size = Size(barW, outH),
                    cornerRadius = radius
                )
            }
        }
    }
}

@Composable
private fun TypeBreakdownCard(
    rows: List<TypeBreakdown>,
    selected: TransactionType?,
    onSelect: (TransactionType?) -> Unit
) {
    val volume = rows.sumOf { it.total }
    val focused = rows.firstOrNull { it.type == selected }
    Column(modifier = Modifier.fillMaxWidth()) {
        ShareStack(
            slices = rows.map { row ->
                ShareSlice(
                    key = row.type.name,
                    weight = row.total,
                    color = row.type.categoryInk(),
                    label = row.label
                )
            },
            selectedKey = selected?.name,
            onSelectKey = { key ->
                onSelect(rows.firstOrNull { it.type.name == key }?.type)
            },
            modifier = Modifier.recordingPrivacyCover()
        )
        if (focused != null && volume > 0.0) {
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = "${focused.label} is ${(focused.total / volume * 100).roundToInt()}% of this stretch",
                style = HomeType.body,
                color = focused.type.categoryInk(),
                modifier = Modifier.recordingPrivacyCover()
            )
        }
        Spacer(modifier = Modifier.height(Space.block))
        rows.forEachIndexed { index, row ->
            val ink = row.type.categoryInk()
            val share = if (volume <= 0.0) 0 else (row.total / volume * 100).roundToInt()
            val on = row.type == selected
            if (index > 0) {
                HorizontalDivider(color = Hairline, thickness = 1.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) row.type.categoryDisc() else Color.Transparent)
                    .clickable { onSelect(if (on) null else row.type) }
                    .padding(vertical = Space.block, horizontal = Space.gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FaviconDisc(
                    icon = row.type.insightIcon(),
                    fill = row.type.categoryDisc(),
                    tint = ink
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.label,
                        style = HomeType.rowTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${row.count} txn${if (row.count == 1) "" else "s"} · $share%",
                        style = HomeType.caption,
                        color = Mute,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
                Text(
                    formatKes(row.total),
                    style = HomeType.rowTitle,
                    color = ink,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
    }
}

@Composable
private fun TopCounterpartiesCard(rows: List<CounterpartySpend>) {
    val volume = rows.sumOf { it.total }
    var selected by remember(rows) { mutableStateOf<String?>(null) }
    val focused = rows.firstOrNull { it.name == selected }
    Column(modifier = Modifier.fillMaxWidth()) {
        ShareStack(
            slices = rows.map { row ->
                ShareSlice(
                    key = row.name,
                    weight = row.total,
                    color = avatarPastel(row.name.hashCode()),
                    label = row.name
                )
            },
            selectedKey = selected,
            onSelectKey = { selected = it },
            modifier = Modifier.recordingPrivacyCover()
        )
        if (focused != null && volume > 0.0) {
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = "${focused.name} took ${(focused.total / volume * 100).roundToInt()}% of outgoing here",
                style = HomeType.body,
                color = Ink,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
        Spacer(modifier = Modifier.height(Space.block))
        rows.forEachIndexed { index, row ->
            val letter = row.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val fill = avatarPastel(row.name.hashCode())
            val on = row.name == selected
            if (index > 0) {
                HorizontalDivider(color = Hairline, thickness = 1.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) fill.copy(alpha = 0.55f) else Color.Transparent)
                    .clickable { selected = if (on) null else row.name }
                    .padding(vertical = Space.block, horizontal = Space.gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FaviconDisc(
                    letter = letter,
                    fill = fill,
                    tint = OnPastel
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        row.name,
                        style = HomeType.rowTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                    Text(
                        "${row.count} txn${if (row.count == 1) "" else "s"}",
                        style = HomeType.caption,
                        color = Mute,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
                Text(
                    formatKes(row.total),
                    style = HomeType.rowTitle,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = HomeType.label, color = Mute)
        Spacer(modifier = Modifier.height(Space.gap))
        Text(
            value,
            style = HomeType.amount,
            color = valueColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

@Composable
private fun MetricStatCard(
    title: String,
    amount: Double,
    tint: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    changeLabel: String? = null,
    changeColor: Color = Mute
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = HomeType.label,
                color = Mute,
                modifier = Modifier.weight(1f)
            )
            Icon(icon, contentDescription = null, tint = tint)
        }
        Spacer(modifier = Modifier.height(Space.block))
        Text(
            text = formatKes(amount),
            style = HomeType.rowTitle,
            color = tint,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.recordingPrivacyCover()
        )
        if (changeLabel != null) {
            Spacer(modifier = Modifier.height(Space.tight))
            Text(changeLabel, style = HomeType.caption, color = changeColor)
        }
    }
}

@Composable
@ReadOnlyComposable
private fun deltaColor(current: Double, previous: Double, moreIsGood: Boolean): Color {
    val delta = current - previous
    if (abs(delta) < 0.005) return Mute
    val improved = if (moreIsGood) delta > 0 else delta < 0
    return if (improved) Income else Expense
}

private fun formatChartAxis(amount: Double): String {
    val magnitude = abs(amount)
    return when {
        magnitude >= 1_000_000 -> String.format(Locale.US, "%.1fM", amount / 1_000_000.0)
        magnitude >= 10_000 -> String.format(Locale.US, "%.0fK", amount / 1_000.0)
        magnitude >= 1_000 -> String.format(Locale.US, "%.1fK", amount / 1_000.0)
        else -> formatKes(amount)
    }
}

@Composable
private fun InsightsSection(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Space.section)
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(Space.card)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FaviconDisc(icon = icon, fill = SoftBlue, tint = Accent)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = HomeType.section, color = Ink)
                if (caption != null) {
                    Text(caption, style = HomeType.caption, color = Mute)
                }
            }
        }
        Spacer(modifier = Modifier.height(Space.block))
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(Space.block))
        content()
    }
}

@Composable
private fun FaviconDisc(
    fill: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    letter: String? = null
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        } else if (letter != null) {
            Text(letter, style = HomeType.rowTitle, color = tint)
        }
    }
}

private data class ShareSlice(
    val key: String,
    val weight: Double,
    val color: Color,
    val label: String
)

@Composable
private fun ShareStack(
    slices: List<ShareSlice>,
    selectedKey: String?,
    onSelectKey: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = slices.filter { it.weight > 0.0 }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Canvas),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        visible.forEach { slice ->
            val on = slice.key == selectedKey
            val dimmed = selectedKey != null && !on
            Box(
                modifier = Modifier
                    .weight(slice.weight.toFloat().coerceAtLeast(0.05f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (dimmed) slice.color.copy(alpha = 0.28f) else slice.color)
                    .clickable { onSelectKey(if (on) null else slice.key) }
            )
        }
    }
}

private fun TransactionType.insightIcon(): ImageVector = when (this) {
    TransactionType.SENT -> Icons.Outlined.NorthEast
    TransactionType.RECEIVED -> Icons.Outlined.SouthWest
    TransactionType.PAYBILL -> Icons.AutoMirrored.Outlined.ReceiptLong
    TransactionType.BUY_GOODS -> Icons.Outlined.Storefront
    TransactionType.POCHI -> Icons.Outlined.AccountBalanceWallet
    TransactionType.WITHDRAW -> Icons.Outlined.NorthEast
    TransactionType.DEPOSIT -> Icons.Outlined.SouthWest
    TransactionType.REVERSED -> Icons.AutoMirrored.Outlined.Undo
    TransactionType.FULIZA -> Icons.Outlined.Bolt
    TransactionType.SAVINGS -> Icons.Outlined.Savings
    TransactionType.AIRTIME -> Icons.Outlined.PhoneIphone
    TransactionType.UNKNOWN -> Icons.Outlined.SwapHoriz
}
