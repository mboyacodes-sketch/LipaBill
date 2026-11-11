package com.lipabill.app.ui.engage

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.R
import com.lipabill.app.engage.BadgeProgress
import com.lipabill.app.engage.CatchUp
import com.lipabill.app.engage.CategoryRules
import com.lipabill.app.engage.ChallengeTemplates
import com.lipabill.app.engage.HabitBadge
import com.lipabill.app.engage.HabitLevels
import com.lipabill.app.engage.HabitPattern
import com.lipabill.app.engage.HabitPoints
import com.lipabill.app.engage.centsToKes
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.OnPastel
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.viewmodel.ChallengeOption
import com.lipabill.app.viewmodel.ChallengeSubject
import com.lipabill.app.viewmodel.HabitsUiState
import com.lipabill.app.viewmodel.TEACH_NEED
import com.lipabill.app.viewmodel.TEACH_WANT
import java.time.format.TextStyle
import java.util.Locale

/** Pages inside Habits. Everything opens from the Habits home and returns to it. */
internal enum class HabitsPage {
    HOME, CHALLENGES, NOTICED, BADGES, CHECK_IN, COMPLETE, RECAP, SETTINGS
}

internal object HabitsTokens {
    val pagePad = 16.dp
    val sectionGap = 16.dp
    val cardRadius = 20.dp
    val levelRadius = 24.dp
    val CardShape = RoundedCornerShape(cardRadius)
    val LevelShape = RoundedCornerShape(levelRadius)
}

private enum class ChallengeFilter { FOR_YOU, SPEND, FEES, SAVE }

@Composable
internal fun levelName(number: Int): String = stringResource(
    when (number) {
        1 -> R.string.habit_level_1
        2 -> R.string.habit_level_2
        3 -> R.string.habit_level_3
        4 -> R.string.habit_level_4
        5 -> R.string.habit_level_5
        6 -> R.string.habit_level_6
        else -> R.string.habit_level_7
    }
)

@Composable
internal fun LevelCard(
    habits: HabitsUiState,
    streak: Int,
    quietDays: Int,
    feesDodgedCents: Long,
    onClick: () -> Unit
) {
    val level = habits.level
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.LevelShape)
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.habits_level_label, level.number),
                    style = HomeType.caption,
                    color = SoftBlue,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    levelName(level.number),
                    style = HomeType.greeting.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    color = OnAccent
                )
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(OnAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = OnAccent, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
        HabitsProgressBar(
            fraction = level.progress(habits.points),
            track = OnAccent.copy(alpha = 0.18f),
            fill = ActionMetrics
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            val next = level.nextMinPoints
            Text(
                if (next == null) {
                    stringResource(R.string.habits_points_top, habits.points)
                } else {
                    stringResource(R.string.habits_points_progress, habits.points, next)
                },
                style = HomeType.caption,
                color = SoftBlue,
                modifier = Modifier.weight(1f)
            )
            if (next != null) {
                Text(
                    stringResource(R.string.habits_next_level, levelName(level.number + 1)),
                    style = HomeType.caption,
                    color = SoftBlue
                )
            }
        }
        Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LevelStat(streak.toString(), stringResource(R.string.habits_stat_streak), Modifier.weight(1f))
            LevelStat(quietDays.toString(), stringResource(R.string.habits_stat_quiet), Modifier.weight(1f))
            val feesLabel = if (feesDodgedCents > 0L) {
                formatKsh(centsToKes(feesDodgedCents)).removePrefix("KSh ").trim()
            } else {
                "0"
            }
            LevelStat(feesLabel, stringResource(R.string.habits_stat_fees), Modifier.weight(1f))
        }
    }
}

@Composable
private fun LevelStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(OnAccent.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(value, style = HomeType.greeting.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = OnAccent)
        Text(label, style = HomeType.caption.copy(fontSize = 11.sp), color = SoftBlue)
    }
}

@Composable
internal fun HabitsProgressBar(
    fraction: Float,
    track: androidx.compose.ui.graphics.Color,
    fill: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(5.dp))
                .background(fill)
        )
    }
}

@Composable
internal fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(title, style = HomeType.section.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = Ink, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = HomeType.body,
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
internal fun CatchUpCard(catchUp: CatchUp, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(Accent)
            .padding(18.dp)
    ) {
        Text(
            stringResource(R.string.habits_catchup_label, catchUp.daysAway),
            style = HomeType.caption,
            color = SoftBlue,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        Text(stringResource(R.string.habits_catchup_title), style = HomeType.rowTitle.copy(fontSize = 17.sp), color = OnAccent)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CatchUpStat(catchUp.payments.toString(), stringResource(R.string.habits_catchup_payments), Modifier.weight(1f))
            CatchUpStat(catchUp.newRegulars.toString(), stringResource(R.string.habits_catchup_regulars), Modifier.weight(1f))
            CatchUpStat(
                catchUp.nextBillLabel ?: "—",
                stringResource(R.string.habits_catchup_bill_stat),
                Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(stringResource(R.string.habits_catchup_safe), style = HomeType.body, color = SoftBlue)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardWhite)
                .clickable(onClick = onDismiss)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.habits_catchup_ok), style = HomeType.rowTitle, color = Accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CatchUpStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(OnAccent.copy(alpha = 0.10f))
            .padding(10.dp)
    ) {
        Text(value, style = HomeType.rowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), color = OnAccent, maxLines = 1)
        Text(label, style = HomeType.caption.copy(fontSize = 11.sp), color = SoftBlue)
    }
}

/** The challenge a pattern turns into, if any. */
internal fun patternChallenge(pattern: HabitPattern): String? = when (pattern) {
    is HabitPattern.FoodPeakDay -> ChallengeTemplates.foodDelivery3Days.id
    is HabitPattern.FulizaLateMonth -> ChallengeTemplates.fulizaFree7Days.id
    is HabitPattern.FrequentPayee -> ChallengeTemplates.skipPayee(pattern.payee).id
    is HabitPattern.PaydayRush -> ChallengeTemplates.paydayPause2Days.id
    is HabitPattern.AirtimeDrip -> null
}

@Composable
internal fun PatternCard(
    pattern: HabitPattern,
    canStart: Boolean,
    onStart: (String) -> Unit,
    onNotMe: () -> Unit,
    foodBars: List<Int> = emptyList(),
    compact: Boolean = false
) {
    val (icon, title, body) = patternCopy(pattern)
    val strong = pattern is HabitPattern.FoodPeakDay && pattern.sharePercent >= 40
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, Hairline, HabitsTokens.CardShape)
            .padding(18.dp)
    ) {
        if (compact) {
            Row(verticalAlignment = Alignment.Top) {
                IconChip(icon)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = HomeType.rowTitle, color = Ink)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(body, style = HomeType.body, color = Mute)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = HomeType.rowTitle, color = Ink, modifier = Modifier.weight(1f))
                Text(
                    stringResource(if (strong) R.string.habits_pattern_strong else R.string.habits_pattern_likely),
                    style = HomeType.caption,
                    color = if (strong) OnPastel else Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (strong) ActionMetrics.copy(alpha = 0.85f) else SoftBlue)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            if (pattern is HabitPattern.FoodPeakDay && foodBars.size == 7) {
                Spacer(modifier = Modifier.height(12.dp))
                WeekdayBars(counts = foodBars, highlight = pattern.day.value - 1)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(body, style = HomeType.body, color = Mute)
        }
        Spacer(modifier = Modifier.height(12.dp))
        when (pattern) {
            is HabitPattern.PaydayRush -> {
                val templateId = patternChallenge(pattern)
                if (templateId != null && canStart) {
                    PillButton(
                        label = stringResource(R.string.pattern_payday_cta),
                        filled = false,
                        accentOutline = true,
                        onClick = { onStart(templateId) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            is HabitPattern.AirtimeDrip -> Unit
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val templateId = patternChallenge(pattern)
                    if (templateId != null && canStart) {
                        PillButton(
                            label = when (pattern) {
                                is HabitPattern.FulizaLateMonth -> stringResource(R.string.pattern_fuliza_cta)
                                else -> stringResource(R.string.habits_make_challenge)
                            },
                            filled = true,
                            onClick = { onStart(templateId) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    PillButton(label = stringResource(R.string.habits_not_me), filled = false, onClick = onNotMe)
                }
            }
        }
    }
}

@Composable
private fun WeekdayBars(counts: List<Int>, highlight: Int) {
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            counts.forEachIndexed { index, count ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((8 + 56 * count / max).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when {
                                index == highlight -> Accent
                                count > 0 -> SoftBlue.copy(alpha = 0.85f)
                                else -> SoftBlue
                            }
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { index, label ->
                Text(
                    label,
                    style = HomeType.caption,
                    color = if (index == highlight) Ink else Mute,
                    fontWeight = if (index == highlight) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun patternCopy(pattern: HabitPattern): Triple<ImageVector, String, String> = when (pattern) {
    is HabitPattern.FoodPeakDay -> {
        val day = pattern.day.getDisplayName(TextStyle.FULL, Locale.UK)
        Triple(
            Icons.Outlined.DeliveryDining,
            stringResource(R.string.pattern_food_title, day),
            stringResource(
                R.string.pattern_food_body,
                pattern.sharePercent,
                day,
                formatKsh(centsToKes(pattern.weeklyCents))
            )
        )
    }
    is HabitPattern.FulizaLateMonth -> Triple(
        Icons.Outlined.CreditCardOff,
        stringResource(R.string.pattern_fuliza_title),
        stringResource(R.string.pattern_fuliza_body, pattern.lateMonths, pattern.months)
    )
    is HabitPattern.FrequentPayee -> Triple(
        Icons.Outlined.Storefront,
        stringResource(R.string.pattern_regular_title, pattern.payee),
        stringResource(R.string.pattern_regular_body, pattern.count, formatKsh(centsToKes(pattern.cents)))
    )
    is HabitPattern.AirtimeDrip -> Triple(
        Icons.Outlined.PhoneAndroid,
        stringResource(R.string.pattern_airtime_title),
        stringResource(R.string.pattern_airtime_body, pattern.count, formatKsh(centsToKes(pattern.averageCents)))
    )
    is HabitPattern.PaydayRush -> Triple(
        Icons.Outlined.Payments,
        stringResource(R.string.pattern_payday_title),
        stringResource(
            R.string.pattern_payday_body,
            pattern.events,
            formatKsh(centsToKes(pattern.spendCents / pattern.events.coerceAtLeast(1)))
        )
    )
}

@Composable
private fun IconChip(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SoftBlue),
        contentAlignment = Alignment.Center
    ) {
        // Ink on SoftBlue stays readable in dark mode.
        Icon(icon, contentDescription = null, tint = Ink)
    }
}

@Composable
internal fun PillButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentOutline: Boolean = false
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(
                if (filled) {
                    Modifier.background(if (enabled) Accent else Accent.copy(alpha = 0.35f))
                } else {
                    Modifier
                        .background(CardWhite)
                        .border(1.dp, if (accentOutline) Accent else Hairline, shape)
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = HomeType.rowTitle,
            color = when {
                filled -> OnAccent
                accentOutline -> Accent
                else -> Ink
            },
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun BadgeStrip(badges: List<BadgeProgress>, onClick: () -> Unit) {
    val shown = badges.sortedByDescending { it.earned }.take(4)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        shown.forEach { progress ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BadgeMedal(progress, size = 64)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    badgeTitle(progress.badge),
                    style = HomeType.caption,
                    color = if (progress.earned) Ink else Mute,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun BadgeMedal(progress: BadgeProgress, size: Int, dashed: Boolean = !progress.earned) {
    val dashColor = Hairline
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (progress.earned) SoftBlue else CardWhite),
        contentAlignment = Alignment.Center
    ) {
        if (dashed && !progress.earned) {
            Canvas(modifier = Modifier.size(size.dp)) {
                drawCircle(
                    color = dashColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                )
            }
        }
        Icon(
            if (progress.earned) badgeIcon(progress.badge) else Icons.Outlined.Lock,
            contentDescription = null,
            tint = if (progress.earned) Ink else Mute
        )
    }
}

private fun badgeIcon(badge: HabitBadge): ImageVector = when (badge) {
    HabitBadge.FIRST_KEPT -> Icons.Outlined.CheckCircle
    HabitBadge.THREE_KEPT, HabitBadge.FIVE_KEPT, HabitBadge.TEN_KEPT -> Icons.Outlined.EmojiEvents
    HabitBadge.FOOD_BREAK -> Icons.Outlined.DeliveryDining
    HabitBadge.SKIPPED_REGULAR -> Icons.Outlined.Storefront
    HabitBadge.FULIZA_FREE, HabitBadge.FEE_DODGER, HabitBadge.FEES_DODGED -> Icons.Outlined.CreditCardOff
    HabitBadge.PAYDAY_PAUSE_KEPT -> Icons.Outlined.Payments
    HabitBadge.QUIET_WEEK, HabitBadge.QUIET_SUNDAY -> Icons.Outlined.WbSunny
    HabitBadge.DIY_KEPT -> Icons.Outlined.Tune
    HabitBadge.FIRST_TEACH -> Icons.Outlined.School
    HabitBadge.DUO_PENDING -> Icons.Outlined.Star
    HabitBadge.FIRST_CHECK_IN, HabitBadge.CHECK_INS_5, HabitBadge.TEN_CHECK_INS -> Icons.Outlined.Quiz
    HabitBadge.STREAK_TWO, HabitBadge.STREAK_FOUR, HabitBadge.STREAK_SIX -> Icons.Outlined.CalendarMonth
}

@Composable
internal fun badgeTitle(badge: HabitBadge): String = stringResource(
    when (badge) {
        HabitBadge.FIRST_KEPT -> R.string.badge_first_kept
        HabitBadge.THREE_KEPT -> R.string.badge_three_kept
        HabitBadge.FIVE_KEPT -> R.string.badge_five_kept
        HabitBadge.TEN_KEPT -> R.string.badge_ten_kept
        HabitBadge.FOOD_BREAK -> R.string.badge_food_break
        HabitBadge.SKIPPED_REGULAR -> R.string.badge_skipped_regular
        HabitBadge.FULIZA_FREE -> R.string.badge_fuliza_free
        HabitBadge.FEE_DODGER -> R.string.badge_fee_dodger
        HabitBadge.PAYDAY_PAUSE_KEPT -> R.string.badge_payday_pause
        HabitBadge.QUIET_WEEK -> R.string.badge_quiet_week
        HabitBadge.DIY_KEPT -> R.string.badge_diy_kept
        HabitBadge.FIRST_CHECK_IN -> R.string.badge_first_check_in
        HabitBadge.CHECK_INS_5 -> R.string.badge_check_ins_5
        HabitBadge.TEN_CHECK_INS -> R.string.badge_ten_check_ins
        HabitBadge.STREAK_TWO -> R.string.badge_streak_two
        HabitBadge.STREAK_FOUR -> R.string.badge_streak_four
        HabitBadge.STREAK_SIX -> R.string.badge_streak_six
        HabitBadge.QUIET_SUNDAY -> R.string.badge_quiet_day
        HabitBadge.FIRST_TEACH -> R.string.badge_first_teach
        HabitBadge.FEES_DODGED -> R.string.badge_fees_dodged
        HabitBadge.DUO_PENDING -> R.string.badge_duo_pending
    }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChallengesPage(
    habits: HabitsUiState,
    canStart: Boolean,
    onStart: (String) -> Unit,
    onStartDiy: (categoryId: String, days: Int, capCents: Long) -> Unit,
    onDuoInvite: () -> Unit
) {
    var filter by rememberSaveable { mutableStateOf(ChallengeFilter.FOR_YOU.name) }
    val selected = ChallengeFilter.valueOf(filter)
    val filtered = habits.options.filter { option ->
        when (selected) {
            ChallengeFilter.FOR_YOU -> true
            ChallengeFilter.SPEND -> option.subject in setOf(
                ChallengeSubject.FOOD_DELIVERY, ChallengeSubject.PAYEE, ChallengeSubject.QUIET, ChallengeSubject.DIY
            )
            ChallengeFilter.FEES -> option.subject in setOf(ChallengeSubject.FULIZA, ChallengeSubject.FEE)
            ChallengeFilter.SAVE -> option.subject in setOf(ChallengeSubject.PAYDAY, ChallengeSubject.QUIET)
        }
    }
    val featured = filtered.firstOrNull { it.suggested } ?: filtered.firstOrNull()
    val rest = filtered.filterNot { it.templateId == featured?.templateId }
    Column(verticalArrangement = Arrangement.spacedBy(HabitsTokens.sectionGap)) {
        if (!canStart) {
            Text(stringResource(R.string.challenges_busy), style = HomeType.body, color = Mute)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(stringResource(R.string.challenges_filter_for_you), selected == ChallengeFilter.FOR_YOU) {
                filter = ChallengeFilter.FOR_YOU.name
            }
            FilterChip(stringResource(R.string.challenges_filter_spend), selected == ChallengeFilter.SPEND) {
                filter = ChallengeFilter.SPEND.name
            }
            FilterChip(stringResource(R.string.challenges_filter_fees), selected == ChallengeFilter.FEES) {
                filter = ChallengeFilter.FEES.name
            }
            FilterChip(stringResource(R.string.challenges_filter_save), selected == ChallengeFilter.SAVE) {
                filter = ChallengeFilter.SAVE.name
            }
        }
        featured?.let { option ->
            FeaturedChallengeCard(option = option, enabled = canStart, onStart = { onStart(option.templateId) })
        }
        rest.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    CompactChallengeCard(
                        option = option,
                        enabled = canStart,
                        onStart = { onStart(option.templateId) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        DuoCard(onInvite = onDuoInvite)
        DiyCard(
            shops = habits.options.mapNotNull { it.payee }.distinct(),
            enabled = canStart,
            onStart = onStartDiy
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Text(
        label,
        style = HomeType.body,
        color = if (selected) OnAccent else Ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Accent) else Modifier.background(CardWhite).border(1.dp, Hairline, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun FeaturedChallengeCard(option: ChallengeOption, enabled: Boolean, onStart: () -> Unit) {
    val difficulty = difficultyLabel(option.points)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(2.dp, Accent, HabitsTokens.CardShape)
            .padding(18.dp)
    ) {
        Text(
            stringResource(R.string.challenges_suggested),
            style = HomeType.caption,
            color = Accent,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(challengeOptionTitle(option), style = HomeType.greeting.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = Ink)
        Spacer(modifier = Modifier.height(6.dp))
        Text(challengeOptionBody(option), style = HomeType.body, color = Mute)
        Spacer(modifier = Modifier.height(12.dp))
        FlowRowChips(option, difficulty)
        Spacer(modifier = Modifier.height(12.dp))
        PillButton(
            label = stringResource(R.string.challenge_start),
            filled = true,
            enabled = enabled,
            onClick = onStart,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(option: ChallengeOption, difficulty: String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MetaChip(difficulty, hard = option.points >= 120)
        MetaChip(stringResource(R.string.challenges_days_chip, option.days))
        MetaChip(stringResource(R.string.challenges_points_chip, option.points), cream = true)
    }
}

@Composable
private fun MetaChip(label: String, hard: Boolean = false, cream: Boolean = false) {
    Text(
        label,
        style = HomeType.caption,
        color = when {
            hard -> ColorHard
            cream -> OnPastel
            else -> Ink
        },
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    hard -> ColorHardBg
                    cream -> ActionMetrics
                    else -> SoftBlue.copy(alpha = 0.55f)
                }
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

private val ColorHard = androidx.compose.ui.graphics.Color(0xFF8A1C1C)
private val ColorHardBg = androidx.compose.ui.graphics.Color(0xFFF5DCDC)

@Composable
private fun difficultyLabel(points: Int): String = when {
    points <= 60 -> stringResource(R.string.challenges_difficulty_easy)
    points <= 110 -> stringResource(R.string.challenges_difficulty_medium)
    else -> stringResource(R.string.challenges_difficulty_hard)
}

@Composable
private fun CompactChallengeCard(
    option: ChallengeOption,
    enabled: Boolean,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .border(1.dp, Hairline, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onStart)
            .padding(14.dp)
    ) {
        IconChip(challengeOptionIcon(option.subject))
        Spacer(modifier = Modifier.height(8.dp))
        Text(challengeOptionTitle(option), style = HomeType.rowTitle, color = Ink, maxLines = 3)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(R.string.challenges_option_meta_short, difficultyLabel(option.points), option.points),
            style = HomeType.caption,
            color = Mute
        )
    }
}

@Composable
private fun DuoCard(onInvite: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, Hairline, HabitsTokens.CardShape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SoftBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.challenges_duo_you),
                    style = HomeType.caption,
                    color = Accent,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 28.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ActionMetrics)
                    .border(2.dp, CardWhite, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.challenges_duo_plus),
                    style = HomeType.caption,
                    color = OnPastel,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.challenges_duo_title), style = HomeType.rowTitle, color = Ink)
            Text(stringResource(R.string.challenges_duo_body), style = HomeType.caption, color = Mute)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.dp, Accent, CircleShape)
                .clickable {
                    onInvite()
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.challenges_duo_share))
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.challenges_duo_invite), tint = Accent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiyCard(
    shops: List<String>,
    enabled: Boolean,
    onStart: (categoryId: String, days: Int, capCents: Long) -> Unit
) {
    val choices = listOf(
        CategoryRules.foodDelivery.categoryId to stringResource(R.string.diy_food),
        CategoryRules.discretionary.categoryId to stringResource(R.string.diy_tills)
    ) + shops.map { (CategoryRules.PAYEE_PREFIX + it) to it }
    var what by rememberSaveable { mutableStateOf(choices.first().first) }
    var days by rememberSaveable { mutableIntStateOf(7) }
    var cap by rememberSaveable { mutableIntStateOf(2500) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, SoftBlue, HabitsTokens.CardShape)
            .padding(18.dp)
    ) {
        Text(stringResource(R.string.diy_title), style = HomeType.rowTitle, color = Ink)
        Spacer(modifier = Modifier.height(4.dp))
        Text(stringResource(R.string.diy_body), style = HomeType.body, color = Mute)
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            choices.forEach { (id, label) ->
                ChoiceChip(label = label, selected = what == id, onClick = { what = id })
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.diy_under), style = HomeType.body, color = Mute)
            listOf(1000, 2500, 5000).forEach { amount ->
                ChoiceChip(
                    label = stringResource(R.string.diy_cap, "%,d".format(Locale.US, amount)),
                    selected = cap == amount,
                    onClick = { cap = amount }
                )
            }
            Text(stringResource(R.string.diy_for), style = HomeType.body, color = Mute)
            listOf(3, 7, 14).forEach { n ->
                ChoiceChip(
                    label = stringResource(R.string.diy_days, n),
                    selected = days == n,
                    onClick = { days = n }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        PillButton(
            label = stringResource(R.string.challenge_start),
            filled = true,
            enabled = enabled,
            onClick = { onStart(what, days, cap * 100L) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        label,
        style = HomeType.body,
        color = if (selected) OnAccent else Ink,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Accent) else Modifier.background(SoftBlue.copy(alpha = 0.45f)))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeachCard(payee: String, onAnswer: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, Hairline, HabitsTokens.CardShape)
            .padding(18.dp)
    ) {
        Text(stringResource(R.string.teach_title), style = HomeType.rowTitle, color = Ink)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            stringResource(R.string.teach_body, payee, HabitPoints.TEACH),
            style = HomeType.body,
            color = Mute
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceChip(stringResource(R.string.teach_groceries), false) { onAnswer(TEACH_NEED) }
            ChoiceChip(stringResource(R.string.teach_household), false) { onAnswer(TEACH_NEED) }
            ChoiceChip(stringResource(R.string.teach_snacks), false) { onAnswer(TEACH_WANT) }
            ChoiceChip(stringResource(R.string.teach_essential), false) { onAnswer(TEACH_NEED) }
        }
    }
}

@Composable
internal fun challengeOptionTitle(option: ChallengeOption): String = when (option.subject) {
    ChallengeSubject.FOOD_DELIVERY -> stringResource(R.string.challenge_option_food, option.days)
    ChallengeSubject.FULIZA -> stringResource(R.string.challenge_option_fuliza, option.days)
    ChallengeSubject.PAYEE -> stringResource(R.string.challenge_option_payee, option.days, option.payee.orEmpty())
    ChallengeSubject.QUIET -> stringResource(R.string.challenge_option_quiet, option.days)
    ChallengeSubject.FEE -> stringResource(R.string.challenge_option_fee, option.days)
    ChallengeSubject.PAYDAY -> stringResource(R.string.challenge_option_payday, option.days)
    ChallengeSubject.DIY -> {
        val category = ChallengeTemplates.diyOf(option.templateId)?.categoryId.orEmpty()
        when {
            category == CategoryRules.foodDelivery.categoryId ->
                stringResource(R.string.challenge_option_food, option.days)
            category.startsWith(CategoryRules.PAYEE_PREFIX) -> stringResource(
                R.string.challenge_option_payee,
                option.days,
                category.removePrefix(CategoryRules.PAYEE_PREFIX)
            )
            else -> stringResource(R.string.challenge_option_tills, option.days)
        }
    }
}

@Composable
private fun challengeOptionBody(option: ChallengeOption): String = when (option.subject) {
    ChallengeSubject.FOOD_DELIVERY -> stringResource(R.string.challenge_option_food_body)
    ChallengeSubject.FULIZA -> stringResource(R.string.challenge_option_fuliza_body)
    ChallengeSubject.PAYEE -> stringResource(R.string.challenge_option_payee_body)
    ChallengeSubject.QUIET -> stringResource(R.string.challenge_option_quiet_body)
    ChallengeSubject.FEE -> stringResource(R.string.challenge_option_fee_body)
    ChallengeSubject.PAYDAY -> stringResource(R.string.challenge_option_payday_body)
    ChallengeSubject.DIY -> stringResource(R.string.challenge_option_diy_body)
}

private fun challengeOptionIcon(subject: ChallengeSubject): ImageVector = when (subject) {
    ChallengeSubject.FOOD_DELIVERY -> Icons.Outlined.DeliveryDining
    ChallengeSubject.FULIZA, ChallengeSubject.FEE -> Icons.Outlined.CreditCardOff
    ChallengeSubject.PAYEE -> Icons.Outlined.Storefront
    ChallengeSubject.QUIET -> Icons.Outlined.WbSunny
    ChallengeSubject.PAYDAY -> Icons.Outlined.Payments
    ChallengeSubject.DIY -> Icons.Outlined.Tune
}

@Composable
internal fun NoticedPage(
    habits: HabitsUiState,
    canStart: Boolean,
    onStart: (String) -> Unit,
    onNotMe: (String) -> Unit,
    onDismissCatchUp: () -> Unit,
    onTeach: (payee: String, answer: String) -> Unit
) {
    val learningGood = habits.learningScore >= 55
    Column(verticalArrangement = Arrangement.spacedBy(HabitsTokens.sectionGap)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HabitsTokens.CardShape)
                .background(CardWhite)
                .border(1.dp, Hairline, HabitsTokens.CardShape)
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Income, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    stringResource(R.string.habits_noticed_private),
                    style = HomeType.caption,
                    color = Income,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(R.string.habits_noticed_read, habits.paymentsRead),
                style = HomeType.body,
                color = Ink
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.habits_learning_label), style = HomeType.caption, color = Mute)
                Text(
                    stringResource(if (learningGood) R.string.habits_learning_good else R.string.habits_learning_fair),
                    style = HomeType.caption,
                    color = Ink,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            HabitsProgressBar(
                fraction = habits.learningScore / 100f,
                track = SoftBlue,
                fill = Accent
            )
        }
        habits.catchUp?.let { CatchUpCard(it, onDismissCatchUp) }
        Text(stringResource(R.string.habits_patterns), style = HomeType.section.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = Ink)
        if (habits.patterns.isEmpty()) {
            Text(stringResource(R.string.habits_noticed_empty), style = HomeType.body, color = Mute)
        }
        habits.patterns.forEach { pattern ->
            PatternCard(
                pattern = pattern,
                canStart = canStart,
                onStart = onStart,
                onNotMe = { onNotMe(pattern.id) },
                foodBars = habits.foodDayBars
            )
        }
        habits.teachCandidate?.let { payee ->
            TeachCard(payee = payee, onAnswer = { answer -> onTeach(payee, answer) })
        }
    }
}

@Composable
internal fun BadgesPage(habits: HabitsUiState) {
    val earned = habits.badges.count { it.earned }
    val almost = habits.badges.filter { !it.earned && it.current > 0 }
    val toFind = habits.badges.size - earned - almost.size
    Column(verticalArrangement = Arrangement.spacedBy(HabitsTokens.sectionGap)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HabitsTokens.CardShape)
                .background(CardWhite)
                .border(1.dp, Hairline, HabitsTokens.CardShape)
                .padding(18.dp)
        ) {
            Text(stringResource(R.string.badges_levels), style = HomeType.rowTitle, color = Ink)
            Spacer(modifier = Modifier.height(12.dp))
            LevelPath(current = habits.level.number)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "1 ${levelName(1)} · 2 ${levelName(2)} · 3 ${levelName(3)} · 4 ${levelName(4)}",
                style = HomeType.caption,
                color = Mute
            )
            Text(
                "5 ${levelName(5)} · 6 ${levelName(6)} · 7 ${levelName(7)}",
                style = HomeType.caption,
                color = Mute
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryStat(earned.toString(), stringResource(R.string.badges_summary_earned), Modifier.weight(1f))
            SummaryStat(almost.size.toString(), stringResource(R.string.badges_summary_almost), Modifier.weight(1f))
            SummaryStat(toFind.coerceAtLeast(0).toString(), stringResource(R.string.badges_summary_find), Modifier.weight(1f))
        }
        if (almost.isNotEmpty()) {
            Text(stringResource(R.string.badges_almost_title), style = HomeType.section.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = Ink)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(HabitsTokens.CardShape)
                    .background(CardWhite)
                    .border(1.dp, Hairline, HabitsTokens.CardShape)
                    .padding(horizontal = 16.dp)
            ) {
                almost.take(3).forEachIndexed { index, progress ->
                    AlmostRow(progress)
                    if (index < almost.take(3).lastIndex) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Hairline))
                    }
                }
            }
        }
        Text(stringResource(R.string.badges_collection), style = HomeType.section.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = Ink)
        habits.badges.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { progress ->
                    CollectionCell(progress, Modifier.weight(1f))
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun LevelPath(current: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        HabitLevels.thresholds.indices.forEach { index ->
            val number = index + 1
            val reached = number <= current
            val isCurrent = number == current
            Box(
                modifier = Modifier
                    .size(if (isCurrent) 40.dp else 32.dp)
                    .clip(CircleShape)
                    .then(
                        if (isCurrent) {
                            Modifier
                                .background(ActionMetrics)
                                .border(3.dp, Accent, CircleShape)
                        } else {
                            Modifier.background(if (reached) Accent else SoftBlue)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    number.toString(),
                    style = HomeType.caption,
                    color = when {
                        isCurrent -> OnPastel
                        reached -> OnAccent
                        else -> Accent
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            if (index < HabitLevels.thresholds.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (number < current) Accent else SoftBlue)
                )
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Text(value, style = HomeType.greeting.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), color = Ink)
        Text(label, style = HomeType.caption, color = Mute)
    }
}

@Composable
private fun AlmostRow(progress: BadgeProgress) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(SoftBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(badgeIcon(progress.badge), contentDescription = null, tint = Ink)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(badgeTitle(progress.badge), style = HomeType.rowTitle, color = Ink)
                Text(
                    stringResource(R.string.badges_progress, progress.current, progress.badge.target),
                    style = HomeType.caption,
                    color = Mute
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            HabitsProgressBar(
                fraction = progress.current.toFloat() / progress.badge.target.coerceAtLeast(1),
                track = SoftBlue,
                fill = Accent
            )
        }
    }
}

@Composable
private fun CollectionCell(progress: BadgeProgress, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BadgeMedal(progress, size = 52)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            badgeTitle(progress.badge),
            style = HomeType.caption,
            color = if (progress.earned) Ink else Mute,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
internal fun SettingsPage(onOpenRecap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, Hairline, HabitsTokens.CardShape)
            .padding(18.dp)
    ) {
        Text(stringResource(R.string.habits_settings_title), style = HomeType.rowTitle, color = Ink)
        Spacer(modifier = Modifier.height(6.dp))
        Text(stringResource(R.string.habits_settings_body), style = HomeType.body, color = Mute)
        Spacer(modifier = Modifier.height(12.dp))
        PillButton(
            label = stringResource(R.string.habits_settings_recap),
            filled = true,
            onClick = onOpenRecap,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
