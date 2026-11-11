package com.lipabill.app.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.LipaChipHeight
import com.lipabill.app.ui.LipaPillButton
import com.lipabill.app.ui.components.CounterpartyAvatar
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.LabelBlue
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.OnPastel
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.theme.asPillLabel
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.viewmodel.AnalyticsRange
import com.lipabill.app.viewmodel.AnalyticsSummary
import com.lipabill.app.viewmodel.ChallengeCardKind
import com.lipabill.app.viewmodel.CounterpartySpend
import com.lipabill.app.viewmodel.DayFlow
import com.lipabill.app.viewmodel.FeeSummary
import com.lipabill.app.viewmodel.InsightBadge
import com.lipabill.app.viewmodel.InsightChallenge
import com.lipabill.app.viewmodel.PeriodBar
import com.lipabill.app.viewmodel.PulseCell
import com.lipabill.app.viewmodel.RegularPayee
import com.lipabill.app.viewmodel.SpendingHabit
import com.lipabill.app.viewmodel.TypeBreakdown
import com.lipabill.app.viewmodel.WalletAccount
import com.lipabill.app.viewmodel.busiestDayLabel
import com.lipabill.app.viewmodel.calendarAmountLabel
import com.lipabill.app.viewmodel.dueCopy
import com.lipabill.app.viewmodel.formatPeriodSpan
import com.lipabill.app.viewmodel.fulizaDueCopy
import com.lipabill.app.viewmodel.regularCadenceCopy
import com.lipabill.app.viewmodel.spendShareOfIncome
import com.lipabill.app.viewmodel.upcomingDue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal val CardShape = RoundedCornerShape(20.dp)

/** Amber for days that ended down despite money coming in; differs from red in hue and lightness. */
internal val NetDown = Color(0xFFD97706)

/** Calendar fill per kind of day. Each differs in hue and lightness so they read apart. */
@Composable
internal fun DayFlow.fill(): Color = when (this) {
    DayFlow.IN_ONLY -> Income.copy(alpha = 0.18f)
    DayFlow.OUT_ONLY -> Expense.copy(alpha = 0.16f)
    DayFlow.NET_UP -> Accent.copy(alpha = 0.22f)
    DayFlow.NET_DOWN -> NetDown.copy(alpha = 0.26f)
    DayFlow.NONE -> Color.Transparent
}

// region Hero

@Composable
internal fun HeroCard(
    stats: AnalyticsSummary,
    account: WalletAccount,
    showFigures: Boolean,
    fulizaOutstanding: Double?,
    fulizaDue: LocalDate?
) {
    val soft = OnAccent.copy(alpha = 0.82f)
    val mpesa = account == WalletAccount.MPESA
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Accent)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stats.heroLabel.ifEmpty { stats.rangeLabel }.uppercase(currentJavaLocale()),
            style = HomeType.caption,
            color = soft,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stats.keptHeadline ?: stats.storyHeadline,
            style = HomeType.greeting,
            color = OnAccent,
            modifier = Modifier.recordingPrivacyCover()
        )
        val detail = if (stats.keptHeadline != null) null else stats.storyDetail
        if (detail != null) {
            Text(detail, style = HomeType.body, color = soft)
        }
        if (fulizaOutstanding != null && fulizaOutstanding > 0.0) {
            val due = fulizaDue?.let { " · ${fulizaDueCopy(it, LocalDate.now())}" }.orEmpty()
            Text(
                text = (if (mpesa) "Fuliza outstanding " else "Outstanding ") + formatKsh(fulizaOutstanding) + due,
                style = HomeType.label,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(OnAccent)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .recordingPrivacyCover()
            )
        }
        if (showFigures) {
            Row(modifier = Modifier.fillMaxWidth()) {
                HeroFigure(
                    label = if (mpesa) "Came in" else account.inflowLabel,
                    amount = stats.incomeTotal,
                    modifier = Modifier.weight(1f)
                )
                HeroFigure(
                    label = if (mpesa) "Went out" else account.outflowLabel,
                    amount = stats.expenseTotal,
                    modifier = Modifier.weight(1f)
                )
            }
            val share = if (mpesa) spendShareOfIncome(stats.expenseTotal, stats.incomeTotal) else null
            if (share != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(OnAccent.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(OnAccent)
                        )
                    }
                    Text(
                        text = "${(share * 100).roundToInt()}% of what came in has gone out · " +
                            "reversals netted, fees included",
                        style = HomeType.caption,
                        color = soft,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
            }
        }
    }
}

@Composable
internal fun HeroFigure(label: String, amount: Double, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = HomeType.caption, color = OnAccent.copy(alpha = 0.82f))
        Text(
            text = formatKsh(amount),
            style = HomeType.rowTitle,
            color = OnAccent,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

// endregion

// region What keeps costing you

internal enum class CostTab { REGULARS, FEES, HABITS }

internal const val REGULARS_SHOWN = 5

@Composable
internal fun CostingYouCard(
    regulars: List<RegularPayee>,
    fees: FeeSummary,
    expense: Double,
    habit: SpendingHabit?,
    onEssential: (String, Boolean) -> Unit,
    onWatch: (String, Boolean) -> Unit
) {
    var tab by remember { mutableStateOf(CostTab.REGULARS) }
    var showEssential by remember { mutableStateOf(false) }
    var showAllRegulars by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val shown = regulars.filterNot { it.essential }
    val essential = regulars.filter { it.essential }
    val feeShare = if (expense > 0.0) (fees.total / expense * 100).roundToInt() else null
    InsightCard(title = "What keeps costing you", caption = "Money that leaves again and again") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CostTile(
                title = "Regulars",
                value = when {
                    shown.isNotEmpty() -> formatKsh(shown.sumOf { it.monthlyCost })
                    essential.isNotEmpty() -> formatKsh(essential.sumOf { it.monthlyCost })
                    else -> "—"
                },
                caption = when {
                    shown.isNotEmpty() ->
                        "a month · ${shown.size} payee${if (shown.size == 1) "" else "s"}"
                    essential.isNotEmpty() ->
                        "${essential.size} essential · all-time"
                    else -> "None yet · all-time"
                },
                selected = tab == CostTab.REGULARS,
                onClick = { tab = CostTab.REGULARS },
                modifier = Modifier.weight(1f)
            )
            CostTile(
                title = "Fees",
                value = formatKsh(fees.total),
                caption = feeShare?.let { "$it% of spend" } ?: "this period",
                selected = tab == CostTab.FEES,
                onClick = { tab = CostTab.FEES },
                modifier = Modifier.weight(1f)
            )
            CostTile(
                title = "Habits",
                value = habit?.let { "${it.visits} visits" } ?: "—",
                caption = habit?.name ?: "None yet · period",
                selected = tab == CostTab.HABITS,
                onClick = { tab = CostTab.HABITS },
                modifier = Modifier.weight(1f)
            )
        }
        when (tab) {
            CostTab.REGULARS -> Column {
                if (regulars.isEmpty()) {
                    Text(
                        "Pay the same bill or shop twice on a steady rhythm and they show up here. " +
                            "Regulars look across your whole history, not just this period.",
                        style = HomeType.body,
                        color = Mute
                    )
                } else if (shown.isEmpty() && essential.isNotEmpty() && !showEssential) {
                    Text(
                        "All regulars are marked essential. Tap below to review them.",
                        style = HomeType.body,
                        color = Mute
                    )
                }
                // Most expensive first; the top five keep the card short.
                val listed = if (showAllRegulars) shown else shown.take(REGULARS_SHOWN)
                listed.forEach { regular ->
                    HorizontalDivider(color = Hairline, thickness = 1.dp)
                    RegularRow(regular, today, onEssential, onWatch)
                }
                if (shown.size > REGULARS_SHOWN) {
                    HorizontalDivider(color = Hairline, thickness = 1.dp)
                    TextButton(onClick = { showAllRegulars = !showAllRegulars }) {
                        Text(
                            if (showAllRegulars) "Show top $REGULARS_SHOWN" else "Show all ${shown.size} regulars",
                            style = HomeType.label,
                            color = Accent
                        )
                    }
                }
                if (essential.isNotEmpty()) {
                    HorizontalDivider(color = Hairline, thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable(role = Role.Button) { showEssential = !showEssential },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${essential.size} essential payee${if (essential.size == 1) "" else "s"} " +
                                "hidden (${essential.joinToString { it.name }}) · ",
                            style = HomeType.caption,
                            color = Mute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .recordingPrivacyCover()
                        )
                        Text(if (showEssential) "hide" else "show", style = HomeType.caption, color = Accent)
                    }
                    if (showEssential) {
                        essential.forEach { regular ->
                            HorizontalDivider(color = Hairline, thickness = 1.dp)
                            RegularRow(regular, today, onEssential, onWatch)
                        }
                    }
                }
            }
            CostTab.FEES -> Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
                Text(
                    text = "${formatKsh(fees.total)} went on transaction fees this period.",
                    style = HomeType.body,
                    color = Ink,
                    modifier = Modifier.recordingPrivacyCover()
                )
                if (feeShare != null) {
                    Text("That is $feeShare% of what went out.", style = HomeType.caption, color = Mute)
                }
                if (fees.withdrawCount > 0) {
                    Text(
                        text = "Cash withdrawals: ${fees.withdrawCount} · ${formatKsh(fees.withdrawFees)} in fees",
                        style = HomeType.caption,
                        color = Mute,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
            }
            CostTab.HABITS -> Text(
                text = habit?.let {
                    "${it.name}: ${it.visits} times this period, ${formatKsh(it.total)} in all."
                } ?: "No single place shows up often enough in this period.",
                style = HomeType.body,
                color = if (habit == null) Mute else Ink,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
    }
}

@Composable
internal fun CostTile(
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
            .heightIn(min = 88.dp)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Hairline, shape)
            .background(if (selected) SoftBlue else CardWhite)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title.uppercase(currentJavaLocale()),
            style = HomeType.caption,
            color = if (selected) Accent else Mute,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            maxLines = 1
        )
        Text(
            text = value,
            style = HomeType.label,
            color = Ink,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover()
        )
        Text(
            text = caption,
            style = HomeType.caption,
            color = Mute,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

@Composable
internal fun RegularRow(
    regular: RegularPayee,
    today: LocalDate,
    onEssential: (String, Boolean) -> Unit,
    onWatch: (String, Boolean) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Payee options") { menu = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialDisc(name = regular.name, fill = SoftBlue, tint = Accent)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    regular.name,
                    style = HomeType.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.recordingPrivacyCover()
                )
                Text(regularCadenceCopy(regular, today), style = HomeType.caption, color = Mute)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatKsh(regular.typicalAmount),
                    style = HomeType.rowTitle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.recordingPrivacyCover()
                )
                when {
                    regular.essential -> Pill("Essential")
                    regular.watched -> Pill("Watching")
                    else -> Text(
                        "Watch it",
                        style = HomeType.caption,
                        color = Accent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { onWatch(regular.key, true) }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (regular.watched) "Stop watching" else "Watch it") },
                onClick = {
                    menu = false
                    onWatch(regular.key, !regular.watched)
                }
            )
            DropdownMenuItem(
                text = { Text(if (regular.essential) "Not essential" else "Mark essential") },
                onClick = {
                    menu = false
                    onEssential(regular.key, !regular.essential)
                }
            )
        }
    }
}

@Composable
internal fun Pill(text: String) {
    Text(
        text = text,
        style = HomeType.caption,
        color = Accent,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(SoftBlue)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

// endregion

// region Tip and challenge

@Composable
internal fun FeeTipCard(withdrawCount: Int, withdrawFees: Double, onSee: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(ActionMetrics)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
        ) {
            Icon(
                Icons.Outlined.Lightbulb,
                contentDescription = null,
                tint = OnPastel,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "FEE TIP",
                style = HomeType.caption,
                color = OnPastel,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "You withdrew cash $withdrawCount times this period and paid " +
                    "${formatKsh(withdrawFees)} in fees. Fewer, larger withdrawals usually cost less per shilling.",
                style = HomeType.body,
                color = OnPastel,
                modifier = Modifier.recordingPrivacyCover()
            )
            Box(
                modifier = Modifier
                    .height(LipaChipHeight)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White)
                    .clickable(role = Role.Button, onClick = onSee)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "See the $withdrawCount withdrawals",
                    style = HomeType.label.asPillLabel(),
                    color = OnPastel,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun ChallengeCard(
    challenge: InsightChallenge?,
    habit: SpendingHabit?,
    quietStreak: Int,
    onStart: (String) -> Unit,
    onCheckIn: () -> Unit
) {
    InsightCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = when (challenge?.kind) {
                ChallengeCardKind.ACTIVE -> "Cost cutter · day ${challenge.day} of ${challenge.totalDays}"
                ChallengeCardKind.COMPLETED -> "Cost cutter · done"
                ChallengeCardKind.BROKEN -> "Cost cutter · ended"
                else -> "Cost cutter · 7 days"
            }
            Text(
                label.uppercase(currentJavaLocale()),
                style = HomeType.caption,
                color = Accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.weight(1f)
            )
            if (quietStreak >= 2) {
                Text(
                    "$quietStreak quiet days",
                    style = HomeType.caption,
                    color = Income,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Income.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                )
            }
        }
        if (challenge == null) {
            if (habit == null) return@InsightCard
            Text(
                "Skip ${habit.name} this week",
                style = HomeType.section,
                color = Ink,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "You paid them ${habit.visits} times this period, ${formatKsh(habit.total)} in all. " +
                    "Seven days off keeps that money in your wallet.",
                style = HomeType.body,
                color = Mute,
                modifier = Modifier.recordingPrivacyCover()
            )
            PrimaryAction(text = "Start", onClick = { onStart(habit.name) })
            return@InsightCard
        }
        Text(challenge.title, style = HomeType.section, color = Ink, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(challenge.totalDays) { index ->
                val date = challenge.startDate.plusDays(index.toLong())
                val done = when (challenge.kind) {
                    ChallengeCardKind.COMPLETED -> true
                    ChallengeCardKind.ACTIVE -> index < challenge.day - 1
                    else -> false
                }
                val today = challenge.kind == ChallengeCardKind.ACTIVE && index == challenge.day - 1
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (done) Accent else if (today) CardWhite else Canvas)
                        .then(if (today) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp)) else Modifier)
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, currentJavaLocale()),
                        style = HomeType.caption,
                        fontWeight = FontWeight.Bold,
                        color = if (done) OnAccent else if (today) Accent else Mute
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        challenge.kind == ChallengeCardKind.BROKEN -> "A payment landed"
                        challenge.kept == null -> "Still clear"
                        else -> "Kept so far"
                    },
                    style = HomeType.caption,
                    color = Mute
                )
                val kept = challenge.kept
                if (challenge.kind != ChallengeCardKind.BROKEN && kept != null) {
                    Text(
                        formatKsh(kept),
                        style = HomeType.rowTitle,
                        color = Income,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
            }
            when {
                challenge.kind == ChallengeCardKind.ACTIVE -> PrimaryAction(text = "Check in", onClick = onCheckIn)
                habit != null -> PrimaryAction(text = "Start again", onClick = { onStart(habit.name) })
            }
        }
    }
}

@Composable
internal fun PrimaryAction(text: String, onClick: () -> Unit) {
    LipaPillButton(
        label = text,
        onClick = onClick,
        maxLines = 1
    )
}

// endregion

// region Rhythm: day grid or month bars

@Composable
internal fun RhythmCard(stats: AnalyticsSummary, fuliza: Boolean) {
    val span = stats.periodFrom?.let { from ->
        stats.periodTo?.let { to -> formatPeriodSpan(from, to) }
    }
    val title = when {
        stats.pulse.isNotEmpty() && stats.range == AnalyticsRange.DAYS_7 ->
            span?.let { "Your week · $it" } ?: "Your week, day by day"
        stats.pulse.isNotEmpty() ->
            span?.let { "Day by day · $it" } ?: "Day by day"
        stats.periodBars.firstOrNull()?.label?.length == 4 -> "Year by year"
        else -> "Month by month"
    }
    val caption = stats.busiestLabel?.let {
        "Busiest: $it, ${formatKsh(stats.busiestTotal)} ${if (fuliza) "drawn" else "out"}"
    } ?: "${stats.quietDays} days with no money out"
    val cells = stats.pulse
    var selectedDate by remember(cells) {
        mutableStateOf(
            (
                cells.firstOrNull { it.isToday && it.inRange }
                    ?: cells.lastOrNull { it.inRange && it.hasOutflow }
                    ?: cells.lastOrNull { it.inRange }
                )?.date
        )
    }
    val today = LocalDate.now()
    val comingUp = if (fuliza) emptyList() else upcomingDue(stats.regulars, today)
    InsightCard(title = title, caption = caption) {
        if (cells.isNotEmpty()) {
            PulseGrid(
                cells = cells,
                fuliza = fuliza,
                selectedDate = selectedDate,
                onSelect = { selectedDate = it }
            )
            if (fuliza) {
                LegendSwatch(fill = DayFlow.OUT_ONLY.fill(), border = null, label = "Drawn")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LegendSwatch(fill = DayFlow.IN_ONLY.fill(), border = null, label = "Only in")
                        LegendSwatch(fill = DayFlow.OUT_ONLY.fill(), border = null, label = "Only out")
                        LegendSwatch(fill = CardWhite, border = Accent, label = "Due")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LegendSwatch(fill = DayFlow.NET_UP.fill(), border = null, label = "Both, net up")
                        LegendSwatch(fill = DayFlow.NET_DOWN.fill(), border = null, label = "Both, net down")
                    }
                }
            }
        } else if (stats.periodBars.isNotEmpty()) {
            PeriodBars(bars = stats.periodBars, fuliza = fuliza)
        }
        if (comingUp.isNotEmpty()) {
            ComingUp(
                regulars = comingUp,
                today = today,
                onSelect = { date ->
                    if (cells.any { it.date == date && it.inRange }) selectedDate = date
                }
            )
        }
    }
}

/** Regular payments due soon or overdue, so the "due" marks on the calendar say what they are. */
@Composable
internal fun ComingUp(
    regulars: List<RegularPayee>,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        HorizontalDivider(color = Hairline, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Coming up", style = HomeType.rowTitle, color = Ink)
        Text(
            "Regular payments, from how often you have paid them.",
            style = HomeType.caption,
            color = Mute
        )
        regulars.forEach { regular ->
            val overdue = regular.nextDue.isBefore(today)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Show on calendar") { onSelect(regular.nextDue) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InitialDisc(name = regular.name, fill = SoftBlue, tint = Ink)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        regular.name,
                        style = HomeType.rowTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                    Text(
                        dueCopy(regular.nextDue, today) + (if (regular.essential) " · Essential" else ""),
                        style = HomeType.caption,
                        color = if (overdue) Expense else Mute
                    )
                }
                Text(
                    "~" + formatKsh(regular.typicalAmount),
                    style = HomeType.rowTitle,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
    }
}

@Composable
internal fun LegendSwatch(fill: Color, border: Color?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(fill)
                .then(if (border != null) Modifier.border(2.dp, border, CircleShape) else Modifier)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = HomeType.caption, color = Mute)
    }
}

@Composable
internal fun PulseGrid(
    cells: List<PulseCell>,
    fuliza: Boolean,
    selectedDate: LocalDate?,
    onSelect: (LocalDate) -> Unit
) {
    val selected = cells.firstOrNull { it.date == selectedDate }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(
                    text = day,
                    style = HomeType.caption,
                    color = Mute,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell ->
                    PulseDayCell(
                        cell = cell,
                        selected = cell.date == selected?.date,
                        onSelect = { if (cell.inRange) onSelect(cell.date) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
    val day = selected
    if (day != null && day.inRange) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (day.isToday) "Today" else busiestDayLabel(day.date),
                style = HomeType.caption,
                color = Mute
            )
            if (day.hasInflow) {
                Text(
                    "+${formatKsh(day.inflow)} received",
                    style = HomeType.body,
                    color = Income,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
            if (day.hasOutflow) {
                Text(
                    "−${formatKsh(day.outflow)} ${if (fuliza) "drawn" else "out"}",
                    style = HomeType.body,
                    color = Expense,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
            if (day.hasInflow && day.hasOutflow) {
                val up = day.net >= 0.0
                Text(
                    "Net ${if (up) "+" else "−"}${formatKsh(abs(day.net))}",
                    style = HomeType.body,
                    fontWeight = FontWeight.SemiBold,
                    color = if (up) Income else Expense,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
            if (!day.hasInflow && !day.hasOutflow && !day.isDue) {
                Text("Nothing moved this day", style = HomeType.body, color = Mute)
            }
            day.due.forEach { regular ->
                Text(
                    "Due: ${regular.name} · ~${formatKsh(regular.typicalAmount)} " +
                        regular.cadence.label.lowercase(Locale.UK),
                    style = HomeType.body,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.recordingPrivacyCover()
                )
            }
        }
    }
}

@Composable
internal fun PulseDayCell(
    cell: PulseCell,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    val fill = when {
        !cell.inRange -> Color.Transparent
        cell.flow != DayFlow.NONE -> cell.flow.fill()
        cell.isDue || cell.isToday -> CardWhite
        else -> Canvas
    }
    val outline = when {
        !cell.inRange -> null
        selected || cell.isToday -> Ink
        cell.isDue -> Accent
        else -> null
    }
    Column(
        modifier = modifier
            .height(56.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (outline != null) Modifier.border(if (selected) 2.dp else 1.5.dp, outline, shape) else Modifier
            )
            .clickable(enabled = cell.inRange, onClick = onSelect),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = HomeType.caption,
            fontWeight = FontWeight.SemiBold,
            color = if (cell.inRange) Ink else Hairline
        )
        if (cell.inRange) {
            val received = calendarAmountLabel(cell.inflow)
            val spent = calendarAmountLabel(cell.outflow)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.recordingPrivacyCover()
            ) {
                if (received.isNotEmpty()) {
                    Text(
                        text = "+$received",
                        style = HomeType.caption,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Income,
                        maxLines = 1
                    )
                }
                if (spent.isNotEmpty()) {
                    Text(
                        text = "−$spent",
                        style = HomeType.caption,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Expense,
                        maxLines = 1
                    )
                }
                if (received.isEmpty() && spent.isEmpty() && cell.isDue) {
                    Text(
                        text = "due",
                        style = HomeType.caption,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun PeriodBars(bars: List<PeriodBar>, fuliza: Boolean) {
    val max = bars.maxOfOrNull { it.outflow }?.takeIf { it > 0.0 } ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        bars.forEach { bar ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bar.label, style = HomeType.caption, color = Mute, modifier = Modifier.width(52.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Canvas)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((bar.outflow / max).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Expense)
                    )
                }
                Text(
                    text = formatKsh(bar.outflow),
                    style = HomeType.caption,
                    color = Ink,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(96.dp)
                        .recordingPrivacyCover()
                )
            }
        }
        Text(
            if (fuliza) "Drawn in each period" else "Money out in each period, fees included",
            style = HomeType.caption,
            color = Mute
        )
    }
}

// endregion

// region Where it went and people

@Composable
internal fun WhereItWentCard(
    rows: List<TypeBreakdown>,
    selected: TransactionType?,
    onSelect: (TransactionType?) -> Unit,
    onOpenTransactions: () -> Unit
) {
    if (rows.isEmpty()) return
    val max = rows.maxOf { it.total }.takeIf { it > 0.0 } ?: 1.0
    InsightCard(title = "Where it went", caption = "Tap a row to focus your transactions on it") {
        rows.forEach { row ->
            val on = row.type == selected
            val dimmed = selected != null && !on
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onSelect(if (on) null else row.type) }
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row {
                    Text(
                        plainTypeLabel(row.type, row.label),
                        style = HomeType.label,
                        color = if (dimmed) Mute else Ink,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatKsh(row.total),
                        style = HomeType.label,
                        fontWeight = FontWeight.Bold,
                        color = if (dimmed) Mute else Ink,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Canvas)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((row.total / max).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (dimmed) LabelBlue else Accent)
                    )
                }
            }
        }
        if (selected != null) {
            TextButton(onClick = onOpenTransactions) {
                Text("Show these in transactions", style = HomeType.label, color = Accent)
            }
        }
    }
}

internal fun plainTypeLabel(type: TransactionType, fallback: String): String = when (type) {
    TransactionType.SENT -> "Send Money"
    TransactionType.PAYBILL -> "Bills (Paybill)"
    TransactionType.BUY_GOODS -> "Shops (Buy goods)"
    TransactionType.POCHI -> "Small businesses (Pochi)"
    TransactionType.WITHDRAW -> "Cash withdrawals"
    TransactionType.AIRTIME -> "Airtime and bundles"
    TransactionType.SAVINGS -> "Savings"
    else -> fallback
}

@Composable
internal fun WhoGotTheMostCard(
    people: List<CounterpartySpend>,
    businesses: List<CounterpartySpend>,
    peopleTotal: Double,
    businessTotal: Double
) {
    val caption = buildString {
        if (peopleTotal > 0.0 && businessTotal > 0.0) {
            append("People ${formatKsh(peopleTotal)} · Businesses ${formatKsh(businessTotal)}")
        } else if (peopleTotal > 0.0) {
            append("People ${formatKsh(peopleTotal)}")
        } else if (businessTotal > 0.0) {
            append("Businesses ${formatKsh(businessTotal)}")
        }
    }.ifBlank { null }
    InsightCard(title = "Who got the most", caption = caption) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (people.isNotEmpty()) {
                CounterpartySection(title = "People", rows = people)
            }
            if (people.isNotEmpty() && businesses.isNotEmpty()) {
                HorizontalDivider(color = Hairline, thickness = 1.dp)
            }
            if (businesses.isNotEmpty()) {
                CounterpartySection(title = "Businesses", rows = businesses)
            }
        }
    }
}

@Composable
internal fun CounterpartySection(title: String, rows: List<CounterpartySpend>) {
    Text(
        title,
        style = HomeType.caption,
        color = Mute,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
    rows.forEachIndexed { index, row ->
        if (index > 0) HorizontalDivider(color = Hairline, thickness = 1.dp)
        CounterpartyRow(row)
    }
}

@Composable
internal fun CounterpartyRow(row: CounterpartySpend) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InitialDisc(
            name = row.name,
            fill = if (row.business) SoftBlue else ActionMetrics,
            tint = if (row.business) Ink else OnPastel
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                row.name,
                style = HomeType.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover()
            )
            Text(
                text = buildString {
                    append("${row.count} time${if (row.count == 1) "" else "s"}")
                    if (row.lastMillis > 0L) {
                        val last = Instant.ofEpochMilli(row.lastMillis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        append(" · last on ${busiestDayLabel(last)}")
                    }
                },
                style = HomeType.caption,
                color = Mute
            )
        }
        Text(
            formatKsh(row.total),
            style = HomeType.rowTitle,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.recordingPrivacyCover()
        )
    }
}

// endregion

// region Recap, badges, export

@Composable
internal fun RecapCard(
    checkInDone: Boolean,
    finding: String,
    badges: List<InsightBadge>,
    onCheckIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SoftBlue)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "MONDAY RECAP",
                style = HomeType.caption,
                color = Accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = if (checkInDone) {
                    "This week's finding: $finding"
                } else {
                    "Check in on Monday to unlock this week's new finding."
                },
                style = HomeType.body,
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.recordingPrivacyCover()
            )
            if (!checkInDone) {
                TextButton(onClick = onCheckIn) {
                    Text("Check in now", style = HomeType.label, color = Accent)
                }
            }
        }
        if (badges.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                badges.forEach { badge ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(if (badge.earned) Accent else CardWhite)
                                .then(
                                    if (badge.earned) Modifier else Modifier.border(2.dp, LabelBlue, CircleShape)
                                )
                        ) {
                            Text(
                                badge.mark,
                                style = HomeType.caption,
                                fontWeight = FontWeight.Bold,
                                color = if (badge.earned) OnAccent else Mute
                            )
                        }
                        Text(
                            badge.name,
                            style = HomeType.caption,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ExportBlock(rangeLabel: String, onExport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Accent, RoundedCornerShape(24.dp))
                .clickable(role = Role.Button, onClick = onExport),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.FileDownload,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Save ${rangeLabel.replaceFirstChar { it.lowercase() }} as PDF",
                style = HomeType.label.asPillLabel(),
                color = Accent,
                maxLines = 1
            )
        }
        Text(
            "Statement for this stretch, saved on this phone. Up to 30 days.",
            style = HomeType.caption,
            color = Mute,
            textAlign = TextAlign.Center
        )
    }
}

// region Shared pieces

@Composable
internal fun InsightCard(
    title: String? = null,
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (title != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = HomeType.section, color = Ink, fontWeight = FontWeight.Bold)
                if (caption != null) {
                    Text(caption, style = HomeType.caption, color = Mute)
                }
            }
        }
        content()
    }
}

@Composable
internal fun InitialDisc(name: String, fill: Color, tint: Color) {
    CounterpartyAvatar(
        name = name,
        fill = fill,
        contentColor = tint,
        size = 40.dp,
        fontWeight = FontWeight.Bold
    )
}
