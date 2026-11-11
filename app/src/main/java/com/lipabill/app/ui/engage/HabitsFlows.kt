package com.lipabill.app.ui.engage

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.R
import com.lipabill.app.engage.HabitPoints
import com.lipabill.app.engage.QuizKind
import com.lipabill.app.engage.QuizQuestion
import com.lipabill.app.engage.SpendCompare
import com.lipabill.app.engage.CheckInScoring
import com.lipabill.app.engage.centsToKes
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.OnPastel
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.util.formatKsh
import com.lipabill.app.viewmodel.ChallengeCardState
import com.lipabill.app.viewmodel.EngageUiState
import com.lipabill.app.viewmodel.HabitsUiState
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun CheckInPage(
    state: EngageUiState,
    onClose: () -> Unit,
    onComplete: (Map<String, String>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .padding(horizontal = HabitsTokens.pagePad, vertical = 12.dp)
    ) {
        if (state.questions.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.checkin_close), tint = Ink)
                }
            }
            Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
            Text(stringResource(R.string.checkin_empty_title), style = HomeType.greeting, color = Ink)
            Spacer(modifier = Modifier.height(6.dp))
            Text(stringResource(R.string.checkin_empty_body), style = HomeType.body, color = Mute)
            Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
            HabitsAccentButton(label = stringResource(R.string.checkin_finish), onClick = onClose)
        } else {
            QuizFlow(
                state = state,
                onDismiss = onClose,
                onComplete = onComplete,
                fullScreen = true
            )
        }
    }
}

@Composable
internal fun QuizFlow(
    state: EngageUiState,
    onDismiss: () -> Unit,
    onComplete: (Map<String, String>) -> Unit,
    fullScreen: Boolean = false
) {
    val questions = state.questions
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var pickedId by remember { mutableStateOf<String?>(null) }
    var showSummary by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var score by remember { mutableIntStateOf(HabitPoints.CHECK_IN) }
    var streakTarget by remember { mutableIntStateOf(0) }
    val streak by animateIntAsState(streakTarget, tween(350), label = "streak")
    LaunchedEffect(showSummary) {
        if (showSummary) streakTarget = state.nextStreakCount
    }
    val step = if (showSummary) -1 else index
    val categoryCents = state.summary?.byCategory.orEmpty().associate { it.type.name to it.cents }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fullScreen) Modifier.fillMaxSize() else Modifier)
    ) {
        if (fullScreen && !showSummary) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.checkin_close), tint = Ink)
                }
                Spacer(modifier = Modifier.weight(1f))
                SegmentBars(count = questions.size, index = index)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.checkin_points_chip, score),
                    style = HomeType.caption,
                    color = OnPastel,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ActionMetrics)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState >= initialState) {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "quiz",
            modifier = Modifier.weight(1f, fill = fullScreen)
        ) { current ->
            if (current < 0) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = streak.toString(),
                        color = Accent,
                        style = HomeType.greeting.copy(
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = HomeType.greeting.fontSize * 2.4f
                        )
                    )
                    Text(
                        stringResource(R.string.checkin_done_title, state.nextStreakCount),
                        style = HomeType.rowTitle,
                        color = Ink,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.checkin_done_body),
                        style = HomeType.body,
                        color = Mute,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val question = questions[current]
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (!fullScreen) {
                        SegmentBars(count = questions.size, index = current)
                        Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
                    }
                    Text(
                        stringResource(R.string.checkin_guess_label),
                        style = HomeType.caption,
                        color = Mute,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(promptFor(question.kind), style = HomeType.greeting.copy(fontSize = 22.sp), color = Ink)
                    Spacer(modifier = Modifier.height(14.dp))
                    QuestionBody(
                        question = question,
                        revealed = revealed && current == index,
                        pickedId = pickedId.takeIf { revealed && current == index },
                        categoryCents = categoryCents,
                        summarySpend = state.summary?.spendCents ?: 0L,
                        previousSpend = state.summary?.previousSpendCents ?: 0L,
                        quietDays = state.summary?.quietDays ?: 0,
                        onPick = { optionId ->
                            if (!revealed) {
                                answers = answers + (question.id to optionId)
                                pickedId = optionId
                                revealed = true
                                score += CheckInScoring.guessBonus(question, optionId)
                            }
                        }
                    )
                    if (!revealed) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.checkin_tap_hint),
                            style = HomeType.caption,
                            color = Mute,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val onLast = index == questions.lastIndex
        HabitsAccentButton(
            label = stringResource(
                if (showSummary || (revealed && onLast)) R.string.checkin_finish else R.string.checkin_next
            ),
            enabled = showSummary || revealed,
            onClick = {
                when {
                    showSummary -> onComplete(answers)
                    revealed && onLast -> showSummary = true
                    revealed -> {
                        revealed = false
                        pickedId = null
                        index++
                    }
                }
            }
        )
        if (!showSummary) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.checkin_scoring_footer),
                style = HomeType.caption,
                color = Mute,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.checkin_skip),
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun SegmentBars(count: Int, index: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= index) Accent else SoftBlue)
            )
        }
    }
}

@Composable
private fun QuestionBody(
    question: QuizQuestion,
    revealed: Boolean,
    pickedId: String?,
    categoryCents: Map<String, Long>,
    summarySpend: Long,
    previousSpend: Long,
    quietDays: Int,
    onPick: (String) -> Unit
) {
    val maxCents = when (question.kind) {
        QuizKind.TOP_CATEGORY -> question.options.maxOfOrNull { categoryCents[it.id] ?: 0L }?.coerceAtLeast(1L) ?: 1L
        else -> 1L
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        question.options.forEach { option ->
            val isCorrect = option.id == question.correctOptionId
            val isPicked = option.id == pickedId
            val cents = categoryCents[option.id] ?: 0L
            val border = when {
                revealed && isCorrect -> Income
                revealed && isPicked -> Accent
                else -> Hairline
            }
            val background = when {
                revealed && isCorrect -> Income.copy(alpha = 0.08f)
                revealed && isPicked -> SoftBlue
                else -> CardWhite
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(background)
                    .border(2.dp, border, RoundedCornerShape(16.dp))
                    .clickable(enabled = !revealed) { onPick(option.id) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        optionLabel(question, option.id, option.label),
                        style = HomeType.rowTitle,
                        color = Ink,
                        modifier = Modifier.weight(1f)
                    )
                    if (revealed && question.kind == QuizKind.TOP_CATEGORY) {
                        Text(formatKsh(centsToKes(cents)), style = HomeType.caption, color = Ink, fontWeight = FontWeight.Bold)
                    }
                }
                if (question.kind == QuizKind.TOP_CATEGORY) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SoftBlue)
                    ) {
                        if (revealed) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth((cents.toFloat() / maxCents).coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isCorrect) Income else SoftBlue.copy(alpha = 0.9f))
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = revealed) {
            val correctLabel = question.options.firstOrNull { it.id == question.correctOptionId }?.label.orEmpty()
            val secondLabel = question.options
                .filterNot { it.id == question.correctOptionId }
                .maxByOrNull { categoryCents[it.id] ?: 0L }
                ?.label
            val verdict = when {
                pickedId == question.correctOptionId -> stringResource(R.string.checkin_spot_on)
                question.kind == QuizKind.TOP_CATEGORY &&
                    secondLabel != null &&
                    pickedId == question.options.firstOrNull { it.label == secondLabel }?.id ->
                    stringResource(R.string.checkin_close_guess, secondLabel)
                CheckInScoring.guessBonus(question, pickedId.orEmpty()) == CheckInScoring.CLOSE ->
                    stringResource(R.string.checkin_close_guess, correctLabel)
                else -> stringResource(R.string.checkin_not_quite, correctLabel)
            }
            val verdictColor = if (pickedId == question.correctOptionId) Income else Accent
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CardWhite)
                    .border(1.dp, Hairline, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(verdict, style = HomeType.rowTitle, color = verdictColor, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    insightFor(question, categoryCents[question.correctOptionId] ?: 0L, summarySpend, previousSpend, quietDays),
                    style = HomeType.body,
                    color = Mute
                )
            }
        }
    }
}

@Composable
private fun promptFor(kind: QuizKind): String = when (kind) {
    QuizKind.TOP_CATEGORY -> stringResource(R.string.quiz_top_category)
    QuizKind.WEEK_COMPARE -> stringResource(R.string.quiz_compare)
    QuizKind.QUIET_DAYS -> stringResource(R.string.quiz_quiet_days)
}

@Composable
private fun optionLabel(question: QuizQuestion, id: String, fallback: String): String {
    if (question.kind != QuizKind.WEEK_COMPARE) return fallback
    return when (id) {
        SpendCompare.MORE.name -> stringResource(R.string.quiz_more)
        SpendCompare.LESS.name -> stringResource(R.string.quiz_less)
        else -> stringResource(R.string.quiz_same)
    }
}

@Composable
private fun insightFor(
    question: QuizQuestion,
    categoryCents: Long,
    summarySpend: Long,
    previousSpend: Long,
    quietDays: Int
): String {
    val correct = question.options.firstOrNull { it.id == question.correctOptionId }?.label.orEmpty()
    return when (question.kind) {
        QuizKind.TOP_CATEGORY -> stringResource(
            R.string.quiz_insight_top,
            correct,
            formatKsh(centsToKes(categoryCents))
        )
        QuizKind.WEEK_COMPARE -> stringResource(
            R.string.quiz_insight_compare,
            formatKsh(centsToKes(summarySpend)),
            formatKsh(centsToKes(previousSpend))
        )
        QuizKind.QUIET_DAYS -> stringResource(R.string.quiz_insight_quiet, quietDays)
    }
}

@Composable
internal fun ChallengeCompletePage(
    card: ChallengeCardState,
    habits: HabitsUiState,
    stayedCents: Long,
    onNextChallenge: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val name = challengePlayingName(card.subject, card.payee, card.totalDays)
    val earned = habits.badges.count { it.earned }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Accent)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(OnAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(ActionMetrics),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = OnPastel, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            stringResource(R.string.complete_kept_label),
            style = HomeType.caption,
            color = SoftBlue,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            name,
            style = HomeType.greeting.copy(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
            color = OnAccent,
            textAlign = TextAlign.Center
        )
        if (stayedCents > 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.complete_stayed, formatKsh(centsToKes(stayedCents))),
                style = HomeType.body,
                color = SoftBlue,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(OnAccent.copy(alpha = 0.10f))
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.complete_points, card.points), style = HomeType.rowTitle, color = OnAccent)
                if (habits.level.number > 1) {
                    Text(
                        stringResource(R.string.complete_level_unlocked, habits.level.number),
                        style = HomeType.rowTitle,
                        color = ActionMetrics
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            HabitsProgressBar(fraction = 1f, track = OnAccent.copy(alpha = 0.18f), fill = ActionMetrics)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.complete_badge_line,
                    levelName(habits.level.number),
                    earned,
                    habits.badges.size
                ),
                style = HomeType.caption,
                color = SoftBlue
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardWhite)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Income.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Savings, contentDescription = null, tint = Income)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.complete_lock_copy), style = HomeType.body, color = Ink, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(CardWhite)
                .clickable {
                    val note = if (stayedCents > 0L) {
                        resources.getString(
                            R.string.complete_savings_note,
                            formatKsh(centsToKes(stayedCents)),
                            name
                        )
                    } else {
                        resources.getString(R.string.complete_share_text, name)
                    }
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.complete_move_savings))
                        putExtra(Intent.EXTRA_TEXT, note)
                    }
                    context.startActivity(Intent.createChooser(send, resources.getString(R.string.complete_move_savings)))
                    onDone()
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.complete_move_savings),
                style = HomeType.rowTitle,
                color = Accent,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlineAccentButton(
                label = stringResource(R.string.complete_share),
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.complete_share_text, name))
                    }
                    context.startActivity(Intent.createChooser(send, null))
                },
                modifier = Modifier.weight(1f)
            )
            OutlineAccentButton(
                label = stringResource(R.string.complete_next),
                onClick = onNextChallenge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun OutlineAccentButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, OnAccent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = HomeType.rowTitle, color = OnAccent)
    }
}

@Composable
internal fun MonthlyRecapPage(
    habits: HabitsUiState,
    quietDays: Int
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    var hideAmounts by remember { mutableStateOf(true) }
    val month = LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.UK)
    val (title, body) = when {
        quietDays >= 8 || habits.kept >= 2 ->
            stringResource(R.string.recap_personality_saver) to stringResource(R.string.recap_personality_saver_body)
        habits.kept >= 1 || quietDays >= 4 ->
            stringResource(R.string.recap_personality_steady) to stringResource(R.string.recap_personality_steady_body)
        else ->
            stringResource(R.string.recap_personality_starter) to stringResource(R.string.recap_personality_starter_body)
    }
    val dark = Ink
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(dark)
            .padding(horizontal = HabitsTokens.pagePad, vertical = 16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < 3) CardWhite else SoftBlue.copy(alpha = 0.35f))
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(ActionMetrics)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.recap_title, month),
                    style = HomeType.caption,
                    color = OnPastel,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(stringResource(R.string.recap_brand), style = HomeType.caption, color = OnPastel, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(stringResource(R.string.recap_personality_label), style = HomeType.body, color = OnPastel, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, style = HomeType.greeting.copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold), color = OnPastel)
            Spacer(modifier = Modifier.height(6.dp))
            Text(body, style = HomeType.body, color = OnPastel.copy(alpha = 0.85f))
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RecapStat(quietDays.toString(), stringResource(R.string.recap_quiet), Modifier.weight(1f))
                RecapStat(
                    stringResource(
                        R.string.habits_badges_count,
                        habits.kept,
                        habits.kept.coerceAtLeast(1)
                    ),
                    stringResource(R.string.recap_bills),
                    Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RecapStat(habits.kept.toString(), stringResource(R.string.recap_kept), Modifier.weight(1f))
                RecapStat(
                    if (hideAmounts || habits.feesDodgedCents <= 0L) "—" else "−${formatKsh(centsToKes(habits.feesDodgedCents))}",
                    stringResource(R.string.recap_fees),
                    Modifier.weight(1f),
                    valueColor = Income
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OnPastel)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Outlined.TrendingUp, contentDescription = null, tint = ActionMetrics)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.recap_next), style = HomeType.body, color = CardWhite)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.recap_hide_amounts),
                style = HomeType.rowTitle,
                color = CardWhite,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = hideAmounts,
                onCheckedChange = { hideAmounts = it },
                colors = SwitchDefaults.colors(checkedTrackColor = ActionMetrics, checkedThumbColor = OnPastel)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(ActionMetrics)
                    .clickable {
                        val text = resources.getString(
                            R.string.recap_share_text,
                            month,
                            quietDays,
                            habits.kept
                        )
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, null))
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.recap_share), style = HomeType.rowTitle, color = OnPastel, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, SoftBlue.copy(alpha = 0.4f), RoundedCornerShape(26.dp))
                    .clickable {
                        val text = buildString {
                            append(resources.getString(R.string.recap_title, month))
                            append('\n')
                            append(title)
                            append('\n')
                            append(body)
                            append('\n')
                            append(
                                resources.getString(
                                    R.string.recap_share_text,
                                    month,
                                    quietDays,
                                    habits.kept
                                )
                            )
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.recap_title, month))
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, resources.getString(R.string.recap_save)))
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.recap_save), style = HomeType.rowTitle, color = CardWhite)
            }
        }
    }
}

@Composable
private fun RecapStat(
    value: String,
    label: String,
    modifier: Modifier,
    valueColor: androidx.compose.ui.graphics.Color = OnPastel
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(14.dp)
    ) {
        Text(value, style = HomeType.greeting.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold), color = valueColor)
        Text(label, style = HomeType.caption, color = Mute, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun HabitsAccentButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(if (enabled) Accent else Accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = HomeType.rowTitle, color = OnAccent, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun CompactDayRing(day: Int, totalDays: Int, modifier: Modifier = Modifier) {
    val fraction = (day.toFloat() / totalDays.coerceAtLeast(1)).coerceIn(0f, 1f)
    val track = SoftBlue
    val fill = Income
    Box(modifier = modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(radius * 2f, radius * 2f)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = fill,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(
            "$day/$totalDays",
            style = HomeType.rowTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
            color = Ink
        )
    }
}
