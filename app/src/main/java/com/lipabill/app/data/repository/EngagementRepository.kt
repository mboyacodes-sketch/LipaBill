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
import com.lipabill.app.engage.WeeklySummary
import com.lipabill.app.engage.WeeklySummaryBuilder
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.engage.kesToCents
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.viewmodel.withoutReversals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EngagementRepository(
    private val challenges: ChallengeDao,
    private val transactions: TransactionDao
) {
    fun observeLatest() = challenges.observeLatest()

    fun observeActive() = challenges.observeActive()

    fun observeStreak() = challenges.observeStreak()

    fun observeKeptTemplateIds() = challenges.observeKeptTemplateIds()

    fun observeCheckInCount() = challenges.observeCheckInCount()

    suspend fun transactionsSince(since: Long, now: Long = System.currentTimeMillis()): List<ChallengeTransaction> =
        withContext(Dispatchers.IO) {
            withoutReversals(transactions.listBetween(since, now + 1).map { it.toMpesa() })
                .map { it.toChallengeTransaction() }
        }

    suspend fun hasCheckIn(weekId: String): Boolean = withContext(Dispatchers.IO) {
        challenges.checkIn(weekId) != null
    }

    suspend fun startSkipPayeeChallenge(payeeName: String, now: Long = System.currentTimeMillis()): Long? =
        start(ChallengeTemplates.skipPayee(payeeName), now)

    /** Active challenges, newest first. */
    suspend fun listActive(): List<ChallengeEntity> = withContext(Dispatchers.IO) {
        challenges.listActive()
    }

    /** Newest active challenge, if any. */
    suspend fun activeChallenge(): ChallengeEntity? = listActive().firstOrNull()

    /** Starts a known template. Unknown ids do nothing. At most [MAX_ACTIVE] concurrent. */
    suspend fun startChallenge(templateId: String, now: Long = System.currentTimeMillis()): Long? {
        val template = ChallengeTemplates.byId(templateId) ?: return null
        return start(template, now)
    }

    private suspend fun start(template: ChallengeTemplate, now: Long): Long? = withContext(Dispatchers.IO) {
        val active = challenges.listActive()
        val same = active.firstOrNull { it.templateId == template.id }
        when {
            // Same template already running — keep the window; do not insert again.
            same != null -> same.id
            // Cap concurrent actives.
            active.size >= MAX_ACTIVE -> null
            else -> {
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
                AppMetrics.challengeStarted(ChallengeTemplates.metricId(template.id))
                id
            }
        }
    }

    /** Cancels one active challenge by row id. */
    suspend fun cancelChallenge(id: Long) = withContext(Dispatchers.IO) {
        val row = challenges.listActive().firstOrNull { it.id == id } ?: return@withContext
        challenges.updateOutcome(
            id = row.id,
            status = ChallengeStatus.CANCELLED.name,
            breakingTransactionId = row.breakingTransactionId,
            completedAt = row.completedAt,
            brokenAt = row.brokenAt
        )
    }

    /** Cancels every active challenge (tests / rare wipe). */
    suspend fun cancelActive() = withContext(Dispatchers.IO) {
        for (row in challenges.listActive()) {
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
            val window = withoutReversals(
                transactions.listBetween(row.startAt, row.endAt).map { it.toMpesa() }
            )
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
                withoutReversals(transactions.listBetween(prevStart, prevEnd).map { it.toMpesa() })
                    .sumOf { WeeklySummaryBuilder.signedSpendCents(it.toChallengeTransaction()) }
            }
            WeeklySummaryBuilder.build(
                weekStart = start,
                weekEnd = end,
                weekId = weekId,
                previousSpendCents = previousSpend,
                transactions = withoutReversals(transactions.listBetween(start, end).map { it.toMpesa() })
                    .map { it.toChallengeTransaction() },
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
            if (inserted != -1L) AppMetrics.challengeCompleted(ChallengeTemplates.metricId(row.templateId))
        }
    }

    companion object {
        const val MAX_ACTIVE = 3
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

private fun WindowTransactionRow.toMpesa() = MpesaTransaction(
    id = id,
    code = code,
    type = type,
    amount = amount,
    counterpartyName = counterpartyName,
    counterpartyPhone = counterpartyPhone,
    timestampMillis = timestampMillis,
    balance = null,
    cost = cost,
    rawBody = rawBody
)

private fun WindowTransactionRow.toChallengeTransaction() = toMpesa().toChallengeTransaction()

private fun MpesaTransaction.toChallengeTransaction() = ChallengeTransaction(
    id = id,
    timestampMillis = timestampMillis,
    counterpartyName = counterpartyName,
    type = type,
    amountCents = kesToCents(amount) ?: 0L,
    feeCents = cost?.let { kesToCents(it) } ?: 0L,
    counterpartyPhone = counterpartyPhone
)
