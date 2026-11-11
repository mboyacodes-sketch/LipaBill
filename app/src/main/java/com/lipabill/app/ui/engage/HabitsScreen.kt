package com.lipabill.app.ui.engage

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lipabill.app.R
import com.lipabill.app.engage.HabitPattern
import com.lipabill.app.engage.centsToKes
import com.lipabill.app.ui.LipaPillButton
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.ui.theme.Canvas
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
import com.lipabill.app.viewmodel.ChallengeCardKind
import com.lipabill.app.viewmodel.ChallengeCardState
import com.lipabill.app.viewmodel.ChallengeSubject
import com.lipabill.app.viewmodel.EngageUiState
import com.lipabill.app.viewmodel.EngagementViewModel
import com.lipabill.app.viewmodel.HabitsUiState
import java.time.format.TextStyle
import java.util.Locale

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
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(HabitsPage.HOME) }
    var autoCompleteShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.openHabits() }
    LaunchedEffect(startQuiz) {
        if (startQuiz) {
            page = HabitsPage.CHECK_IN
            onQuizOpened()
        }
    }
    LaunchedEffect(state.card?.kind) {
        if (state.card?.kind == ChallengeCardKind.COMPLETED && !autoCompleteShown) {
            autoCompleteShown = true
            page = HabitsPage.COMPLETE
        }
    }
    BackHandler {
        when (page) {
            HabitsPage.HOME -> onBack()
            else -> page = HabitsPage.HOME
        }
    }
    val canStart = state.card?.kind != ChallengeCardKind.ACTIVE
    val startFromPage: (String) -> Unit = { templateId ->
        viewModel.startChallenge(templateId)
        page = HabitsPage.HOME
    }
    val showTopBar = page in setOf(
        HabitsPage.CHALLENGES,
        HabitsPage.NOTICED,
        HabitsPage.BADGES,
        HabitsPage.SETTINGS
    )
    Scaffold(
        modifier = modifier,
        containerColor = Canvas,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                when (page) {
                                    HabitsPage.CHALLENGES -> R.string.challenges_title
                                    HabitsPage.NOTICED -> R.string.habits_noticed
                                    HabitsPage.BADGES -> R.string.badges_title
                                    HabitsPage.SETTINGS -> R.string.habits_settings_title
                                    else -> R.string.habits_title
                                }
                            ),
                            style = HomeType.greeting.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { page = HabitsPage.HOME }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
                )
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState != HabitsPage.HOME) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
                } else {
                    fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "habitsPage",
            modifier = Modifier.padding(padding)
        ) { current ->
            when (current) {
                HabitsPage.CHECK_IN -> CheckInPage(
                    state = state,
                    onClose = { page = HabitsPage.HOME },
                    onComplete = { answers ->
                        viewModel.completeCheckIn(answers)
                        page = HabitsPage.HOME
                    }
                )
                HabitsPage.COMPLETE -> {
                    val card = state.card
                    if (card == null) {
                        LaunchedEffect(Unit) { page = HabitsPage.HOME }
                        Box(Modifier.fillMaxSize())
                    } else {
                        ChallengeCompletePage(
                            card = card,
                            habits = habits,
                            stayedCents = stayedEstimateCents(habits),
                            onNextChallenge = { page = HabitsPage.CHALLENGES },
                            onDone = { page = HabitsPage.HOME }
                        )
                    }
                }
                HabitsPage.RECAP -> MonthlyRecapPage(
                    habits = habits,
                    quietDays = state.summary?.quietDays ?: 0
                )
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = HabitsMaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = HabitsTokens.pagePad, vertical = 12.dp)
                    ) {
                        when (current) {
                            HabitsPage.HOME -> HabitsHome(
                                state = state,
                                habits = habits,
                                canStart = canStart,
                                onOpen = { page = it },
                                viewModel = viewModel
                            )
                            HabitsPage.CHALLENGES -> ChallengesPage(
                                habits = habits,
                                canStart = canStart,
                                onStart = startFromPage,
                                onStartDiy = { categoryId, days, capCents ->
                                    viewModel.startDiy(categoryId, days, capCents)
                                    page = HabitsPage.HOME
                                },
                                onDuoInvite = { viewModel.setDuoPending(true) }
                            )
                            HabitsPage.NOTICED -> NoticedPage(
                                habits = habits,
                                canStart = canStart,
                                onStart = startFromPage,
                                onNotMe = viewModel::dismissPattern,
                                onDismissCatchUp = viewModel::dismissCatchUp,
                                onTeach = viewModel::teachPayee
                            )
                            HabitsPage.BADGES -> BadgesPage(habits = habits)
                            HabitsPage.SETTINGS -> SettingsPage(
                                onOpenRecap = { page = HabitsPage.RECAP }
                            )
                            HabitsPage.CHECK_IN, HabitsPage.COMPLETE, HabitsPage.RECAP -> Unit
                        }
                        Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
                    }
                }
            }
        }
    }
}

private val HabitsMaxWidth = 560.dp

private val GENERIC_SUBJECTS = setOf(
    ChallengeSubject.QUIET,
    ChallengeSubject.FEE,
    ChallengeSubject.PAYDAY,
    ChallengeSubject.DIY,
    ChallengeSubject.OTHER
)

private fun stayedEstimateCents(habits: HabitsUiState): Long =
    habits.patterns.filterIsInstance<HabitPattern.FoodPeakDay>().firstOrNull()?.weeklyCents
        ?: habits.patterns.filterIsInstance<HabitPattern.FrequentPayee>().firstOrNull()?.let { it.cents / 4 }
        ?: 0L

@Composable
private fun HabitsHome(
    state: EngageUiState,
    habits: HabitsUiState,
    canStart: Boolean,
    onOpen: (HabitsPage) -> Unit,
    viewModel: EngagementViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.habits_title),
                style = HomeType.greeting.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                color = Ink
            )
            Text(stringResource(R.string.habits_subtitle), style = HomeType.body, color = Mute)
        }
        IconButton(
            onClick = { onOpen(HabitsPage.SETTINGS) },
            modifier = Modifier
                .size(44.dp)
                .border(1.dp, Hairline, CircleShape)
                .clip(CircleShape)
                .background(CardWhite)
        ) {
            Icon(
                Icons.Outlined.Tune,
                contentDescription = stringResource(R.string.habits_settings),
                tint = Ink
            )
        }
    }
    Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
    LevelCard(
        habits = habits,
        streak = state.streakCount,
        quietDays = state.summary?.quietDays ?: 0,
        feesDodgedCents = habits.feesDodgedCents,
        onClick = { onOpen(HabitsPage.BADGES) }
    )
    Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
    val card = state.card
    if (card != null) {
        SectionHeader(
            title = stringResource(R.string.habits_now_playing),
            action = stringResource(R.string.habits_all_challenges),
            onAction = { onOpen(HabitsPage.CHALLENGES) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        when (card.kind) {
            ChallengeCardKind.ACTIVE -> ActiveNowPlaying(
                card = card,
                tipDay = habits.patterns.filterIsInstance<HabitPattern.FoodPeakDay>().firstOrNull(),
                usualCents = stayedEstimateCents(habits)
            )
            ChallengeCardKind.COMPLETED -> CompletedTeaser(
                card = card,
                onOpen = { onOpen(HabitsPage.COMPLETE) }
            )
            else -> ChallengeStage(
                kind = card.kind,
                day = card.day,
                totalDays = card.totalDays,
                payee = card.payee,
                fuliza = card.subject == ChallengeSubject.FULIZA,
                generic = card.subject in GENERIC_SUBJECTS,
                onStart = { viewModel.startChallenge() },
                onRestart = viewModel::restartChallenge
            )
        }
        Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
    }
    habits.patterns.firstOrNull()?.let { pattern ->
        val more = (habits.patterns.size - 1).coerceAtLeast(0) +
            (if (habits.catchUp != null) 1 else 0) +
            (if (habits.teachCandidate != null) 1 else 0)
        SectionHeader(
            title = stringResource(R.string.habits_noticed),
            action = if (more > 0) {
                stringResource(R.string.habits_see_more, more.coerceAtLeast(1))
            } else {
                stringResource(R.string.habits_see_all)
            },
            onAction = { onOpen(HabitsPage.NOTICED) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        PatternCard(
            pattern = pattern,
            canStart = canStart,
            onStart = { viewModel.startChallenge(it) },
            onNotMe = { viewModel.dismissPattern(pattern.id) },
            foodBars = habits.foodDayBars,
            compact = true
        )
        Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
    }
    if (state.checkInDone) {
        CheckInDoneStrip(streakCount = maxOf(state.streakCount, state.nextStreakCount))
    } else {
        CheckInRow(onPlay = { onOpen(HabitsPage.CHECK_IN) })
    }
    if (habits.badges.isNotEmpty()) {
        Spacer(modifier = Modifier.height(HabitsTokens.sectionGap))
        SectionHeader(
            title = stringResource(R.string.habits_badges),
            action = stringResource(
                R.string.habits_badges_count,
                habits.badges.count { it.earned },
                habits.badges.size
            ),
            onAction = { onOpen(HabitsPage.BADGES) }
        )
        Spacer(modifier = Modifier.height(12.dp))
        BadgeStrip(habits.badges, onClick = { onOpen(HabitsPage.BADGES) })
    }
}

@Composable
private fun ActiveNowPlaying(
    card: ChallengeCardState,
    tipDay: HabitPattern.FoodPeakDay?,
    usualCents: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, Hairline, HabitsTokens.CardShape)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompactDayRing(day = card.day, totalDays = card.totalDays)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    challengeActiveBody(card.day, card.totalDays),
                    style = HomeType.caption,
                    color = Income,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    challengePlayingName(card.subject, card.payee, card.totalDays),
                    style = HomeType.rowTitle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
                    color = Ink
                )
                if (usualCents > 0L) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.challenge_playing_usual, formatKsh(centsToKes(usualCents))),
                        style = HomeType.body,
                        color = Mute
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        WeekdayLetterStrip(kind = card.kind, day = card.day, totalDays = card.totalDays)
        tipDay?.let { peak ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ActionMetrics)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(
                        R.string.challenge_tip_peak_day,
                        peak.day.getDisplayName(TextStyle.FULL, Locale.UK)
                    ),
                    style = HomeType.body,
                    color = OnPastel,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WeekdayLetterStrip(kind: ChallengeCardKind, day: Int, totalDays: Int) {
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    val days = totalDays.coerceIn(1, 7)
    val done = when (kind) {
        ChallengeCardKind.COMPLETED -> days
        ChallengeCardKind.ACTIVE -> (day - 1).coerceIn(0, days)
        else -> 0
    }
    val todayIndex = if (kind == ChallengeCardKind.ACTIVE) (day - 1).coerceIn(0, days - 1) else -1
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(days) { i ->
            val filled = i < done
            val isToday = i == todayIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        when {
                            filled -> Modifier.background(Income)
                            isToday -> Modifier
                                .background(CardWhite)
                                .border(2.dp, Income, RoundedCornerShape(10.dp))
                            else -> Modifier.background(Canvas)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    letters.getOrElse(i) { (i + 1).toString() },
                    style = HomeType.caption,
                    color = when {
                        filled -> OnAccent
                        isToday -> Income
                        else -> Mute
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CompletedTeaser(card: ChallengeCardState, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, Hairline, HabitsTokens.CardShape)
            .clickable(onClick = onOpen)
            .padding(18.dp)
    ) {
        Text(stringResource(R.string.complete_kept_label), style = HomeType.caption, color = Income, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            challengePlayingName(card.subject, card.payee, card.totalDays),
            style = HomeType.rowTitle.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
            color = Ink
        )
        if (card.points > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.challenge_points_earned, card.points),
                style = HomeType.rowTitle,
                color = Income
            )
        }
    }
}

@Composable
private fun ChallengeStage(
    kind: ChallengeCardKind,
    day: Int,
    totalDays: Int,
    payee: String?,
    fuliza: Boolean,
    generic: Boolean,
    onStart: () -> Unit,
    onRestart: () -> Unit
) {
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(kind) { busy = false }
    val title = challengeTitle(kind, day, totalDays)
    val body = challengeBody(kind, day, totalDays, payee, fuliza, generic)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, Hairline, HabitsTokens.CardShape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HabitMark(
            kind = kind,
            day = day,
            totalDays = totalDays,
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, style = HomeType.rowTitle, color = Ink, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(4.dp))
        Text(body, style = HomeType.body, color = Mute, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        PillButton(
            label = stringResource(
                if (kind == ChallengeCardKind.OFFER) R.string.challenge_start else R.string.challenge_restart
            ),
            filled = true,
            enabled = !busy,
            onClick = {
                busy = true
                if (kind == ChallengeCardKind.OFFER) onStart() else onRestart()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CheckInDoneStrip(streakCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, Hairline, HabitsTokens.CardShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
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
private fun CheckInRow(onPlay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HabitsTokens.CardShape)
            .background(CardWhite)
            .border(1.dp, Hairline, HabitsTokens.CardShape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SoftBlue),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Quiz, contentDescription = null, tint = Ink)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.checkin_offer_title), style = HomeType.rowTitle, color = Ink)
            Text(stringResource(R.string.checkin_offer_body), style = HomeType.body, color = Mute)
        }
        LipaPillButton(
            label = stringResource(R.string.checkin_play),
            onClick = onPlay,
            maxLines = 1
        )
    }
}
