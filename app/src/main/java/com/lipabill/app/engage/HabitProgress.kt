package com.lipabill.app.engage

/**
 * Points come only from things already stored: kept challenges and finished check-ins.
 * Nothing is saved twice, so a cleared ledger or reinstall can never double-count.
 */
object HabitPoints {
    const val CHECK_IN = 60
    const val TEACH = 40
    /** Teaching earns points for the first few payees only, so tagging can't farm levels. */
    const val TEACH_MAX = 5

    fun forTemplate(templateId: String): Int = when {
        templateId == ChallengeTemplates.foodDelivery3Days.id -> 50
        templateId == ChallengeTemplates.fulizaFree7Days.id -> 150
        templateId == ChallengeTemplates.quietDays7.id -> 80
        templateId == ChallengeTemplates.zeroFee7Days.id -> 120
        templateId == ChallengeTemplates.paydayPause2Days.id -> 100
        ChallengeTemplates.fulizaUntilPaydayDays(templateId) != null -> 150
        ChallengeTemplates.diyOf(templateId) != null -> 90
        ChallengeTemplates.payeeOf(templateId) != null -> 100
        else -> 0
    }

    fun total(keptTemplateIds: List<String>, checkIns: Int, taughtPayees: Int = 0): Int =
        keptTemplateIds.sumOf(::forTemplate) +
            checkIns.coerceAtLeast(0) * CHECK_IN +
            taughtPayees.coerceIn(0, TEACH_MAX) * TEACH
}

data class HabitLevel(
    /** 1-based. */
    val number: Int,
    val minPoints: Int,
    /** Null on the top level. */
    val nextMinPoints: Int?
) {
    /** Share of the way to the next level, 0..1. The top level is always full. */
    fun progress(points: Int): Float {
        val next = nextMinPoints ?: return 1f
        return ((points - minPoints).toFloat() / (next - minPoints)).coerceIn(0f, 1f)
    }
}

object HabitLevels {
    val thresholds = listOf(0, 100, 250, 500, 800, 1200, 1800)

    fun of(points: Int): HabitLevel {
        val index = thresholds.indexOfLast { points >= it }.coerceAtLeast(0)
        return HabitLevel(
            number = index + 1,
            minPoints = thresholds[index],
            nextMinPoints = thresholds.getOrNull(index + 1)
        )
    }
}

enum class HabitBadge(val id: String, val target: Int) {
    FIRST_KEPT("habit_first_kept", 1),
    THREE_KEPT("habit_three_kept", 3),
    FIVE_KEPT("habit_five_kept", 5),
    TEN_KEPT("habit_ten_kept", 10),
    FOOD_BREAK("habit_food_break", 1),
    SKIPPED_REGULAR("habit_skipped_regular", 1),
    FULIZA_FREE("habit_fuliza_free", 1),
    FEE_DODGER("habit_fee_dodger", 1),
    PAYDAY_PAUSE_KEPT("habit_payday_pause", 1),
    QUIET_WEEK("habit_quiet_week", 1),
    DIY_KEPT("habit_diy_kept", 1),
    FIRST_CHECK_IN("habit_first_check_in", 1),
    CHECK_INS_5("habit_check_ins_5", 5),
    TEN_CHECK_INS("habit_check_ins_10", 10),
    STREAK_TWO("habit_streak_2", 2),
    STREAK_FOUR("habit_streak_4", 4),
    STREAK_SIX("habit_streak_6", 6),
    QUIET_SUNDAY("habit_quiet_sunday", 1),
    FIRST_TEACH("habit_first_teach", 1),
    FEES_DODGED("habit_fees_dodged", 1),
    DUO_PENDING("habit_duo_pending", 1)
}

data class BadgeProgress(
    val badge: HabitBadge,
    val current: Int,
    val earned: Boolean
)

object HabitBadges {

    /** Badges in [alreadyEarned] stay earned, even after a streak resets. */
    fun evaluate(
        keptTemplateIds: List<String>,
        checkIns: Int,
        streak: Int,
        alreadyEarned: Set<String>,
        quietDays: Int = 0,
        taughtPayees: Int = 0,
        feesDodgedCents: Long = 0L,
        duoPending: Boolean = false
    ): List<BadgeProgress> = HabitBadge.entries.map { badge ->
        val raw = when (badge) {
            HabitBadge.FIRST_KEPT, HabitBadge.THREE_KEPT, HabitBadge.FIVE_KEPT, HabitBadge.TEN_KEPT ->
                keptTemplateIds.size
            HabitBadge.FOOD_BREAK ->
                keptTemplateIds.count { it == ChallengeTemplates.foodDelivery3Days.id }
            HabitBadge.SKIPPED_REGULAR ->
                keptTemplateIds.count { ChallengeTemplates.payeeOf(it) != null }
            HabitBadge.FULIZA_FREE ->
                keptTemplateIds.count {
                    it == ChallengeTemplates.fulizaFree7Days.id ||
                        ChallengeTemplates.fulizaUntilPaydayDays(it) != null
                }
            HabitBadge.FEE_DODGER ->
                keptTemplateIds.count {
                    it == ChallengeTemplates.zeroFee7Days.id ||
                        it == ChallengeTemplates.fulizaFree7Days.id ||
                        ChallengeTemplates.fulizaUntilPaydayDays(it) != null
                }
            HabitBadge.PAYDAY_PAUSE_KEPT ->
                keptTemplateIds.count { it == ChallengeTemplates.paydayPause2Days.id }
            HabitBadge.QUIET_WEEK ->
                keptTemplateIds.count { it == ChallengeTemplates.quietDays7.id }
            HabitBadge.DIY_KEPT ->
                keptTemplateIds.count { ChallengeTemplates.diyOf(it) != null }
            HabitBadge.FIRST_CHECK_IN, HabitBadge.CHECK_INS_5, HabitBadge.TEN_CHECK_INS ->
                checkIns
            HabitBadge.STREAK_TWO, HabitBadge.STREAK_FOUR, HabitBadge.STREAK_SIX ->
                streak
            HabitBadge.QUIET_SUNDAY -> if (quietDays > 0) 1 else 0
            HabitBadge.FIRST_TEACH -> taughtPayees
            HabitBadge.FEES_DODGED -> if (feesDodgedCents > 0L) 1 else 0
            HabitBadge.DUO_PENDING -> if (duoPending) 1 else 0
        }
        val earned = raw >= badge.target || badge.id in alreadyEarned
        BadgeProgress(
            badge = badge,
            current = if (earned) badge.target else raw.coerceIn(0, badge.target),
            earned = earned
        )
    }
}
