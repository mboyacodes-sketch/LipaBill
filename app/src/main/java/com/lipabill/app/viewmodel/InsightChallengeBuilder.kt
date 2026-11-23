package com.lipabill.app.viewmodel

import com.lipabill.app.data.local.entity.ChallengeEntity
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.engage.CategoryMatcher
import com.lipabill.app.engage.CategoryRules
import com.lipabill.app.engage.ChallengeClock
import com.lipabill.app.engage.ChallengeStatus
import com.lipabill.app.engage.ChallengeTemplates
import com.lipabill.app.engage.ChallengeTransaction
import com.lipabill.app.engage.kesToCents

/** Build the Insights cost-cutter card from the latest challenge row + ledger. */
internal fun buildInsightChallenge(
    ledger: List<MpesaTransaction>,
    latest: ChallengeEntity?,
    now: Long,
    acknowledgedId: Long
): InsightChallenge? {
    if (latest == null) return null
    val status = ChallengeStatus.fromStored(latest.status)
    if (status == ChallengeStatus.CANCELLED) return null
    val template = ChallengeTemplates.byId(latest.templateId) ?: return null
    val rule = CategoryRules.byId(template.categoryId) ?: return null
    val kind = challengeCardKind(status)
    if (kind != ChallengeCardKind.ACTIVE && now - latest.endAt > ChallengeCardAge.RECENT_MILLIS) return null
    if (kind == ChallengeCardKind.COMPLETED && latest.id == acknowledgedId) return null
    val zone = ChallengeClock.zoneOf(latest.zoneId)
    val total = template.durationDays
    val day = if (kind == ChallengeCardKind.ACTIVE) {
        ChallengeClock.dayNumber(latest.startAt, latest.endAt, now, zone)
    } else {
        total
    }
    val baseline = averageDailySpend(ledger, beforeMillis = latest.startAt) { tx ->
        CategoryMatcher.matches(
            rule,
            ChallengeTransaction(
                id = tx.id,
                timestampMillis = tx.timestampMillis,
                counterpartyName = tx.counterpartyName,
                type = tx.type,
                amountCents = kesToCents(tx.amount) ?: 0L,
                feeCents = kesToCents(tx.cost) ?: 0L,
                counterpartyPhone = tx.counterpartyPhone
            )
        )
    }
    val cleanDays = when (kind) {
        ChallengeCardKind.ACTIVE -> day - 1
        ChallengeCardKind.COMPLETED -> total
        else -> 0
    }
    val payee = ChallengeTemplates.payeeOf(latest.templateId)
    val fuliza = latest.templateId == ChallengeTemplates.fulizaFree7Days.id
    return InsightChallenge(
        title = when {
            fuliza -> "No Fuliza for $total days"
            payee != null -> "Skip $payee this week"
            else -> "No food delivery for $total days"
        },
        kind = kind,
        day = day,
        totalDays = total,
        kept = if (fuliza) null else baseline * cleanDays.coerceAtLeast(0),
        startDate = ChallengeClock.localDate(latest.startAt, zone),
        breakingTransactionId = latest.breakingTransactionId?.takeIf { it > 0L },
        challengeId = latest.id
    )
}
