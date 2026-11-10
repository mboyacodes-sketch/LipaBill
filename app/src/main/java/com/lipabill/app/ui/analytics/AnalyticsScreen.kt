package com.lipabill.app.ui.analytics

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import com.lipabill.app.data.export.ledgerCsv
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.viewmodel.FeeSummary
import com.lipabill.app.viewmodel.RegularPayee
import com.lipabill.app.viewmodel.SpendingHabit
import com.lipabill.app.viewmodel.regularCadenceCopy
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NorthEast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.ui.LipaChoiceChip
import com.lipabill.app.ui.SourceFilterRow
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
import com.lipabill.app.viewmodel.AnalyticsRange
import com.lipabill.app.viewmodel.CounterpartySpend
import com.lipabill.app.viewmodel.PulseCell
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.TypeBreakdown
import com.lipabill.app.viewmodel.WalletAccount
import com.lipabill.app.viewmodel.busiestDayLabel
import com.lipabill.app.viewmodel.calendarAmountLabel
import com.lipabill.app.viewmodel.spendShareOfIncome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
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
    val emptyPeriod = stats.txnCount == 0
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportRows = stats.periodTransactions
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val csv = ledgerCsv(exportRows, ZoneId.systemDefault())
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                if (written) "Exported ${exportRows.size} transactions" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

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

            // Controls stay compact so the first screen is about money, not settings.
            if (state.showFuliza) {
                WalletAccountSwitch(
                    account = state.account,
                    onSelect = viewModel::setWalletAccount,
                    accounts = WalletAccount.entries
                )
                Spacer(modifier = Modifier.height(Space.block))
            }
            RangeSegmentRow(
                selected = stats.range,
                onSelect = { viewModel.setAnalyticsRange(it) },
                onCustom = { showDatePicker = true }
            )
            val yearMode = stats.range == AnalyticsRange.THIS_YEAR ||
                stats.range == AnalyticsRange.YEAR
            if (yearMode && stats.availableYears.size > 1) {
                Spacer(modifier = Modifier.height(Space.block))
                YearChipRow(
                    years = stats.availableYears,
                    selectedYear = stats.selectedYear ?: LocalDate.now().year,
                    onSelect = { viewModel.setAnalyticsYear(it) }
                )
            }
            if (account != WalletAccount.FULIZA && state.availableSources.size > 1) {
                Spacer(modifier = Modifier.height(Space.block))
                SourceFilterRow(
                    sources = state.availableSources,
                    selected = state.sourceFilter,
                    onSelect = viewModel::setSourceFilter
                )
            }
            Spacer(modifier = Modifier.height(Space.section))

            InsightsHero(
                rangeLabel = stats.rangeLabel,
                headline = stats.keptHeadline ?: stats.storyHeadline,
                detail = if (stats.keptHeadline != null) stats.storyHeadline else stats.storyDetail,
                showFigures = stats.hasLedger && !emptyPeriod,
                income = stats.incomeTotal,
                expense = stats.expenseTotal,
                inflowLabel = account.inflowLabel,
                outflowLabel = account.outflowLabel,
                spendShare = if (account == WalletAccount.MPESA) {
                    spendShareOfIncome(stats.expenseTotal, stats.incomeTotal)
                } else {
                    null
                },
                fees = stats.fees.total,
                quietStreak = stats.quietStreak,
                fuliza = account == WalletAccount.FULIZA
            )

            if (stats.hasLedger && !emptyPeriod) {
                if (account == WalletAccount.MPESA &&
                    (stats.regulars.isNotEmpty() || stats.fees.total > 0.0 || stats.habit != null)
                ) {
                    CostingYouCard(
                        regulars = stats.regulars,
                        fees = stats.fees,
                        expense = stats.expenseTotal,
                        habit = stats.habit,
                        onEssential = viewModel::setPayeeEssential
                    )
                }
                if (account == WalletAccount.MPESA &&
                    stats.fees.withdrawCount >= 3 && stats.fees.withdrawFees > 0.0
                ) {
                    FeeTipCard(
                        withdrawCount = stats.fees.withdrawCount,
                        withdrawFees = stats.fees.withdrawFees
                    )
                }

                InsightsSection(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "Day by day",
                    caption = stats.rangeLabel
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
                        fuliza = account == WalletAccount.FULIZA,
                        showStory = false
                    )
                }

                InsightsSection(
                    icon = Icons.Outlined.Category,
                    title = "Where it went",
                    caption = if (state.typeFilter == null) {
                        "Tap a slice or a row to focus"
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
                        title = "Who you pay most",
                        caption = "Tap someone to see their share"
                    ) {
                        TopCounterpartiesCard(rows = stats.topCounterparties)
                    }
                }
            } else if (stats.hasLedger) {
                InsightsSection(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "Day by day",
                    caption = stats.rangeLabel
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
                        fuliza = account == WalletAccount.FULIZA,
                        showStory = false
                    )
                }
            }

            if (exportRows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Space.block))
                OutlinedButton(
                    onClick = { exportLauncher.launch(csvFileName(stats.rangeLabel)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Space.gap))
                    Text("Export as CSV", style = HomeType.label, color = Accent)
                }
                Spacer(modifier = Modifier.height(Space.tight))
                Text(
                    text = "${stats.rangeLabel} · ${exportRows.size} transactions. " +
                        "Saved where you choose on this phone; opens in Excel or Google Sheets.",
                    style = HomeType.caption,
                    color = Mute,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
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
private fun StretchStoryCard(
    headline: String,
    detail: String?,
    hasLedger: Boolean,
    quietDays: Int,
    quietStreak: Int,
    busiestLabel: String?,
    busiestTotal: Double,
    pulse: List<PulseCell>,
    fuliza: Boolean,
    showStory: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showStory) {
            Text(headline, style = HomeType.greeting, color = Ink)
            if (detail != null) {
                Spacer(modifier = Modifier.height(Space.tight))
                Text(detail, style = HomeType.body, color = Mute)
            }
        }
        if (pulse.isNotEmpty()) {
            if (showStory) Spacer(modifier = Modifier.height(Space.block))
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
                Text(
                    text = if (day.isDue) "A regular payment is due" else "Nothing moved this day",
                    style = HomeType.body,
                    color = if (day.isDue) Accent else Mute
                )
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
        cell.isDue && cell.inRange -> Accent
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
                    if (cell.isDue) {
                        Text("due", style = HomeType.caption, color = Accent, maxLines = 1)
                    } else {
                        Text("—", style = HomeType.caption, color = Mute)
                    }
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

@Composable
private fun InsightsHero(
    rangeLabel: String,
    headline: String,
    detail: String?,
    showFigures: Boolean,
    income: Double,
    expense: Double,
    inflowLabel: String,
    outflowLabel: String,
    spendShare: Double?,
    fees: Double,
    quietStreak: Int,
    fuliza: Boolean
) {
    val soft = OnAccent.copy(alpha = 0.82f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.section)
            .clip(RoundedCornerShape(20.dp))
            .background(Accent)
            .padding(Space.card)
    ) {
        Text(rangeLabel, style = HomeType.label, color = soft)
        Spacer(modifier = Modifier.height(Space.tight))
        Text(
            text = headline,
            style = HomeType.greeting,
            color = OnAccent,
            modifier = Modifier.recordingPrivacyCover()
        )
        if (detail != null) {
            Spacer(modifier = Modifier.height(Space.tight))
            Text(detail, style = HomeType.body, color = soft)
        }
        if (showFigures) {
            Spacer(modifier = Modifier.height(Space.block))
            Row(modifier = Modifier.fillMaxWidth()) {
                HeroFigure(label = inflowLabel, amount = income, modifier = Modifier.weight(1f))
                HeroFigure(label = outflowLabel, amount = expense, modifier = Modifier.weight(1f))
            }
            if (spendShare != null) {
                Spacer(modifier = Modifier.height(Space.block))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(OnAccent.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(spendShare.toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(OnAccent)
                    )
                }
                Spacer(modifier = Modifier.height(Space.tight))
                Text(
                    text = "${(spendShare * 100).roundToInt()}% of what came in went out",
                    style = HomeType.caption,
                    color = soft,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
            if (!fuliza && fees > 0.0) {
                Text(
                    text = "Plus ${formatKes(fees)} in transaction fees",
                    style = HomeType.caption,
                    color = soft,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
        if (quietStreak >= 2) {
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = if (fuliza) "$quietStreak days without a draw" else "$quietStreak quiet days in a row",
                style = HomeType.label,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(OnAccent)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun HeroFigure(label: String, amount: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = HomeType.caption, color = OnAccent.copy(alpha = 0.82f))
        Text(
            text = formatKes(amount),
            style = HomeType.rowTitle,
            color = OnAccent,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

private enum class CostTab { REGULARS, FEES, HABITS }

@Composable
private fun CostingYouCard(
    regulars: List<RegularPayee>,
    fees: FeeSummary,
    expense: Double,
    habit: SpendingHabit?,
    onEssential: (String, Boolean) -> Unit
) {
    var tab by remember { mutableStateOf(CostTab.REGULARS) }
    var showEssential by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val watched = regulars.filterNot { it.essential }
    val essential = regulars.filter { it.essential }
    val feeShare = if (expense > 0.0) (fees.total / expense * 100).roundToInt() else null
    InsightsSection(
        icon = Icons.Outlined.Autorenew,
        title = "What keeps costing you",
        caption = "Money that leaves again and again"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.gap)
        ) {
            CostTile(
                title = "Regulars",
                value = if (watched.isEmpty()) "—" else formatKes(watched.sumOf { it.monthlyCost }),
                caption = if (watched.isEmpty()) "None yet" else "a month · ${watched.size}",
                selected = tab == CostTab.REGULARS,
                onClick = { tab = CostTab.REGULARS },
                modifier = Modifier.weight(1f)
            )
            CostTile(
                title = "Fees",
                value = formatKes(fees.total),
                caption = feeShare?.let { "$it% of spend" } ?: "this stretch",
                selected = tab == CostTab.FEES,
                onClick = { tab = CostTab.FEES },
                modifier = Modifier.weight(1f)
            )
            CostTile(
                title = "Habits",
                value = habit?.let { "${it.visits}×" } ?: "—",
                caption = habit?.name ?: "None yet",
                selected = tab == CostTab.HABITS,
                onClick = { tab = CostTab.HABITS },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(Space.block))
        when (tab) {
            CostTab.REGULARS -> {
                if (regulars.isEmpty()) {
                    Text(
                        "Pay someone on a steady rhythm three times and they show up here.",
                        style = HomeType.body,
                        color = Mute
                    )
                }
                watched.forEachIndexed { index, regular ->
                    if (index > 0) HorizontalDivider(color = Hairline, thickness = 1.dp)
                    RegularRow(
                        regular = regular,
                        today = today,
                        actionLabel = "Essential?",
                        onAction = { onEssential(regular.key, true) }
                    )
                }
                if (essential.isNotEmpty()) {
                    HorizontalDivider(color = Hairline, thickness = 1.dp)
                    TextButton(onClick = { showEssential = !showEssential }) {
                        Text(
                            text = "${essential.size} essential hidden · " +
                                if (showEssential) "hide" else "show",
                            style = HomeType.caption,
                            color = Accent
                        )
                    }
                    if (showEssential) {
                        essential.forEach { regular ->
                            RegularRow(
                                regular = regular,
                                today = today,
                                actionLabel = "Not essential",
                                onAction = { onEssential(regular.key, false) }
                            )
                        }
                    }
                }
            }
            CostTab.FEES -> {
                Text(
                    text = "${formatKes(fees.total)} went on transaction fees this stretch.",
                    style = HomeType.body,
                    color = Ink,
                    modifier = Modifier.recordingPrivacyCover()
                )
                if (feeShare != null) {
                    Spacer(modifier = Modifier.height(Space.tight))
                    Text("That is $feeShare% of what went out.", style = HomeType.caption, color = Mute)
                }
                if (fees.withdrawCount > 0) {
                    Spacer(modifier = Modifier.height(Space.tight))
                    Text(
                        text = "Cash withdrawals: ${fees.withdrawCount} · " +
                            "${formatKes(fees.withdrawFees)} in fees",
                        style = HomeType.caption,
                        color = Mute,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
            }
            CostTab.HABITS -> {
                Text(
                    text = habit?.let {
                        "${it.name}: ${it.visits} times this stretch, ${formatKes(it.total)} in all."
                    } ?: "No single place shows up three times or more this stretch.",
                    style = HomeType.body,
                    color = if (habit == null) Mute else Ink,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
    }
}

@Composable
private fun CostTile(
    title: String,
    value: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Hairline, shape)
            .background(if (selected) SoftBlue else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Text(title, style = HomeType.caption, color = if (selected) Accent else Mute, maxLines = 1)
        Spacer(modifier = Modifier.height(Space.tight))
        Text(
            text = value,
            style = HomeType.label,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover()
        )
        Text(
            text = caption,
            style = HomeType.caption,
            color = Mute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

@Composable
private fun RegularRow(
    regular: RegularPayee,
    today: LocalDate,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FaviconDisc(
            letter = regular.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            fill = avatarPastel(regular.name.hashCode()),
            tint = OnPastel
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                regular.name,
                style = HomeType.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover()
            )
            Text(regularCadenceCopy(regular, today), style = HomeType.caption, color = Mute)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatKes(regular.typicalAmount),
                style = HomeType.rowTitle,
                modifier = Modifier.recordingPrivacyCover()
            )
            TextButton(onClick = onAction) {
                Text(actionLabel, style = HomeType.caption, color = Accent)
            }
        }
    }
}

@Composable
private fun FeeTipCard(withdrawCount: Int, withdrawFees: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.section)
            .clip(RoundedCornerShape(16.dp))
            .background(ActionMetrics)
            .padding(Space.card),
        verticalAlignment = Alignment.Top
    ) {
        FaviconDisc(icon = Icons.Outlined.Lightbulb, fill = CardWhite, tint = OnPastel)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Fee tip", style = HomeType.label, color = OnPastel)
            Spacer(modifier = Modifier.height(Space.tight))
            Text(
                text = "You withdrew cash $withdrawCount times and paid ${formatKes(withdrawFees)} " +
                    "in fees. Fewer, larger withdrawals usually cost less per shilling.",
                style = HomeType.body,
                color = OnPastel,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
    }
}

private fun csvFileName(rangeLabel: String): String {
    val slug = rangeLabel.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    return "lipabill-${slug.ifEmpty { "ledger" }}.csv"
}
