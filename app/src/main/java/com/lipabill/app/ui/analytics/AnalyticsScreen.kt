package com.lipabill.app.ui.analytics

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.R
import com.lipabill.app.data.export.StatementTotals
import com.lipabill.app.data.export.canExportStatementPdf
import com.lipabill.app.data.export.statementSourceBadge
import com.lipabill.app.data.export.writeLedgerStatementPdf
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.components.PaymentRailRow
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.theme.asPillLabel
import com.lipabill.app.viewmodel.AnalyticsRange
import com.lipabill.app.viewmodel.AnalyticsSummary
import com.lipabill.app.viewmodel.EngagementViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.WalletAccount
import com.lipabill.app.viewmodel.shortPeriodSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Observable locale for Compose lint (NonObservableLocale). */
@Composable
internal fun currentJavaLocale(): Locale =
    Locale.forLanguageTag(ComposeLocale.current.toLanguageTag())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(
    viewModel: TransactionListViewModel,
    onBack: (() -> Unit)? = null,
    onOpenCheckIn: () -> Unit = {},
    onOpenTransactions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val challenge by viewModel.challenge.collectAsStateWithLifecycle()
    // Fully qualified: the `viewModel` parameter hides the Compose helper of the same name.
    val engageVm: EngagementViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val engage by engageVm.uiState.collectAsStateWithLifecycle()
    val stats = state.analytics
    val account = state.account
    val mpesa = account == WalletAccount.MPESA
    val emptyPeriod = stats.txnCount == 0
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refreshChallenge() }
    val earnedNow = stats.badges.filter { it.earned }.map { it.id }.toSet()
    LaunchedEffect(earnedNow) {
        if (earnedNow.isNotEmpty()) viewModel.recordBadges(earnedNow)
    }

    val exportRows = stats.periodTransactions
    val canExportPdf = canExportStatementPdf(stats.dayCount) &&
        stats.periodFrom != null &&
        stats.periodTo != null &&
        exportRows.isNotEmpty()
    val statementSourceLabel = statementSourceBadge(
        txs = exportRows,
        sourceFilter = state.sourceFilter,
        fuliza = account == WalletAccount.FULIZA
    )
    val statementLogo = remember(context) {
        ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            ?.toBitmap(width = 192, height = 192)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val from = stats.periodFrom ?: return@rememberLauncherForActivityResult
        val to = stats.periodTo ?: return@rememberLauncherForActivityResult
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        writeLedgerStatementPdf(
                            txs = exportRows,
                            zone = ZoneId.systemDefault(),
                            periodFrom = from,
                            periodTo = to,
                            totals = StatementTotals(
                                income = stats.incomeTotal,
                                expense = stats.expenseTotal,
                                fees = stats.fees.total,
                                incomeLabel = if (account == WalletAccount.FULIZA) {
                                    "DRAWN"
                                } else {
                                    "CAME IN"
                                },
                                expenseLabel = if (account == WalletAccount.FULIZA) {
                                    "ACCESS FEES"
                                } else {
                                    "WENT OUT"
                                }
                            ),
                            output = out,
                            logo = statementLogo,
                            sourceLabel = statementSourceLabel
                        )
                    } != null
                }.getOrDefault(false)
            }
            Toast.makeText(
                context,
                if (written) "Saved PDF statement" else "Export failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val periodPill: @Composable () -> Unit = {
        PeriodPill(
            stats = stats,
            onRange = viewModel::setAnalyticsRange,
            onYear = viewModel::setAnalyticsYear,
            onCustom = { showDatePicker = true }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(account.metricsTitle, style = HomeType.greeting) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        periodPill()
                        Spacer(modifier = Modifier.width(Space.page))
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
                .padding(horizontal = Space.page),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (onBack == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.block),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        account.metricsTitle,
                        style = HomeType.greeting,
                        modifier = Modifier.weight(1f)
                    )
                    periodPill()
                }
            }

            PaymentRailRow(
                account = account,
                showFuliza = state.showFuliza,
                sources = state.availableSources,
                selectedSource = state.sourceFilter,
                onSelectAll = viewModel::selectAllLedgerRails,
                onSelectSource = viewModel::selectLedgerSource,
                onSelectFuliza = viewModel::selectFulizaLedger
            )

            HeroCard(
                stats = stats,
                account = account,
                showFigures = stats.hasLedger && !emptyPeriod,
                fulizaOutstanding = if (state.showFuliza) state.fulizaOutstanding else null,
                fulizaDue = state.fulizaDue
            )

            if (stats.hasLedger && mpesa &&
                (stats.regulars.isNotEmpty() || stats.fees.total > 0.0 || stats.habit != null)
            ) {
                CostingYouCard(
                    regulars = stats.regulars,
                    fees = stats.fees,
                    expense = stats.expenseTotal,
                    habit = stats.habit,
                    onEssential = viewModel::setPayeeEssential,
                    onWatch = viewModel::setPayeeWatched
                )
            }

            if (mpesa && stats.fees.withdrawCount >= 3 && stats.fees.withdrawFees > 0.0) {
                FeeTipCard(
                    withdrawCount = stats.fees.withdrawCount,
                    withdrawFees = stats.fees.withdrawFees,
                    onSee = {
                        viewModel.setTypeFilter(TransactionType.WITHDRAW)
                        onOpenTransactions()
                    }
                )
            }

            if (stats.hasLedger && mpesa && (challenge != null || stats.habit != null)) {
                ChallengeCard(
                    challenge = challenge,
                    habit = stats.habit,
                    quietStreak = stats.quietStreak,
                    onStart = viewModel::startSkipChallenge,
                    onCheckIn = onOpenCheckIn
                )
            }

            if (stats.hasLedger) {
                RhythmCard(stats = stats, fuliza = !mpesa)
            }

            if (stats.hasLedger && !emptyPeriod) {
                WhereItWentCard(
                    rows = stats.byType.filter { !mpesa || it.type.isOutgoing() },
                    selected = state.typeFilter,
                    onSelect = viewModel::setTypeFilter,
                    onOpenTransactions = onOpenTransactions
                )
                if (stats.topPeople.isNotEmpty() || stats.topBusinesses.isNotEmpty()) {
                    WhoGotTheMostCard(
                        people = stats.topPeople,
                        businesses = stats.topBusinesses,
                        peopleTotal = stats.peopleSpendTotal,
                        businessTotal = stats.businessSpendTotal
                    )
                }
            }

            if (stats.hasLedger && mpesa) {
                RecapCard(
                    checkInDone = engage.checkInDone,
                    finding = stats.keptHeadline
                        ?: stats.habit?.let { "${it.name} came up ${it.visits} times." }
                        ?: stats.storyHeadline,
                    badges = stats.badges,
                    onCheckIn = onOpenCheckIn
                )
            }

            if (canExportPdf) {
                ExportBlock(
                    rangeLabel = stats.rangeLabel,
                    onExport = { exportLauncher.launch(pdfFileName(stats.periodFrom, stats.periodTo)) }
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

// region Header

@Composable
private fun PeriodPill(
    stats: AnalyticsSummary,
    onRange: (AnalyticsRange) -> Unit,
    onYear: (Int) -> Unit,
    onCustom: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Hairline, RoundedCornerShape(22.dp))
                .background(CardWhite)
                .clickable(role = Role.Button, onClickLabel = "Change period") { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = shortRangeLabel(stats),
                style = HomeType.label.asPillLabel(),
                color = Ink,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                AnalyticsRange.DAYS_7 to "Last 7 days",
                AnalyticsRange.DAYS_30 to "Last 30 days",
                AnalyticsRange.DAYS_90 to "Last 90 days",
                AnalyticsRange.THIS_YEAR to "This year",
                AnalyticsRange.ALL to "All time"
            ).forEach { (range, label) ->
                DropdownMenuItem(
                    text = {
                        Text(label, fontWeight = if (stats.range == range) FontWeight.SemiBold else null)
                    },
                    onClick = {
                        open = false
                        onRange(range)
                    }
                )
            }
            val thisYear = LocalDate.now().year
            stats.availableYears.filter { it != thisYear }.forEach { year ->
                val on = stats.range == AnalyticsRange.YEAR && stats.selectedYear == year
                DropdownMenuItem(
                    text = { Text("Year $year", fontWeight = if (on) FontWeight.SemiBold else null) },
                    onClick = {
                        open = false
                        onYear(year)
                    }
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        "Custom dates…",
                        fontWeight = if (stats.range == AnalyticsRange.CUSTOM) FontWeight.SemiBold else null
                    )
                },
                onClick = {
                    open = false
                    onCustom()
                }
            )
        }
    }
}

private fun shortRangeLabel(stats: AnalyticsSummary): String {
    val from = stats.periodFrom
    val to = stats.periodTo
    return when (stats.range) {
        AnalyticsRange.DAYS_7,
        AnalyticsRange.DAYS_30,
        AnalyticsRange.DAYS_90,
        AnalyticsRange.CUSTOM ->
            if (from != null && to != null) shortPeriodSpan(from, to) else stats.rangeLabel
        AnalyticsRange.THIS_YEAR -> "This year"
        AnalyticsRange.YEAR -> stats.selectedYear?.toString() ?: "Year"
        AnalyticsRange.ALL -> "All time"
    }
}

// endregion

private fun pdfFileName(from: LocalDate?, to: LocalDate?): String {
    if (from == null || to == null) return "lipabill-statement.pdf"
    val slug = shortPeriodSpan(from, to)
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return "lipabill-statement-${slug.ifEmpty { "period" }}.pdf"
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

