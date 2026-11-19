package com.lipabill.app.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lipabill.app.engage.SpendCategory
import com.lipabill.app.ui.LipaChoiceChip
import com.lipabill.app.ui.LipaPillButton
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.viewmodel.BudgetPace
import com.lipabill.app.viewmodel.BudgetStatus
import com.lipabill.app.viewmodel.CategoryChange
import com.lipabill.app.viewmodel.GoalProgress
import com.lipabill.app.viewmodel.MoneyPlanUi
import com.lipabill.app.viewmodel.PriceTrend
import com.lipabill.app.viewmodel.RegularChange
import com.lipabill.app.viewmodel.SafeToSpend
import com.lipabill.app.viewmodel.SavingsGoal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val DayMonth = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
private val ShortDate = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/** Amber for "watch" pace; dark enough for 3:1 against the light track. */
private val Watch = Color(0xFFB45309)

/** Categories a budget can be set on (money in and the catch-all are not budgets). */
private val BudgetCategories = SpendCategory.entries.filter { it != SpendCategory.INCOME }

/** One line for the home screen and the plan card: "until Tue 25 Nov · 1 bill held back". */
fun safeToSpendDetail(safe: SafeToSpend): String = buildString {
    append("until ")
    append(safe.until.format(DayMonth))
    if (safe.paydayEstimated) append(" (payday)")
    if (safe.billsDue > 0) {
        append(" · ")
        append(formatKsh(safe.reservedForBills))
        append(if (safe.billsDue == 1) " held for 1 bill" else " held for ${safe.billsDue} bills")
    }
}

/** Safe to spend, budgets and savings goals in one card near the top of Insights. */
@Composable
internal fun PlanCard(
    plan: MoneyPlanUi,
    onSetBudget: (SpendCategory, Double?) -> Unit,
    onSaveGoal: (SavingsGoal) -> Unit,
    onDeleteGoal: (String) -> Unit
) {
    var budgetEdit by remember { mutableStateOf<BudgetEdit?>(null) }
    var goalEdit by remember { mutableStateOf<GoalEdit?>(null) }

    InsightCard(title = "Your plan", caption = "What you can spend, where it is going, and what you are saving for") {
        SafeToSpendBlock(plan.safe, ready = plan.isReady)
        HorizontalDivider(color = Canvas)

        Text("Budgets this month", style = HomeType.label, fontWeight = FontWeight.Bold, color = Ink)
        if (plan.budgets.isEmpty()) {
            Text(
                "Set a monthly limit for a category and LipaBill tracks the pace as payments arrive.",
                style = HomeType.caption,
                color = Mute
            )
        }
        plan.budgets.forEach { status ->
            BudgetRow(status) { budgetEdit = BudgetEdit(status.category, status.limit) }
        }
        LipaPillButton(
            label = "Set a budget",
            onClick = { budgetEdit = BudgetEdit(null, null) },
            filled = false
        )
        HorizontalDivider(color = Canvas)

        Text("Savings goals", style = HomeType.label, fontWeight = FontWeight.Bold, color = Ink)
        if (plan.goals.isEmpty()) {
            Text(
                "Name what you are saving for. Your M-Shwari and lock-savings transfers fill it in.",
                style = HomeType.caption,
                color = Mute
            )
        }
        plan.goals.forEach { progress ->
            GoalRow(progress) { goalEdit = GoalEdit(progress.goal) }
        }
        LipaPillButton(
            label = "New goal",
            onClick = { goalEdit = GoalEdit(null) },
            filled = false
        )
    }

    budgetEdit?.let { edit ->
        BudgetDialog(
            initial = edit,
            existing = plan.budgets.map { it.category }.toSet(),
            onSave = { category, limit ->
                onSetBudget(category, limit)
                budgetEdit = null
            },
            onRemove = edit.category?.let { category -> { onSetBudget(category, null); budgetEdit = null } },
            onDismiss = { budgetEdit = null }
        )
    }
    goalEdit?.let { edit ->
        GoalDialog(
            initial = edit.goal,
            onSave = { goal ->
                onSaveGoal(goal)
                goalEdit = null
            },
            onDelete = edit.goal?.let { goal -> { onDeleteGoal(goal.id); goalEdit = null } },
            onDismiss = { goalEdit = null }
        )
    }
}

@Composable
private fun SafeToSpendBlock(safe: SafeToSpend?, ready: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Safe to spend", style = HomeType.caption, color = Mute)
        if (safe == null) {
            Text(
                // Until the plan has read the ledger, say so instead of implying no balance.
                if (!ready) "Working it out…" else "Shows once a wallet balance arrives by SMS.",
                style = HomeType.body,
                color = Ink
            )
            return
        }
        Text(
            "${formatKsh(safe.perDay)} a day",
            style = HomeType.section,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.recordingPrivacyCover()
        )
        Text(safeToSpendDetail(safe), style = HomeType.caption, color = Mute, modifier = Modifier.recordingPrivacyCover())
    }
}

@Composable
private fun ProgressTrack(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Canvas)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

@Composable
private fun BudgetRow(status: BudgetStatus, onEdit: () -> Unit) {
    val color = when (status.pace) {
        BudgetPace.ON_TRACK -> Accent
        BudgetPace.WATCH -> Watch
        BudgetPace.OVER -> Expense
    }
    val note = when (status.pace) {
        BudgetPace.OVER -> "${formatKsh(status.spent - status.limit)} over the limit"
        BudgetPace.WATCH -> if (status.projectedOver > 0.0) {
            "On pace to go ${formatKsh(status.projectedOver)} over"
        } else {
            "${(status.usedFraction * 100).toInt()}% used · ${status.daysLeft} days left"
        }
        BudgetPace.ON_TRACK -> "On track · ${formatKsh(status.limit - status.spent)} left for ${status.daysLeft} days"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Edit budget", onClick = onEdit)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status.category.label, style = HomeType.label, color = Ink, modifier = Modifier.weight(1f))
            Text(
                "${formatKsh(status.spent)} of ${formatKsh(status.limit)}",
                style = HomeType.label,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
        ProgressTrack(status.usedFraction, color)
        Text(note, style = HomeType.caption, color = if (status.pace == BudgetPace.ON_TRACK) Mute else color)
    }
}

@Composable
private fun GoalRow(progress: GoalProgress, onEdit: () -> Unit) {
    val note = when {
        progress.done -> "Reached. Well done."
        progress.perWeekNeeded != null && progress.goal.deadline != null ->
            "${formatKsh(progress.perWeekNeeded)} a week to reach it by ${progress.goal.deadline.format(ShortDate)}"
        else -> "${formatKsh(progress.goal.target - progress.saved)} to go"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Edit goal", onClick = onEdit)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(progress.goal.name, style = HomeType.label, color = Ink, modifier = Modifier.weight(1f))
            Text(
                "${formatKsh(progress.saved)} of ${formatKsh(progress.goal.target)}",
                style = HomeType.label,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
        ProgressTrack(progress.fraction, if (progress.done) Income else Accent)
        Text(note, style = HomeType.caption, color = if (progress.done) Income else Mute)
    }
}

/** Categories that moved most against the previous period. */
@Composable
internal fun CategoryChangesCard(changes: List<CategoryChange>) {
    if (changes.isEmpty()) return
    InsightCard(title = "Compared with last period", caption = "Spend by category against the same length of time before") {
        changes.forEach { change ->
            val up = change.now > change.before
            val percent = change.percent
            val delta = when {
                percent == null -> "New"
                change.now < 1.0 -> "None this time"
                percent == 0 -> "Same"
                else -> (if (up) "Up " else "Down ") + "${kotlin.math.abs(percent)}%"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(change.category.label, style = HomeType.label, color = Ink)
                    Text(
                        "${formatKsh(change.now)} · was ${formatKsh(change.before)}",
                        style = HomeType.caption,
                        color = Mute,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
                Text(
                    delta,
                    style = HomeType.label,
                    fontWeight = FontWeight.Bold,
                    // Spending more reads as a warning, less as a win.
                    color = when {
                        percent == 0 -> Mute
                        up -> Expense
                        else -> Income
                    }
                )
            }
        }
    }
}

private data class BudgetEdit(val category: SpendCategory?, val limit: Double?)
private data class GoalEdit(val goal: SavingsGoal?)

private fun parseAmount(raw: String): Double? =
    raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull()?.takeIf { it > 0.0 }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BudgetDialog(
    initial: BudgetEdit,
    existing: Set<SpendCategory>,
    onSave: (SpendCategory, Double) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf(initial.category ?: BudgetCategories.firstOrNull { it !in existing }) }
    var amount by remember { mutableStateOf(initial.limit?.let { "%.0f".format(it) } ?: "") }
    val limit = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.category == null) "Set a budget" else "${initial.category.label} budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (initial.category == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BudgetCategories.forEach { option ->
                            LipaChoiceChip(label = option.label, selected = option == category, onClick = { category = option })
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Monthly limit (KSh)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = category != null && limit != null,
                onClick = { onSave(category!!, limit!!) }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove", color = Expense) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDialog(
    initial: SavingsGoal?,
    onSave: (SavingsGoal) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val today = remember { LocalDate.now() }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.target?.let { "%.0f".format(it) } ?: "") }
    // Deadline as a choice of horizons; editing keeps the existing date.
    var months by remember { mutableStateOf<Long?>(if (initial == null) 6L else null) }
    var keepDeadline by remember { mutableStateOf(initial?.deadline) }
    val target = parseAmount(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New savings goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("What for") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() }.take(9) },
                    label = { Text("Target (KSh)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("By when", style = HomeType.caption, color = Mute)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keepDeadline?.let { date ->
                        LipaChoiceChip(label = date.format(ShortDate), selected = months == null, onClick = { months = null })
                    }
                    listOf(3L to "3 months", 6L to "6 months", 12L to "1 year", 0L to "No date").forEach { (m, label) ->
                        LipaChoiceChip(label = label, selected = months == m, onClick = { months = m })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && target != null,
                onClick = {
                    val deadline = when (months) {
                        null -> keepDeadline
                        0L -> null
                        else -> today.plusMonths(months!!)
                    }
                    onSave(
                        SavingsGoal(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            target = target!!,
                            createdAtMillis = initial?.createdAtMillis ?: System.currentTimeMillis(),
                            deadline = deadline
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = Expense) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}


/** Regular bills that changed, and how prices at usual places have moved. */
@Composable
internal fun WatchingCard(changes: List<RegularChange>, trends: List<PriceTrend>) {
    if (changes.isEmpty() && trends.isEmpty()) return
    InsightCard(title = "Watching what repeats", caption = "Bills and usual shops that changed") {
        changes.forEach { change ->
            val (line, detail, color) = when (change) {
                is RegularChange.PriceChanged -> Triple(
                    "${change.name} went ${if (change.difference > 0) "up" else "down"} ${formatKsh(kotlin.math.abs(change.difference))}",
                    "Now ${formatKsh(change.latest)}, usually ${formatKsh(change.usual)}",
                    if (change.difference > 0) Expense else Income
                )
                is RegularChange.NewCharge -> Triple(
                    "New regular: ${change.name}",
                    "${formatKsh(change.amount)} · ${change.cadence.label.lowercase()}",
                    Watch
                )
                is RegularChange.Stopped -> Triple(
                    "${change.name} stopped",
                    "Last paid ${change.lastPaid.format(ShortDate)} · ${formatKsh(change.amount)}",
                    Mute
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(line, style = HomeType.label, fontWeight = FontWeight.Bold, color = color)
                Text(detail, style = HomeType.caption, color = Mute, modifier = Modifier.recordingPrivacyCover())
            }
        }
        if (changes.isNotEmpty() && trends.isNotEmpty()) HorizontalDivider(color = Canvas)
        trends.forEach { trend ->
            val up = trend.percent > 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(trend.name, style = HomeType.label, color = Ink)
                    Text(
                        "${formatKsh(trend.recentAverage)} a visit · was ${formatKsh(trend.earlierAverage)} since ${trend.since.format(MonthYear)}",
                        style = HomeType.caption,
                        color = Mute,
                        modifier = Modifier.recordingPrivacyCover()
                    )
                }
                Text(
                    (if (up) "Up " else "Down ") + "${kotlin.math.abs(trend.percent)}%",
                    style = HomeType.label,
                    fontWeight = FontWeight.Bold,
                    color = if (up) Expense else Income
                )
            }
        }
    }
}

private val MonthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.UK)
