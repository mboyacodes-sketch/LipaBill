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
    val amountCents: Long,
    /** M-Pesa transaction cost charged on this payment. */
    val feeCents: Long = 0L
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
    val durationDays: Int,
    /** When > 0, matching spend may accumulate up to this amount before the challenge breaks. */
    val capCents: Long = 0L
)

object ChallengeTemplates {
    val foodDelivery3Days = ChallengeTemplate(
        id = "food_delivery_3d",
        categoryId = CategoryRules.foodDelivery.categoryId,
        durationDays = 3
    )

    val fulizaFree7Days = ChallengeTemplate(
        id = "fuliza_free_7d",
        categoryId = CategoryRules.fuliza.categoryId,
        durationDays = 7
    )

    val quietDays7 = ChallengeTemplate(
        id = "quiet_days_7d",
        categoryId = CategoryRules.anyOutgoing.categoryId,
        durationDays = 7
    )

    val zeroFee7Days = ChallengeTemplate(
        id = "zero_fee_7d",
        categoryId = CategoryRules.feeLike.categoryId,
        durationDays = 7
    )

    val paydayPause2Days = ChallengeTemplate(
        id = "payday_pause_2d",
        categoryId = CategoryRules.discretionary.categoryId,
        durationDays = 2
    )

    private const val SKIP_PAYEE_PREFIX = "skip_payee_7d:"
    private const val FULIZA_UNTIL_PREFIX = "fuliza_until_payday:"
    private const val DIY_PREFIX = "diy:"

    /** Seven days without paying one payee, named as it appears in their SMS. */
    fun skipPayee(payeeName: String) = ChallengeTemplate(
        id = SKIP_PAYEE_PREFIX + payeeName.trim(),
        categoryId = CategoryRules.PAYEE_PREFIX + payeeName.trim(),
        durationDays = 7
    )

    /** No Fuliza until payday; [durationDays] is days left until salary. */
    fun fulizaUntilPayday(durationDays: Int) = ChallengeTemplate(
        id = FULIZA_UNTIL_PREFIX + durationDays.coerceAtLeast(1),
        categoryId = CategoryRules.fuliza.categoryId,
        durationDays = durationDays.coerceAtLeast(1)
    )

    /**
     * User-built pause: `diy:{categoryId}:{days}:{capCents}`.
     * Cap 0 = any matching payment breaks; cap > 0 = break once matching spend exceeds the cap.
     */
    fun diy(categoryId: String, days: Int, capCents: Long) = ChallengeTemplate(
        id = "$DIY_PREFIX$categoryId:${days.coerceAtLeast(1)}:${capCents.coerceAtLeast(0L)}",
        categoryId = categoryId,
        durationDays = days.coerceAtLeast(1),
        capCents = capCents.coerceAtLeast(0L)
    )

    /** The payee a skip challenge is about, or null for category challenges. */
    fun payeeOf(id: String): String? =
        id.takeIf { it.startsWith(SKIP_PAYEE_PREFIX) }?.removePrefix(SKIP_PAYEE_PREFIX)

    fun fulizaUntilPaydayDays(id: String): Int? =
        id.takeIf { it.startsWith(FULIZA_UNTIL_PREFIX) }
            ?.removePrefix(FULIZA_UNTIL_PREFIX)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }

    fun diyOf(id: String): ChallengeTemplate? {
        if (!id.startsWith(DIY_PREFIX)) return null
        val body = id.removePrefix(DIY_PREFIX)
        val parts = body.split(':')
        if (parts.size < 3) return null
        val cap = parts.last().toLongOrNull() ?: return null
        val days = parts[parts.lastIndex - 1].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val categoryId = parts.dropLast(2).joinToString(":").takeIf { it.isNotBlank() } ?: return null
        if (CategoryRules.byId(categoryId) == null && !categoryId.startsWith(CategoryRules.PAYEE_PREFIX)) {
            return null
        }
        return ChallengeTemplate(id = id, categoryId = categoryId, durationDays = days, capCents = cap)
    }

    fun capCentsOf(id: String): Long = diyOf(id)?.capCents ?: 0L

    /** Template id safe for analytics: payee names never leave the device. */
    fun metricId(id: String): String = when {
        payeeOf(id) != null -> SKIP_PAYEE_PREFIX.trimEnd(':')
        id.startsWith(DIY_PREFIX) -> DIY_PREFIX.trimEnd(':')
        fulizaUntilPaydayDays(id) != null -> FULIZA_UNTIL_PREFIX.trimEnd(':')
        else -> id
    }

    fun byId(id: String): ChallengeTemplate? = when (id) {
        foodDelivery3Days.id -> foodDelivery3Days
        fulizaFree7Days.id -> fulizaFree7Days
        quietDays7.id -> quietDays7
        zeroFee7Days.id -> zeroFee7Days
        paydayPause2Days.id -> paydayPause2Days
        else -> fulizaUntilPaydayDays(id)?.let { fulizaUntilPayday(it) }
            ?: diyOf(id)
            ?: payeeOf(id)?.takeIf { it.isNotBlank() }?.let { skipPayee(it) }
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
        rule: CategoryRule,
        capCents: Long = ChallengeTemplates.capCentsOf(challenge.templateId)
    ): ChallengeEvaluation {
        if (challenge.status == ChallengeStatus.CANCELLED) {
            return ChallengeEvaluation(
                status = ChallengeStatus.CANCELLED,
                breakingTransactionId = challenge.breakingTransactionId,
                grantAchievement = false
            )
        }
        val inWindow = transactions
            .asSequence()
            .filter { it.timestampMillis >= challenge.startAt && it.timestampMillis < challenge.endAt }
            .filter { CategoryMatcher.matches(rule, it) }
            .sortedWith(compareBy<ChallengeTransaction> { it.timestampMillis }.thenBy { it.id })
        val breaker = if (capCents > 0L) {
            var spent = 0L
            inWindow.firstOrNull { tx ->
                spent += tx.amountCents
                spent > capCents
            }
        } else {
            inWindow.firstOrNull()
        }
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
