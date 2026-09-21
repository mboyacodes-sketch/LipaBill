package com.lipabill.app.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.LipaBillApp
import com.lipabill.app.ui.components.BalanceAmountRow
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.viewmodel.AnalyticsPoint
import com.lipabill.app.viewmodel.AnalyticsRange
import com.lipabill.app.viewmodel.TransactionListViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(
    viewModel: TransactionListViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val stats = state.analytics
    val net = stats.incomeTotal - stats.expenseTotal
    var showDatePicker by remember { mutableStateOf(false) }
    val app = LocalContext.current.applicationContext as LipaBillApp
    val alwaysShowBalance by app.alwaysShowBalance.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text("Metrics & Analytics", style = HomeType.greeting) },
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
                Text("Metrics & Analytics", style = HomeType.greeting)
                Spacer(modifier = Modifier.height(Space.block))
            }

            Text("Period", style = HomeType.section)
            Spacer(modifier = Modifier.height(Space.block))
            RangeChipRow(
                selected = stats.range,
                onSelect = { viewModel.setAnalyticsRange(it) },
                onCustom = { showDatePicker = true }
            )

            if (stats.range == AnalyticsRange.YEAR && stats.availableYears.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Space.gap))
                YearChipRow(
                    years = stats.availableYears,
                    selectedYear = stats.selectedYear,
                    onSelect = { viewModel.setAnalyticsYear(it) }
                )
            }

            Spacer(modifier = Modifier.height(Space.gap))
            Text(stats.rangeLabel, style = HomeType.caption, color = Mute)

            Spacer(modifier = Modifier.height(Space.block))
            Text("Overview", style = HomeType.section)
            Spacer(modifier = Modifier.height(Space.block))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardWhite)
                    .padding(Space.card)
            ) {
                Text("Available balance", style = HomeType.label, color = Mute)
                Spacer(modifier = Modifier.height(Space.gap))
                BalanceAmountRow(
                    balance = state.latestBalance,
                    alwaysShow = alwaysShowBalance,
                    amountStyle = HomeType.amount,
                    eyeTint = Mute
                )
            }
            Spacer(modifier = Modifier.height(Space.block))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.block)
            ) {
                MetricStatCard(
                    title = "Income",
                    amount = stats.incomeTotal,
                    tint = Income,
                    icon = Icons.Outlined.SouthWest,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Expenses",
                    amount = stats.expenseTotal,
                    tint = Expense,
                    icon = Icons.Outlined.NorthEast,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(Space.block))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.block)
            ) {
                MetricTile(
                    title = "Net",
                    value = if (net < 0) "−${formatKes(abs(net))}" else formatKes(net),
                    valueColor = if (net >= 0) Income else Expense,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    title = "Transactions",
                    value = stats.txnCount.toString(),
                    valueColor = Ink,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(Space.section))
            Text("Cash flow", style = HomeType.section)
            Spacer(modifier = Modifier.height(Space.tight))
            Text("Income vs expenses over time", style = HomeType.caption, color = Mute)
            Spacer(modifier = Modifier.height(Space.block))
            CashFlowChartCard(series = stats.series)

            Spacer(modifier = Modifier.height(Space.section))
            Text("By type", style = HomeType.section)
            Spacer(modifier = Modifier.height(Space.block))

            if (stats.byType.isEmpty()) {
                Text(
                    text = "No transactions in this period.",
                    style = HomeType.body,
                    color = Mute
                )
            } else {
                TypeBarChartCard(
                    rows = stats.byType.map { it.label to it.total },
                    maxTotal = stats.byType.maxOf { it.total }
                )
                Spacer(modifier = Modifier.height(Space.block))
                stats.byType.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.gap)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardWhite)
                            .padding(horizontal = Space.card, vertical = Space.cardH),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(row.label, style = HomeType.rowTitle)
                            Text(
                                "${row.count} txn${if (row.count == 1) "" else "s"}",
                                style = HomeType.caption,
                                color = Mute
                            )
                        }
                        Text(formatKes(row.total), style = HomeType.rowTitle)
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
private fun RangeChipRow(
    selected: AnalyticsRange,
    onSelect: (AnalyticsRange) -> Unit,
    onCustom: () -> Unit
) {
    val chips = listOf(
        AnalyticsRange.DAYS_7 to "7D",
        AnalyticsRange.DAYS_30 to "30D",
        AnalyticsRange.DAYS_90 to "90D",
        AnalyticsRange.THIS_YEAR to "This year",
        AnalyticsRange.YEAR to "Year",
        AnalyticsRange.ALL to "All",
        AnalyticsRange.CUSTOM to "Custom"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        chips.forEach { (range, label) ->
            val isSelected = selected == range
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (range == AnalyticsRange.CUSTOM) onCustom()
                    else onSelect(range)
                },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (range == AnalyticsRange.CUSTOM) {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(Space.tight))
                        }
                        Text(label, style = HomeType.label)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = CardWhite,
                    selectedLeadingIconColor = CardWhite,
                    containerColor = CardWhite,
                    labelColor = Ink
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Hairline,
                    selectedBorderColor = Accent
                )
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
            val isSelected = year == selectedYear
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(year) },
                label = { Text(year.toString(), style = HomeType.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Accent,
                    selectedLabelColor = CardWhite,
                    containerColor = CardWhite,
                    labelColor = Ink
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = Hairline,
                    selectedBorderColor = Accent
                )
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
private fun CashFlowChartCard(series: List<AnalyticsPoint>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(Space.card)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.chip)
        ) {
            LegendDot(color = Income, label = "Income")
            LegendDot(color = Expense, label = "Expenses")
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
            IncomeExpenseLineChart(
                series = series,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
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
private fun IncomeExpenseLineChart(
    series: List<AnalyticsPoint>,
    modifier: Modifier = Modifier
) {
    val maxY = remember(series) {
        max(series.maxOf { max(it.income, it.expense) }, 1.0)
    }
    Canvas(modifier = modifier) {
        val leftPad = 8.dp.toPx()
        val rightPad = 8.dp.toPx()
        val topPad = 12.dp.toPx()
        val bottomPad = 8.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad
        val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

        // grid
        for (i in 0..3) {
            val y = topPad + chartH * i / 3f
            drawLine(
                color = Hairline,
                start = Offset(leftPad, y),
                end = Offset(leftPad + chartW, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
        }

        fun xAt(index: Int): Float {
            if (series.size == 1) return leftPad + chartW / 2f
            return leftPad + chartW * index / (series.size - 1).toFloat()
        }

        fun yAt(value: Double): Float {
            val t = (value / maxY).toFloat().coerceIn(0f, 1f)
            return topPad + chartH * (1f - t)
        }

        fun drawSeries(values: List<Double>, color: Color) {
            if (values.isEmpty()) return
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = xAt(i)
                val y = yAt(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            values.forEachIndexed { i, v ->
                if (series.size <= 14 || i == 0 || i == values.lastIndex || i % 2 == 0) {
                    drawCircle(
                        color = color,
                        radius = 3.dp.toPx(),
                        center = Offset(xAt(i), yAt(v))
                    )
                }
            }
        }

        drawSeries(series.map { it.income }, Income)
        drawSeries(series.map { it.expense }, Expense)
    }
}

@Composable
private fun TypeBarChartCard(
    rows: List<Pair<String, Double>>,
    maxTotal: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(Space.card),
        verticalArrangement = Arrangement.spacedBy(Space.block)
    ) {
        rows.take(6).forEach { (label, total) ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, style = HomeType.label, color = Mute)
                    Text(formatKes(total), style = HomeType.caption, color = Ink)
                }
                Spacer(modifier = Modifier.height(Space.gap))
                val fraction = if (maxTotal <= 0.0) 0f else (total / maxTotal).toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Hairline)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Ink)
                    )
                }
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(Space.card)
    ) {
        Text(title, style = HomeType.label, color = Mute)
        Spacer(modifier = Modifier.height(Space.gap))
        Text(value, style = HomeType.amount, color = valueColor)
    }
}

@Composable
private fun MetricStatCard(
    title: String,
    amount: Double,
    tint: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(Space.card)
    ) {
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
            color = tint
        )
    }
}
