package com.lipabill.app.data.repository

import com.lipabill.app.data.local.dao.ChallengeDao
import com.lipabill.app.data.local.dao.TransactionDao
import com.lipabill.app.data.local.dao.WindowTransactionRow
import com.lipabill.app.data.local.entity.ChallengeAchievementEntity
import com.lipabill.app.data.local.entity.ChallengeEntity
import com.lipabill.app.data.local.entity.CheckInStreakEntity
import com.lipabill.app.data.local.entity.WeeklyCheckInEntity
import com.lipabill.app.engage.CategoryRules
import com.lipabill.app.engage.ChallengeClock
import com.lipabill.app.engage.ChallengeEvaluation
import com.lipabill.app.engage.ChallengeEvaluator
import com.lipabill.app.engage.ChallengeSnapshot
import com.lipabill.app.engage.ChallengeStatus
import com.lipabill.app.engage.ChallengeTemplate
import com.lipabill.app.engage.ChallengeTemplates
import com.lipabill.app.engage.ChallengeTransaction
import com.lipabill.app.engage.CheckInStreak
import com.lipabill.app.engage.CheckInStreaks
import com.lipabill.app.engage.QuizQuestion
import com.lipabill.app.engage.WeeklySummary
import com.lipabill.app.engage.WeeklySummaryBuilder
import com.lipabill.app.engage.kesToCents
import com.lipabill.app.metrics.AppMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EngagementRepository(
    private val challenges: ChallengeDao,
    private val transactions: TransactionDao
) {
    fun observeActive() = challenges.observeActive()

    fun observeLatest() = challenges.observeLatest()

    fun observeStreak() = challenges.observeStreak()

    fun observeCheckIn(weekId: String) = challenges.observeCheckIn(weekId)

    suspend fun hasCheckIn(weekId: String): Boolean = withContext(Dispatchers.IO) {
        challenges.checkIn(weekId) != null
    }

    suspend fun startFoodDeliveryChallenge(now: Long = System.currentTimeMillis()): Long? =
        start(ChallengeTemplates.foodDelivery3Days, now)

    suspend fun start(template: ChallengeTemplate, now: Long): Long? = withContext(Dispatchers.IO) {
        val existing = challenges.listEvaluable(now, ChallengeEvaluator.GRACE_MILLIS)
            .firstOrNull { it.status == ChallengeStatus.ACTIVE.name }
        if (existing != null) return@withContext existing.id
        val zone = ChallengeClock.deviceZone()
        val (start, end) = ChallengeClock.windowFrom(now, template.durationDays, zone)
        val id = challenges.insertChallenge(
            ChallengeEntity(
                templateId = template.id,
                startAt = start,
                endAt = end,
                zoneId = zone.id,
                status = ChallengeStatus.ACTIVE.name
            )
        )
        AppMetrics.challengeStarted(template.id)
        id
    }

    suspend fun cancelActive(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val active = challenges.listEvaluable(now, ChallengeEvaluator.GRACE_MILLIS)
            .filter { it.status == ChallengeStatus.ACTIVE.name }
        for (row in active) {
            challenges.updateOutcome(
                id = row.id,
                status = ChallengeStatus.CANCELLED.name,
                breakingTransactionId = row.breakingTransactionId,
                completedAt = row.completedAt,
                brokenAt = row.brokenAt
            )
        }
    }

    /** @return the end instant of a challenge that is still active, if any. */
    suspend fun evaluate(now: Long = System.currentTimeMillis()): Long? = withContext(Dispatchers.IO) {
        val rows = challenges.listEvaluable(now, ChallengeEvaluator.GRACE_MILLIS)
        var nextEnd: Long? = null
        for (row in rows) {
            val template = ChallengeTemplates.byId(row.templateId) ?: continue
            val rule = CategoryRules.byId(template.categoryId) ?: continue
            val window = transactions.listBetween(row.startAt, row.endAt)
            val result = ChallengeEvaluator.evaluate(
                challenge = row.toSnapshot(),
                transactions = window.map { it.toChallengeTransaction() },
                now = now,
                rule = rule
            )
            apply(row, result, now)
            if (result.status == ChallengeStatus.ACTIVE) {
                val end = row.endAt
                nextEnd = if (nextEnd == null) end else minOf(nextEnd, end)
            }
        }
        nextEnd
    }

    suspend fun weeklySummary(now: Long = System.currentTimeMillis()): WeeklySummary =
        withContext(Dispatchers.IO) {
            val zone = ChallengeClock.deviceZone()
            val weekId = ChallengeClock.isoWeekId(now, zone)
            val (start, end) = ChallengeClock.isoWeekRange(now, zone)
            val previousId = ChallengeClock.previousWeekId(weekId)
            val previousSpend = if (previousId == null) {
                0L
            } else {
                val monday = ChallengeClock.localDate(start, zone).minusWeeks(1)
                val prevStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
                val prevEnd = start
                transactions.listBetween(prevStart, prevEnd)
                    .sumOf { WeeklySummaryBuilder.signedSpendCents(it.toChallengeTransaction()) }
            }
            WeeklySummaryBuilder.build(
                weekStart = start,
                weekEnd = end,
                weekId = weekId,
                previousSpendCents = previousSpend,
                transactions = transactions.listBetween(start, end).map { it.toChallengeTransaction() },
                now = now,
                zone = zone
            )
        }

    suspend fun completeCheckIn(weekId: String, answers: String, now: Long): CheckInStreak =
        withContext(Dispatchers.IO) {
            val inserted = challenges.insertCheckIn(
                WeeklyCheckInEntity(weekId = weekId, answers = answers, completedAt = now)
            )
            val stored = challenges.streak() ?: CheckInStreakEntity()
            val previous = CheckInStreak(
                count = stored.count,
                freezeUsed = stored.freezeUsed,
                lastWeekId = stored.lastWeekId
            )
            val next = if (inserted == -1L && stored.lastWeekId == weekId) {
                previous
            } else if (inserted == -1L) {
                previous
            } else {
                CheckInStreaks.advance(previous, weekId)
            }
            if (inserted != -1L) {
                challenges.upsertStreak(
                    CheckInStreakEntity(
                        id = 1,
                        count = next.count,
                        freezeUsed = next.freezeUsed,
                        lastWeekId = next.lastWeekId
                    )
                )
                AppMetrics.checkInCompleted()
            }
            next
        }

    private suspend fun apply(row: ChallengeEntity, result: ChallengeEvaluation, now: Long) {
        val completedAt = when {
            result.status == ChallengeStatus.COMPLETED -> row.completedAt ?: now
            else -> null
        }
        val brokenAt = when {
            result.status == ChallengeStatus.BROKEN -> row.brokenAt ?: now
            else -> null
        }
        val changed = row.status != result.status.name ||
            row.breakingTransactionId != result.breakingTransactionId
        if (changed) {
            challenges.updateOutcome(
                id = row.id,
                status = result.status.name,
                breakingTransactionId = result.breakingTransactionId,
                completedAt = completedAt,
                brokenAt = brokenAt
            )
        }
        if (result.grantAchievement && result.status == ChallengeStatus.COMPLETED) {
            val inserted = challenges.insertAchievement(
                ChallengeAchievementEntity(
                    id = ChallengeEvaluator.achievementId(row.id),
                    challengeId = row.id,
                    grantedAt = row.completedAt ?: now
                )
            )
            if (inserted != -1L) AppMetrics.challengeCompleted(row.templateId)
        }
    }
}

private fun ChallengeEntity.toSnapshot() = ChallengeSnapshot(
    id = id,
    templateId = templateId,
    startAt = startAt,
    endAt = endAt,
    status = ChallengeStatus.fromStored(status),
    breakingTransactionId = breakingTransactionId
)

private fun WindowTransactionRow.toChallengeTransaction() = ChallengeTransaction(
    id = id,
    timestampMillis = timestampMillis,
    counterpartyName = counterpartyName,
    type = type,
    amountCents = kesToCents(amount) ?: 0L
)

fun quizAnswers(questions: List<QuizQuestion>, selected: Map<String, String>): String =
    questions.joinToString("\n") { question ->
        "${question.id}=${selected[question.id].orEmpty()}"
    }
