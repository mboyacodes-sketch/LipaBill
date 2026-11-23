package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.local.entity.ChallengeEntity
import com.lipabill.app.data.repository.EngagementRepository
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
import com.lipabill.app.engage.RecurringChoice
import com.lipabill.app.engage.RecurringPatterns
import com.lipabill.app.engage.SpendCategory
import com.lipabill.app.engage.WeeklySummary
import com.lipabill.app.ui.util.displayLabel
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChallengeCardKind { OFFER, ACTIVE, COMPLETED, BROKEN }

internal fun challengeCardKind(status: ChallengeStatus): ChallengeCardKind = when (status) {
    ChallengeStatus.ACTIVE -> ChallengeCardKind.ACTIVE
    ChallengeStatus.COMPLETED -> ChallengeCardKind.COMPLETED
    ChallengeStatus.BROKEN -> ChallengeCardKind.BROKEN
    ChallengeStatus.CANCELLED -> ChallengeCardKind.OFFER
}

enum class ChallengeSubject { FOOD_DELIVERY, PAYEE, FULIZA, QUIET, FEE, PAYDAY, DIY, OTHER }

internal fun challengeSubject(templateId: String): ChallengeSubject = when {
    templateId == ChallengeTemplates.fulizaFree7Days.id ||
        ChallengeTemplates.fulizaUntilPaydayDays(templateId) != null -> ChallengeSubject.FULIZA
    templateId == ChallengeTemplates.zeroFee7Days.id -> ChallengeSubject.FEE
    templateId == ChallengeTemplates.quietDays7.id -> ChallengeSubject.QUIET
    templateId == ChallengeTemplates.paydayPause2Days.id -> ChallengeSubject.PAYDAY
    ChallengeTemplates.diyOf(templateId) != null -> ChallengeSubject.DIY
    ChallengeTemplates.payeeOf(templateId) != null -> ChallengeSubject.PAYEE
    templateId == ChallengeTemplates.foodDelivery3Days.id -> ChallengeSubject.FOOD_DELIVERY
    else -> ChallengeSubject.OTHER
}

data class ChallengeCardState(
    val kind: ChallengeCardKind,
    val day: Int = 1,
    val totalDays: Int = 3,
    /** Set for skip-a-payee challenges started from Insights. */
    val payee: String? = null,
    val templateId: String = ChallengeTemplates.foodDelivery3Days.id,
    val subject: ChallengeSubject = ChallengeSubject.FOOD_DELIVERY,
    val points: Int = HabitPoints.forTemplate(templateId),
    /** Room row id — used to acknowledge a completed celebration once. */
    val challengeId: Long = 0L,
    val endAt: Long = 0L
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
    val teachCandidate: String? = null,
    /** Pending “Potential recurring payment” to confirm or dismiss. */
    val potentialRecurring: List<com.lipabill.app.engage.RecurringPattern> = emptyList()
)

private data class LedgerRead(
    val patterns: List<HabitPattern> = emptyList(),
    val regulars: List<HabitPattern.FrequentPayee> = emptyList(),
    val paymentsRead: Int = 0,
    val catchUp: CatchUp? = null,
    val window: List<ChallengeTransaction> = emptyList(),
    val foodDayBars: List<Int> = emptyList(),
    val potentialRecurring: List<com.lipabill.app.engage.RecurringPattern> = emptyList()
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
    /** Other ACTIVE challenges under the primary card (cap leaves room for 2). */
    val otherActive: List<ChallengeCardState> = emptyList(),
    /** Template ids of every running challenge — catalog disables these. */
    val activeTemplateIds: Set<String> = emptySet(),
    val weekId: String = "",
    val checkInDone: Boolean = false,
    val streakCount: Int = 0,
    val nextStreakCount: Int = 0,
    val questions: List<QuizQuestion> = emptyList(),
    val summary: WeeklySummary? = null,
    /** True until the first engagement emission — drives skeleton placeholders. */
    val isLoading: Boolean = true
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
    private val acknowledgedCompletedId =
        MutableStateFlow(app.securePreferences.acknowledgedCompletedChallengeId)
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private data class ChallengeBundle(
        val latest: ChallengeEntity?,
        val active: List<ChallengeEntity>,
        val ackId: Long
    )

    private val challengeBundle: StateFlow<ChallengeBundle> = combine(
        app.engagement.observeLatest(),
        app.engagement.observeActive(),
        acknowledgedCompletedId
    ) { latest, active, ackId ->
        ChallengeBundle(latest, active, ackId)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ChallengeBundle(null, emptyList(), acknowledgedCompletedId.value)
    )

    val uiState: StateFlow<EngageUiState> = combine(
        challengeBundle,
        app.engagement.observeStreak(),
        summary,
        checkInDone
    ) { bundle, streak, week, done ->
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
        val now = System.currentTimeMillis()
        val primaryEntity = pickPrimaryChallenge(bundle.latest, bundle.active, bundle.ackId, now)
        val card = cardFor(primaryEntity, bundle.ackId, now)
        val otherActive = bundle.active
            .filter { it.id != card.challengeId }
            .map { cardFor(it, bundle.ackId, now) }
        EngageUiState(
            card = card,
            otherActive = otherActive,
            activeTemplateIds = bundle.active.map { it.templateId }.toSet(),
            weekId = week?.weekId.orEmpty(),
            checkInDone = done,
            streakCount = streakCount,
            nextStreakCount = nextStreak,
            questions = week?.let { QuizGenerator.generate(it) { type -> type.displayLabel() } }.orEmpty(),
            summary = week,
            isLoading = false
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
                .firstOrNull { payee -> taught.none { it.startsWith(HabitPatterns.payeeKey(payee) + "=") } },
            potentialRecurring = core.read.potentialRecurring
        )
    }
        .onEach { state -> rememberEarned(state.badges) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    init {
        refresh()
    }

    /**
     * Habits screen only: reads patterns from the last 90 days and works out what happened
     * since the last visit. Home and Insights share this view model and must not move the visit.
     */
    fun openHabits(now: Long = System.currentTimeMillis()) {
        // Re-evaluate active challenges so end / break shows before patterns load.
        refresh()
        viewModelScope.launch(Dispatchers.Default) {
            val prefs = app.securePreferences
            val zone = ZoneId.systemDefault()
            val rows = app.engagement.transactionsSince(now - HabitPatterns.WINDOW_DAYS * DAY_MILLIS, now)
            val essentials = app.essentialPayees.value
            val choices = app.recurringPatternChoices.value
            val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val recurring = RecurringPatterns.detectFromChallenge(
                rows = rows,
                today = today,
                zone = zone,
                choices = choices,
                essentials = essentials
            )
            val catchUp = HabitPatterns.catchUp(
                rows,
                prefs.habitsLastVisit,
                now,
                recurringChoices = choices,
                essentials = essentials
            )
            prefs.habitsLastVisit = now
            val habitRegulars = HabitPatterns.regulars(rows, now, zone, essentialKeys = essentials)
                .filterNot { shop ->
                    RecurringPatterns.isDismissed(
                        HabitPatterns.payeeKey(shop.payee),
                        shop.payee,
                        choices
                    )
                }
            val patterns = HabitPatterns.detect(rows, now, zone, essentials)
            val bars = HabitPatterns.foodDayBars(rows, zone, now)
            ledger.value = LedgerRead(
                patterns = patterns,
                regulars = habitRegulars,
                paymentsRead = rows.size,
                // Prefer a fresh catch-up; null means none (or already dismissed this session).
                catchUp = catchUp,
                window = rows,
                foodDayBars = bars,
                potentialRecurring = RecurringPatterns.potential(recurring)
            )
        }
    }

    /** Mark the completed celebration as seen so Habits does not reopen it every visit. */
    fun acknowledgeCompletedChallenge() {
        val card = uiState.value.card ?: return
        if (card.kind != ChallengeCardKind.COMPLETED || card.challengeId == 0L) return
        app.securePreferences.acknowledgedCompletedChallengeId = card.challengeId
        acknowledgedCompletedId.value = card.challengeId
    }

    fun setRecurringChoice(key: String, confirmed: Boolean) {
        app.setRecurringChoice(
            key,
            if (confirmed) RecurringChoice.Confirmed else RecurringChoice.Dismissed
        )
        openHabits()
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
        // Mirror into Insights spend overrides so receipts / category totals agree.
        when (categoryId) {
            TEACH_NEED -> app.setSpendCategoryOverride(key, SpendCategory.ESSENTIALS)
            TEACH_WANT -> app.setSpendCategoryOverride(key, SpendCategory.SHOPPING)
            else -> SpendCategory.fromId(categoryId)?.let { app.setSpendCategoryOverride(key, it) }
        }
        openHabits()
    }

    /**
     * Duo tracking has no backend yet. Invite still shares a message from the UI;
     * prefs are kept for a future badge without earning one today.
     */
    fun setDuoPending(pending: Boolean = true) {
        app.securePreferences.pendingDuoInvite = pending
        duoPending.value = pending
    }

    /** Build and start a DIY challenge; the template id is remembered for the catalog. */
    fun startDiy(
        categoryId: String,
        days: Int,
        capCents: Long,
        onStarted: (() -> Unit)? = null
    ) {
        val template = ChallengeTemplates.diy(categoryId, days, capCents)
        if (ChallengeTemplates.byId(template.id) == null) return
        val next = customDiy.value + template.id
        app.securePreferences.customDiyChallenges = next
        customDiy.value = next
        startChallenge(template.id, onStarted)
    }

    private fun rememberEarned(badges: List<BadgeProgress>) {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = app.securePreferences.earnedHabitBadges
            val next = stored + badges.filter { it.earned }.map { it.badge.id }
            if (next != stored) app.securePreferences.earnedHabitBadges = next
        }
    }

    fun refresh() {
        // Shared prefs key with Insights — keep in-memory ack in sync across Nav destinations.
        acknowledgedCompletedId.value = app.securePreferences.acknowledgedCompletedChallengeId
        viewModelScope.launch {
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
            val built = app.engagement.weeklySummary()
            summary.value = built
            checkInDone.value = app.engagement.hasCheckIn(built.weekId)
        }
    }

    /**
     * Starts [templateId] alongside any other actives (up to [EngagementRepository.MAX_ACTIVE]).
     * Same template while running is a no-op. [onStarted] runs only on a real insert.
     */
    fun startChallenge(
        templateId: String = ChallengeTemplates.foodDelivery3Days.id,
        onStarted: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            val active = app.engagement.listActive()
            if (active.any { it.templateId == templateId }) {
                _actionMessage.value = "That challenge is already running."
                return@launch
            }
            if (active.size >= EngagementRepository.MAX_ACTIVE) {
                _actionMessage.value = "You already have 3 challenges running."
                return@launch
            }
            val started = app.engagement.startChallenge(templateId)
            if (started == null) {
                _actionMessage.value = "Couldn't start that challenge. Try again."
                return@launch
            }
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
            else app.engagementScheduler.scheduleChallengeSweep()
            onStarted?.invoke()
        }
    }

    /** Starts the same challenge again, so a broken Fuliza or payee pause stays that kind. */
    fun restartChallenge() {
        val card = uiState.value.card
        val templateId = card?.templateId ?: ChallengeTemplates.foodDelivery3Days.id
        val challengeId = card?.challengeId ?: 0L
        viewModelScope.launch {
            if (challengeId != 0L) {
                app.engagement.cancelChallenge(challengeId)
            }
            val started = app.engagement.startChallenge(templateId)
            if (started == null) {
                _actionMessage.value = "Couldn't restart that challenge. Try again."
                return@launch
            }
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
            else app.engagementScheduler.scheduleChallengeSweep()
        }
    }

    fun completeCheckIn(selected: Map<String, String>) {
        if (checkInDone.value) return
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

    private fun cardFor(
        latest: ChallengeEntity?,
        acknowledgedId: Long = acknowledgedCompletedId.value,
        now: Long = System.currentTimeMillis()
    ): ChallengeCardState {
        if (latest == null || latest.status == ChallengeStatus.CANCELLED.name) {
            return ChallengeCardState(ChallengeCardKind.OFFER)
        }
        val template = ChallengeTemplates.byId(latest.templateId)
        val total = template?.durationDays ?: 3
        val payee = ChallengeTemplates.payeeOf(latest.templateId)
        val zone = ChallengeClock.zoneOf(latest.zoneId)
        val subject = challengeSubject(latest.templateId)
        val status = ChallengeStatus.fromStored(latest.status)
        // After the user dismisses the celebration (or a few days pass), show a fresh offer.
        if (status == ChallengeStatus.COMPLETED &&
            (latest.id == acknowledgedId || now - latest.endAt > COMPLETED_CARD_MILLIS)
        ) {
            return ChallengeCardState(ChallengeCardKind.OFFER)
        }
        return when (val kind = challengeCardKind(status)) {
            ChallengeCardKind.ACTIVE -> ChallengeCardState(
                kind = kind,
                day = ChallengeClock.dayNumber(latest.startAt, latest.endAt, now, zone),
                totalDays = total,
                payee = payee,
                templateId = latest.templateId,
                subject = subject,
                challengeId = latest.id,
                endAt = latest.endAt
            )
            else -> ChallengeCardState(
                kind = kind,
                totalDays = total,
                payee = payee,
                templateId = latest.templateId,
                subject = subject,
                challengeId = latest.id,
                endAt = latest.endAt
            )
        }
    }
}

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
private const val COMPLETED_CARD_MILLIS = ChallengeCardAge.RECENT_MILLIS

/** Answers to "Teach LipaBill": a need (essential) or a want. */
const val TEACH_NEED = "need"
const val TEACH_WANT = "want"
