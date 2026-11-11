package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.local.entity.ChallengeEntity
import com.lipabill.app.engage.BadgeProgress
import com.lipabill.app.engage.CatchUp
import com.lipabill.app.data.local.entity.CheckInStreakEntity
import com.lipabill.app.engage.ChallengeClock
import com.lipabill.app.engage.ChallengeStatus
import com.lipabill.app.engage.ChallengeTemplates
import com.lipabill.app.engage.ChallengeTransaction
import com.lipabill.app.engage.CheckInStreak
import com.lipabill.app.engage.CheckInStreaks
import com.lipabill.app.engage.HabitBadge
import com.lipabill.app.engage.HabitBadges
import com.lipabill.app.engage.HabitLevel
import com.lipabill.app.engage.HabitLevels
import com.lipabill.app.engage.HabitPattern
import com.lipabill.app.engage.HabitPatterns
import com.lipabill.app.engage.HabitPoints
import com.lipabill.app.engage.QuizGenerator
import com.lipabill.app.engage.QuizQuestion
import com.lipabill.app.engage.WeeklySummary
import com.lipabill.app.ui.util.displayLabel
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChallengeCardKind { OFFER, ACTIVE, COMPLETED, BROKEN }

internal fun challengeCardKind(status: ChallengeStatus): ChallengeCardKind = when (status) {
    ChallengeStatus.ACTIVE -> ChallengeCardKind.ACTIVE
    ChallengeStatus.COMPLETED -> ChallengeCardKind.COMPLETED
    ChallengeStatus.BROKEN -> ChallengeCardKind.BROKEN
    ChallengeStatus.CANCELLED -> ChallengeCardKind.OFFER
}

enum class ChallengeSubject { FOOD_DELIVERY, PAYEE, FULIZA, QUIET, FEE, PAYDAY, DIY }

internal fun challengeSubject(templateId: String): ChallengeSubject = when {
    templateId == ChallengeTemplates.fulizaFree7Days.id ||
        ChallengeTemplates.fulizaUntilPaydayDays(templateId) != null -> ChallengeSubject.FULIZA
    templateId == ChallengeTemplates.zeroFee7Days.id -> ChallengeSubject.FEE
    templateId == ChallengeTemplates.quietDays7.id -> ChallengeSubject.QUIET
    templateId == ChallengeTemplates.paydayPause2Days.id -> ChallengeSubject.PAYDAY
    ChallengeTemplates.diyOf(templateId) != null -> ChallengeSubject.DIY
    ChallengeTemplates.payeeOf(templateId) != null -> ChallengeSubject.PAYEE
    templateId == ChallengeTemplates.foodDelivery3Days.id -> ChallengeSubject.FOOD_DELIVERY
    else -> ChallengeSubject.FOOD_DELIVERY
}

data class ChallengeCardState(
    val kind: ChallengeCardKind,
    val day: Int = 1,
    val totalDays: Int = 3,
    /** Set for skip-a-payee challenges started from Insights. */
    val payee: String? = null,
    val templateId: String = ChallengeTemplates.foodDelivery3Days.id,
    val subject: ChallengeSubject = ChallengeSubject.FOOD_DELIVERY,
    val points: Int = HabitPoints.forTemplate(templateId)
)

data class ChallengeOption(
    val templateId: String,
    val subject: ChallengeSubject,
    val payee: String?,
    val days: Int,
    val points: Int,
    /** Picked from the user's own patterns. */
    val suggested: Boolean
)

data class HabitsUiState(
    val points: Int = 0,
    val level: HabitLevel = HabitLevels.of(0),
    val kept: Int = 0,
    val badges: List<BadgeProgress> = emptyList(),
    /** Patterns the user has not waved off, strongest first. */
    val patterns: List<HabitPattern> = emptyList(),
    val catchUp: CatchUp? = null,
    val options: List<ChallengeOption> = emptyList(),
    val paymentsRead: Int = 0,
    val feesDodgedCents: Long = 0L,
    val learningScore: Int = 0,
    val foodDayBars: List<Int> = emptyList(),
    val taughtCount: Int = 0,
    val duoPending: Boolean = false,
    /** A shop paid often that the user has not yet said is a need or a want. */
    val teachCandidate: String? = null
)

private data class LedgerRead(
    val patterns: List<HabitPattern> = emptyList(),
    val regulars: List<HabitPattern.FrequentPayee> = emptyList(),
    val paymentsRead: Int = 0,
    val catchUp: CatchUp? = null,
    val window: List<ChallengeTransaction> = emptyList(),
    val foodDayBars: List<Int> = emptyList()
)

internal fun challengeOptions(
    patterns: List<HabitPattern>,
    regulars: List<HabitPattern.FrequentPayee>,
    diyIds: Set<String> = emptySet()
): List<ChallengeOption> {
    val suggested = when (val top = patterns.firstOrNull()) {
        is HabitPattern.FulizaLateMonth -> ChallengeTemplates.fulizaFree7Days.id
        is HabitPattern.FoodPeakDay -> ChallengeTemplates.foodDelivery3Days.id
        is HabitPattern.FrequentPayee -> ChallengeTemplates.skipPayee(top.payee).id
        is HabitPattern.PaydayRush -> ChallengeTemplates.paydayPause2Days.id
        else -> null
    }
    val catalog = buildList {
        add(ChallengeTemplates.fulizaFree7Days)
        add(ChallengeTemplates.foodDelivery3Days)
        add(ChallengeTemplates.quietDays7)
        add(ChallengeTemplates.zeroFee7Days)
        if (patterns.any { it is HabitPattern.PaydayRush }) {
            add(ChallengeTemplates.paydayPause2Days)
        }
        if (patterns.any { it is HabitPattern.FulizaLateMonth }) {
            add(ChallengeTemplates.fulizaUntilPayday(7))
        }
        addAll(regulars.map { ChallengeTemplates.skipPayee(it.payee) })
        addAll(diyIds.mapNotNull { ChallengeTemplates.byId(it) })
    }
    return catalog
        .distinctBy { it.id }
        .map { template ->
            ChallengeOption(
                templateId = template.id,
                subject = challengeSubject(template.id),
                payee = ChallengeTemplates.payeeOf(template.id),
                days = template.durationDays,
                points = HabitPoints.forTemplate(template.id),
                suggested = template.id == suggested
            )
        }
        .sortedByDescending { it.suggested }
}

data class EngageUiState(
    val card: ChallengeCardState? = null,
    val weekId: String = "",
    val checkInDone: Boolean = false,
    val streakCount: Int = 0,
    val nextStreakCount: Int = 0,
    val questions: List<QuizQuestion> = emptyList(),
    val summary: WeeklySummary? = null
)

class EngagementViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LipaBillApp
    private val summary = MutableStateFlow<WeeklySummary?>(null)
    private val checkInDone = MutableStateFlow(false)
    private val ledger = MutableStateFlow(LedgerRead())
    private val dismissedPatterns = MutableStateFlow(app.securePreferences.dismissedHabitPatterns)
    private val taughtPayees = MutableStateFlow(app.securePreferences.taughtPayeeCategories)
    private val customDiy = MutableStateFlow(app.securePreferences.customDiyChallenges)
    private val duoPending = MutableStateFlow(app.securePreferences.pendingDuoInvite)

    val uiState: StateFlow<EngageUiState> = combine(
        app.engagement.observeLatest(),
        app.engagement.observeStreak(),
        summary,
        checkInDone
    ) { latest, streak, week, done ->
        val streakCount = streak?.count ?: 0
        val nextStreak = if (week == null) {
            streakCount
        } else {
            CheckInStreaks.advance(
                CheckInStreak(
                    count = streakCount,
                    freezeUsed = streak?.freezeUsed == true,
                    lastWeekId = streak?.lastWeekId
                ),
                week.weekId
            ).count
        }
        EngageUiState(
            card = cardFor(latest),
            weekId = week?.weekId.orEmpty(),
            checkInDone = done,
            streakCount = streakCount,
            nextStreakCount = nextStreak,
            questions = week?.let { QuizGenerator.generate(it) { type -> type.displayLabel() } }.orEmpty(),
            summary = week
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EngageUiState())

    private data class HabitsCore(
        val kept: List<String>,
        val checkIns: Int,
        val streak: CheckInStreakEntity?,
        val read: LedgerRead,
        val dismissed: Set<String>
    )

    private val habitsCore: StateFlow<HabitsCore> = combine(
        app.engagement.observeKeptTemplateIds(),
        app.engagement.observeCheckInCount(),
        app.engagement.observeStreak(),
        ledger,
        dismissedPatterns
    ) { kept, checkIns, streak, read, dismissed ->
        HabitsCore(kept, checkIns, streak, read, dismissed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsCore(emptyList(), 0, null, LedgerRead(), emptySet()))

    val habits: StateFlow<HabitsUiState> = combine(
        habitsCore,
        taughtPayees,
        customDiy,
        duoPending,
        summary
    ) { core, taught, diy, duo, week ->
        val taughtCount = taught.size
        val points = HabitPoints.total(core.kept, core.checkIns, taughtPayees = taughtCount)
        val fees = HabitPatterns.feesDodgedCents(core.read.window, core.kept)
        val learning = HabitPatterns.learningScore(
            dismissedCount = core.dismissed.size,
            confirmedViaChallenge = core.kept.size,
            taughtPayees = taughtCount
        )
        // Duo needs a backend that does not exist yet; a badge for a pending invite would be hollow.
        val badges = HabitBadges.evaluate(
            keptTemplateIds = core.kept,
            checkIns = core.checkIns,
            streak = core.streak?.count ?: 0,
            alreadyEarned = app.securePreferences.earnedHabitBadges,
            quietDays = week?.quietDays ?: 0,
            taughtPayees = taughtCount,
            feesDodgedCents = fees,
            duoPending = duo
        ).filterNot { it.badge == HabitBadge.DUO_PENDING }
        rememberEarned(badges)
        val patterns = core.read.patterns.filterNot { it.id in core.dismissed }
        HabitsUiState(
            points = points,
            level = HabitLevels.of(points),
            kept = core.kept.size,
            badges = badges,
            patterns = patterns,
            catchUp = core.read.catchUp,
            options = challengeOptions(patterns, core.read.regulars, diy),
            paymentsRead = core.read.paymentsRead,
            feesDodgedCents = fees,
            learningScore = learning,
            foodDayBars = core.read.foodDayBars,
            taughtCount = taughtCount,
            duoPending = duo,
            teachCandidate = core.read.regulars
                .map { it.payee }
                .firstOrNull { payee -> taught.none { it.startsWith(HabitPatterns.payeeKey(payee) + "=") } }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    init {
        refresh()
    }

    /**
     * Habits screen only: reads patterns from the last 90 days and works out what happened
     * since the last visit. Home and Insights share this view model and must not move the visit.
     */
    fun openHabits(now: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val prefs = app.securePreferences
            val zone = ZoneId.systemDefault()
            val rows = app.engagement.transactionsSince(now - HabitPatterns.WINDOW_DAYS * DAY_MILLIS, now)
            val catchUp = HabitPatterns.catchUp(rows, prefs.habitsLastVisit, now)
            prefs.habitsLastVisit = now
            val essentials = app.essentialPayees.value
            ledger.value = LedgerRead(
                patterns = HabitPatterns.detect(rows, now, zone, essentials),
                regulars = HabitPatterns.regulars(rows, now, essentialKeys = essentials),
                paymentsRead = rows.size,
                catchUp = catchUp ?: ledger.value.catchUp,
                window = rows,
                foodDayBars = HabitPatterns.foodDayBars(rows, zone, now)
            )
        }
    }

    fun dismissCatchUp() {
        ledger.value = ledger.value.copy(catchUp = null)
    }

    /** "Not me": this pattern stops showing. */
    fun dismissPattern(id: String) {
        val next = dismissedPatterns.value + id
        app.securePreferences.dismissedHabitPatterns = next
        dismissedPatterns.value = next
    }

    /** Teach Habits which category a payee belongs to. */
    fun teachPayee(payee: String, categoryId: String) {
        val key = HabitPatterns.payeeKey(payee)
        if (key.isBlank() || categoryId.isBlank()) return
        val encoded = "$key=$categoryId"
        val without = taughtPayees.value.filterNot { it.startsWith("$key=") }.toSet()
        val next = without + encoded
        app.securePreferences.taughtPayeeCategories = next
        taughtPayees.value = next
        // A need is marked essential everywhere, so Habits and Insights stop suggesting a pause on it.
        app.setPayeeEssential(key, categoryId == TEACH_NEED)
        openHabits()
    }

    fun setDuoPending(pending: Boolean = true) {
        app.securePreferences.pendingDuoInvite = pending
        duoPending.value = pending
    }

    /** Build and start a DIY challenge; the template id is remembered for the catalog. */
    fun startDiy(categoryId: String, days: Int, capCents: Long) {
        val template = ChallengeTemplates.diy(categoryId, days, capCents)
        if (ChallengeTemplates.byId(template.id) == null) return
        val next = customDiy.value + template.id
        app.securePreferences.customDiyChallenges = next
        customDiy.value = next
        startChallenge(template.id)
    }

    private fun rememberEarned(badges: List<BadgeProgress>) {
        val stored = app.securePreferences.earnedHabitBadges
        val next = stored + badges.filter { it.earned }.map { it.badge.id }
        if (next != stored) app.securePreferences.earnedHabitBadges = next
    }

    fun refresh() {
        viewModelScope.launch {
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
            val built = app.engagement.weeklySummary()
            summary.value = built
            checkInDone.value = app.engagement.hasCheckIn(built.weekId)
        }
    }

    fun startChallenge(templateId: String = ChallengeTemplates.foodDelivery3Days.id) {
        viewModelScope.launch {
            app.engagement.startChallenge(templateId) ?: return@launch
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
            else app.engagementScheduler.scheduleChallengeSweep()
        }
    }

    /** Starts the same challenge again, so a broken Fuliza or payee pause stays that kind. */
    fun restartChallenge() {
        val templateId = uiState.value.card?.templateId ?: ChallengeTemplates.foodDelivery3Days.id
        viewModelScope.launch {
            app.engagement.cancelActive()
            startChallenge(templateId)
        }
    }

    fun completeCheckIn(selected: Map<String, String>) {
        val week = summary.value ?: return
        checkInDone.value = true
        viewModelScope.launch {
            app.engagement.completeCheckIn(
                weekId = week.weekId,
                answers = selected.entries.joinToString("\n") { "${it.key}=${it.value}" },
                now = System.currentTimeMillis()
            )
        }
    }

    private fun cardFor(latest: ChallengeEntity?): ChallengeCardState {
        if (latest == null || latest.status == ChallengeStatus.CANCELLED.name) {
            return ChallengeCardState(ChallengeCardKind.OFFER)
        }
        val template = ChallengeTemplates.byId(latest.templateId)
        val total = template?.durationDays ?: 3
        val payee = ChallengeTemplates.payeeOf(latest.templateId)
        val zone = ChallengeClock.zoneOf(latest.zoneId)
        val subject = challengeSubject(latest.templateId)
        return when (val kind = challengeCardKind(ChallengeStatus.fromStored(latest.status))) {
            ChallengeCardKind.ACTIVE -> ChallengeCardState(
                kind = kind,
                day = ChallengeClock.dayNumber(latest.startAt, latest.endAt, System.currentTimeMillis(), zone),
                totalDays = total,
                payee = payee,
                templateId = latest.templateId,
                subject = subject
            )
            else -> ChallengeCardState(
                kind = kind,
                totalDays = total,
                payee = payee,
                templateId = latest.templateId,
                subject = subject
            )
        }
    }
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

/** Answers to "Teach LipaBill": a need (essential) or a want. */
const val TEACH_NEED = "need"
const val TEACH_WANT = "want"
