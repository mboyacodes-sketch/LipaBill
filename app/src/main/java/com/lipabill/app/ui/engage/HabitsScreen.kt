package com.lipabill.app.ui.engage

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lipabill.app.R
import com.lipabill.app.engage.QuizKind
import com.lipabill.app.engage.QuizQuestion
import com.lipabill.app.engage.SpendCompare
import com.lipabill.app.engage.centsToKes
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.viewmodel.ChallengeCardKind
import com.lipabill.app.viewmodel.EngageUiState
import com.lipabill.app.viewmodel.EngagementViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    startQuiz: Boolean,
    onQuizOpened: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EngagementViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var quizOpen by remember { mutableStateOf(false) }
    LaunchedEffect(startQuiz) {
        if (startQuiz) {
            quizOpen = true
            onQuizOpened()
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.habits_title), style = HomeType.greeting) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page, vertical = Space.gap)
        ) {
            Text(
                stringResource(R.string.habits_subtitle),
                style = HomeType.body,
                color = Mute
            )
            Spacer(modifier = Modifier.height(Space.section))
            val card = state.card
            if (card != null) {
                ChallengeStage(
                    kind = card.kind,
                    day = card.day,
                    totalDays = card.totalDays,
                    onStart = viewModel::startChallenge,
                    onRestart = viewModel::restartChallenge
                )
                Spacer(modifier = Modifier.height(Space.section))
            }
            Text(stringResource(R.string.habits_this_week), style = HomeType.section, color = Ink)
            Spacer(modifier = Modifier.height(Space.gap))
            if (state.checkInDone && !quizOpen) {
                CheckInDoneStrip(
                    streakCount = maxOf(state.streakCount, state.nextStreakCount)
                )
            } else {
                CheckInStage(
                    state = state,
                    expanded = quizOpen,
                    onOpen = { quizOpen = true },
                    onDismiss = { quizOpen = false },
                    onComplete = { answers ->
                        viewModel.completeCheckIn(answers)
                        quizOpen = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ChallengeStage(
    kind: ChallengeCardKind,
    day: Int,
    totalDays: Int,
    onStart: () -> Unit,
    onRestart: () -> Unit
) {
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(kind) { busy = false }
    val title = challengeTitle(kind, day, totalDays)
    val body = challengeBody(kind, day, totalDays)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CardWhite)
            .padding(horizontal = Space.card, vertical = Space.cardH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HabitMark(
            kind = kind,
            day = day,
            totalDays = totalDays,
            modifier = Modifier.size(148.dp)
        )
        if (kind == ChallengeCardKind.ACTIVE || kind == ChallengeCardKind.COMPLETED) {
            Spacer(modifier = Modifier.height(Space.gap))
            ChallengeDayStrip(kind = kind, day = day, totalDays = totalDays)
        }
        Spacer(modifier = Modifier.height(Space.gap))
        Text(title, style = HomeType.greeting, color = Ink, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        Text(body, style = HomeType.body, color = Mute, textAlign = TextAlign.Center)
        if (kind != ChallengeCardKind.ACTIVE) {
            Spacer(modifier = Modifier.height(Space.block))
            AccentButton(
                label = stringResource(
                    if (kind == ChallengeCardKind.OFFER) R.string.challenge_start else R.string.challenge_restart
                ),
                enabled = !busy,
                onClick = {
                    busy = true
                    if (kind == ChallengeCardKind.OFFER) onStart() else onRestart()
                }
            )
        }
    }
}

@Composable
private fun ChallengeDayStrip(
    kind: ChallengeCardKind,
    day: Int,
    totalDays: Int
) {
    val days = totalDays.coerceAtLeast(1)
    val done = when (kind) {
        ChallengeCardKind.COMPLETED -> days
        ChallengeCardKind.ACTIVE -> (day - 1).coerceIn(0, days)
        else -> 0
    }
    val todayIndex = if (kind == ChallengeCardKind.ACTIVE) (day - 1).coerceIn(0, days - 1) else -1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(days) { i ->
            if (i > 0) Spacer(modifier = Modifier.width(10.dp))
            val filled = i < done
            val isToday = i == todayIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(if (isToday) 14.dp else 12.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                filled || isToday -> Accent
                                else -> CardWhite
                            }
                        )
                        .then(
                            if (!filled && !isToday) {
                                Modifier.border(1.6.dp, Hairline, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = (i + 1).toString(),
                    style = HomeType.caption,
                    color = if (filled || isToday) Ink else Mute,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun CheckInDoneStrip(streakCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CardWhite)
            .padding(horizontal = Space.card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WeekGlyph(modifier = Modifier.width(96.dp).height(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            stringResource(R.string.checkin_done_strip, streakCount),
            style = HomeType.rowTitle,
            color = Ink,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CheckInStage(
    state: EngageUiState,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onComplete: (Map<String, String>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CardWhite)
            .padding(Space.card)
    ) {
        if (!expanded) {
            WeekGlyph(modifier = Modifier.fillMaxWidth().height(28.dp))
            Spacer(modifier = Modifier.height(Space.gap))
            Text(stringResource(R.string.checkin_offer_title), style = HomeType.rowTitle, color = Ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.checkin_offer_body), style = HomeType.body, color = Mute)
            Spacer(modifier = Modifier.height(Space.block))
            AccentButton(label = stringResource(R.string.checkin_open), onClick = onOpen)
        } else if (state.questions.isEmpty()) {
            Text(stringResource(R.string.checkin_empty_title), style = HomeType.rowTitle, color = Ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text(stringResource(R.string.checkin_empty_body), style = HomeType.body, color = Mute)
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                stringResource(R.string.checkin_skip),
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        } else {
            QuizFlow(state = state, onDismiss = onDismiss, onComplete = onComplete)
        }
    }
}

@Composable
private fun QuizFlow(
    state: EngageUiState,
    onDismiss: () -> Unit,
    onComplete: (Map<String, String>) -> Unit
) {
    val questions = state.questions
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var pickedId by remember { mutableStateOf<String?>(null) }
    var showSummary by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var streakTarget by remember { mutableIntStateOf(0) }
    val streak by animateIntAsState(streakTarget, tween(350), label = "streak")
    LaunchedEffect(showSummary) {
        if (showSummary) streakTarget = state.nextStreakCount
    }
    val step = if (showSummary) -1 else index
    Column(modifier = Modifier.fillMaxWidth()) {
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
            label = "quiz"
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
                Column {
                    QuestionDots(count = questions.size, index = current)
                    Spacer(modifier = Modifier.height(Space.gap))
                    Text(
                        stringResource(R.string.checkin_question_progress, current + 1, questions.size),
                        style = HomeType.caption,
                        color = Mute
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    QuestionBody(
                        question = question,
                        revealed = revealed && current == index,
                        pickedId = pickedId.takeIf { revealed && current == index },
                        categoryCents = state.summary?.byCategory
                            ?.firstOrNull { it.type.name == question.correctOptionId }
                            ?.cents ?: 0L,
                        summarySpend = state.summary?.spendCents ?: 0L,
                        previousSpend = state.summary?.previousSpendCents ?: 0L,
                        quietDays = state.summary?.quietDays ?: 0,
                        onPick = { optionId ->
                            if (!revealed) {
                                answers = answers + (question.id to optionId)
                                pickedId = optionId
                                revealed = true
                            }
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Space.block))
        val onLast = index == questions.lastIndex
        AccentButton(
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
private fun QuestionDots(count: Int, index: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(
                modifier = Modifier
                    .size(if (i == index) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= index) Accent else Hairline)
            )
        }
    }
}

@Composable
private fun AccentButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Accent else Accent.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = HomeType.rowTitle, color = OnAccent)
    }
}

@Composable
private fun QuestionBody(
    question: QuizQuestion,
    revealed: Boolean,
    pickedId: String?,
    categoryCents: Long,
    summarySpend: Long,
    previousSpend: Long,
    quietDays: Int,
    onPick: (String) -> Unit
) {
    Column {
        Text(promptFor(question.kind), style = HomeType.rowTitle, color = Ink)
        Spacer(modifier = Modifier.height(10.dp))
        question.options.forEach { option ->
            val isCorrect = option.id == question.correctOptionId
            val isPicked = option.id == pickedId
            val background = when {
                revealed && isCorrect -> Accent
                revealed && isPicked && !isCorrect -> Expense.copy(alpha = 0.14f)
                else -> SoftBlue
            }
            val color = when {
                revealed && isCorrect -> OnAccent
                revealed && isPicked && !isCorrect -> Expense
                else -> Ink
            }
            Text(
                text = optionLabel(question, option.id, option.label),
                style = HomeType.body,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(background)
                    .clickable(enabled = !revealed) { onPick(option.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
        AnimatedVisibility(visible = revealed) {
            Text(
                insightFor(question, categoryCents, summarySpend, previousSpend, quietDays),
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier.padding(top = 4.dp)
            )
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
            formatKes(centsToKes(categoryCents))
        )
        QuizKind.WEEK_COMPARE -> stringResource(
            R.string.quiz_insight_compare,
            formatKes(centsToKes(summarySpend)),
            formatKes(centsToKes(previousSpend))
        )
        QuizKind.QUIET_DAYS -> stringResource(R.string.quiz_insight_quiet, quietDays)
    }
}
