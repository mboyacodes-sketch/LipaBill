package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.local.entity.ChallengeEntity
import com.lipabill.app.engage.ChallengeClock
import com.lipabill.app.engage.ChallengeStatus
import com.lipabill.app.engage.ChallengeTemplates
import com.lipabill.app.engage.CheckInStreak
import com.lipabill.app.engage.CheckInStreaks
import com.lipabill.app.engage.QuizGenerator
import com.lipabill.app.engage.QuizQuestion
import com.lipabill.app.engage.WeeklySummary
import com.lipabill.app.ui.util.displayLabel
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

data class ChallengeCardState(
    val kind: ChallengeCardKind,
    val day: Int = 1,
    val totalDays: Int = 3,
    /** Set for skip-a-payee challenges started from Insights. */
    val payee: String? = null
)

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

    init {
        refresh()
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

    fun startChallenge() {
        viewModelScope.launch {
            app.engagement.startFoodDeliveryChallenge() ?: return@launch
            val end = app.engagement.evaluate()
            if (end != null) app.engagementScheduler.scheduleChallengeEnd(end)
            else app.engagementScheduler.scheduleChallengeSweep()
        }
    }

    fun restartChallenge() {
        viewModelScope.launch {
            app.engagement.cancelActive()
            startChallenge()
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
        return when (val kind = challengeCardKind(ChallengeStatus.fromStored(latest.status))) {
            ChallengeCardKind.ACTIVE -> ChallengeCardState(
                kind = kind,
                day = ChallengeClock.dayNumber(latest.startAt, latest.endAt, System.currentTimeMillis(), zone),
                totalDays = total,
                payee = payee
            )
            else -> ChallengeCardState(kind = kind, totalDays = total, payee = payee)
        }
    }
}
