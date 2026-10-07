package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType

enum class ChallengeStatus {
    ACTIVE,
    COMPLETED,
    BROKEN,
    CANCELLED;

    companion object {
        fun fromStored(raw: String): ChallengeStatus =
            entries.firstOrNull { it.name == raw } ?: ACTIVE
    }
}

data class ChallengeSnapshot(
    val id: Long,
    val templateId: String,
    val startAt: Long,
    val endAt: Long,
    val status: ChallengeStatus,
    val breakingTransactionId: Long? = null
)

data class ChallengeTransaction(
    val id: Long,
    val timestampMillis: Long,
    val counterpartyName: String?,
    val type: TransactionType,
    val amountCents: Long
)

data class ChallengeEvaluation(
    val status: ChallengeStatus,
    val breakingTransactionId: Long?,
    /** True when this result is a completed challenge. Granting is idempotent via a fixed achievement id. */
    val grantAchievement: Boolean
)

data class ChallengeTemplate(
    val id: String,
    val categoryId: String,
    val durationDays: Int
)

object ChallengeTemplates {
    val foodDelivery3Days = ChallengeTemplate(
        id = "food_delivery_3d",
        categoryId = CategoryRules.foodDelivery.categoryId,
        durationDays = 3
    )

    fun byId(id: String): ChallengeTemplate? = when (id) {
        foodDelivery3Days.id -> foodDelivery3Days
        else -> null
    }
}

/**
 * Pure status for one challenge.
 *
 * A matching outgoing transaction with `startAt <= timestamp < endAt` breaks the challenge.
 * No match after [ChallengeSnapshot.endAt] completes it.
 * A completed challenge can become broken until [GRACE_MILLIS] after the end, when a late
 * transaction from inside the window arrives. After that grace the completed result sticks.
 * Cancelled challenges are left as they are.
 */
object ChallengeEvaluator {

    const val GRACE_MILLIS = 48L * 60L * 60L * 1000L

    fun achievementId(challengeId: Long): String = "challenge:$challengeId"

    fun evaluate(
        challenge: ChallengeSnapshot,
        transactions: List<ChallengeTransaction>,
        now: Long,
        rule: CategoryRule
    ): ChallengeEvaluation {
        if (challenge.status == ChallengeStatus.CANCELLED) {
            return ChallengeEvaluation(
                status = ChallengeStatus.CANCELLED,
                breakingTransactionId = challenge.breakingTransactionId,
                grantAchievement = false
            )
        }
        val breaker = transactions
            .asSequence()
            .filter { it.timestampMillis >= challenge.startAt && it.timestampMillis < challenge.endAt }
            .filter { it.type.isOutgoing() && it.amountCents > 0L }
            .filter { CategoryMatcher.matches(rule, it.counterpartyName) }
            .minWithOrNull(compareBy<ChallengeTransaction> { it.timestampMillis }.thenBy { it.id })
        if (breaker != null) {
            val graceOpen = now < challenge.endAt + GRACE_MILLIS
            val stickCompleted = challenge.status == ChallengeStatus.COMPLETED && !graceOpen
            if (stickCompleted) {
                return ChallengeEvaluation(
                    status = ChallengeStatus.COMPLETED,
                    breakingTransactionId = null,
                    grantAchievement = true
                )
            }
            return ChallengeEvaluation(
                status = ChallengeStatus.BROKEN,
                breakingTransactionId = breaker.id,
                grantAchievement = false
            )
        }
        if (challenge.status == ChallengeStatus.BROKEN) {
            return ChallengeEvaluation(
                status = ChallengeStatus.BROKEN,
                breakingTransactionId = challenge.breakingTransactionId,
                grantAchievement = false
            )
        }
        if (now < challenge.endAt) {
            return ChallengeEvaluation(
                status = ChallengeStatus.ACTIVE,
                breakingTransactionId = null,
                grantAchievement = false
            )
        }
        return ChallengeEvaluation(
            status = ChallengeStatus.COMPLETED,
            breakingTransactionId = null,
            grantAchievement = true
        )
    }
}
