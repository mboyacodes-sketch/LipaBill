package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitProgressTest {

    private val food = ChallengeTemplates.foodDelivery3Days.id
    private val fuliza = ChallengeTemplates.fulizaFree7Days.id
    private val payee = ChallengeTemplates.skipPayee("Java House").id

    @Test
    fun points_add_kept_challenges_and_check_ins() {
        assertEquals(0, HabitPoints.total(emptyList(), 0))
        assertEquals(50 + 150 + 100 + 2 * HabitPoints.CHECK_IN, HabitPoints.total(listOf(food, fuliza, payee), 2))
        assertEquals(0, HabitPoints.forTemplate("retired_template"))
        assertEquals(HabitPoints.TEACH, HabitPoints.total(emptyList(), 0, taughtPayees = 1))
        assertEquals(
            HabitPoints.TEACH_MAX * HabitPoints.TEACH,
            HabitPoints.total(emptyList(), 0, taughtPayees = 40)
        )
    }

    @Test
    fun points_cover_new_catalog_templates() {
        assertEquals(80, HabitPoints.forTemplate(ChallengeTemplates.quietDays7.id))
        assertEquals(120, HabitPoints.forTemplate(ChallengeTemplates.zeroFee7Days.id))
        assertEquals(100, HabitPoints.forTemplate(ChallengeTemplates.paydayPause2Days.id))
        assertEquals(150, HabitPoints.forTemplate(ChallengeTemplates.fulizaUntilPayday(12).id))
        assertEquals(90, HabitPoints.forTemplate(ChallengeTemplates.diy(CategoryRules.discretionary.categoryId, 5, 10_000).id))
    }

    @Test
    fun templates_resolve_by_id() {
        assertEquals(ChallengeTemplates.quietDays7, ChallengeTemplates.byId("quiet_days_7d"))
        assertEquals(ChallengeTemplates.zeroFee7Days, ChallengeTemplates.byId("zero_fee_7d"))
        assertEquals(ChallengeTemplates.paydayPause2Days, ChallengeTemplates.byId("payday_pause_2d"))
        assertEquals(12, ChallengeTemplates.byId("fuliza_until_payday:12")!!.durationDays)
        val diy = ChallengeTemplates.byId("diy:food_delivery:4:50000")
        assertNotNull(diy)
        assertEquals("food_delivery", diy!!.categoryId)
        assertEquals(4, diy.durationDays)
        assertNull(ChallengeTemplates.byId("diy:unknown_cat:3:0"))
    }

    @Test
    fun levels_follow_thresholds() {
        assertEquals(1, HabitLevels.of(0).number)
        assertEquals(1, HabitLevels.of(99).number)
        assertEquals(2, HabitLevels.of(100).number)
        val four = HabitLevels.of(650)
        assertEquals(4, four.number)
        assertEquals(800, four.nextMinPoints)
        assertEquals(0.5f, four.progress(650), 0.001f)
        val top = HabitLevels.of(5_000)
        assertEquals(HabitLevels.thresholds.size, top.number)
        assertNull(top.nextMinPoints)
        assertEquals(1f, top.progress(5_000), 0.001f)
    }

    @Test
    fun badges_track_progress_and_stay_once_earned() {
        val fresh = HabitBadges.evaluate(listOf(food), checkIns = 0, streak = 2, alreadyEarned = emptySet())
            .associateBy { it.badge }
        assertTrue(fresh.getValue(HabitBadge.FIRST_KEPT).earned)
        assertTrue(fresh.getValue(HabitBadge.FOOD_BREAK).earned)
        assertFalse(fresh.getValue(HabitBadge.THREE_KEPT).earned)
        assertEquals(1, fresh.getValue(HabitBadge.THREE_KEPT).current)
        assertEquals(2, fresh.getValue(HabitBadge.STREAK_FOUR).current)
        assertTrue(fresh.getValue(HabitBadge.STREAK_TWO).earned)
        assertFalse(fresh.getValue(HabitBadge.STREAK_SIX).earned)

        val afterReset = HabitBadges.evaluate(
            emptyList(),
            checkIns = 4,
            streak = 0,
            alreadyEarned = setOf(HabitBadge.STREAK_FOUR.id)
        ).associateBy { it.badge }
        assertTrue(afterReset.getValue(HabitBadge.STREAK_FOUR).earned)
        assertEquals(4, afterReset.getValue(HabitBadge.STREAK_FOUR).current)
    }

    @Test
    fun new_badges_use_optional_params() {
        val earned = HabitBadges.evaluate(
            keptTemplateIds = listOf(
                ChallengeTemplates.zeroFee7Days.id,
                ChallengeTemplates.paydayPause2Days.id,
                ChallengeTemplates.diy(CategoryRules.anyOutgoing.categoryId, 3, 0).id
            ),
            checkIns = 5,
            streak = 6,
            alreadyEarned = emptySet(),
            quietDays = 2,
            taughtPayees = 1,
            feesDodgedCents = 5_000L,
            duoPending = true
        ).associateBy { it.badge }
        assertTrue(earned.getValue(HabitBadge.FEE_DODGER).earned)
        assertTrue(earned.getValue(HabitBadge.PAYDAY_PAUSE_KEPT).earned)
        assertTrue(earned.getValue(HabitBadge.DIY_KEPT).earned)
        assertTrue(earned.getValue(HabitBadge.CHECK_INS_5).earned)
        assertTrue(earned.getValue(HabitBadge.STREAK_SIX).earned)
        assertTrue(earned.getValue(HabitBadge.QUIET_SUNDAY).earned)
        assertTrue(earned.getValue(HabitBadge.FIRST_TEACH).earned)
        assertTrue(earned.getValue(HabitBadge.FEES_DODGED).earned)
        assertTrue(earned.getValue(HabitBadge.DUO_PENDING).earned)
        assertTrue(HabitBadge.entries.size in 16..24)
    }

    @Test
    fun fuliza_free_breaks_on_a_draw_only() {
        val zone = ZoneId.of("Africa/Nairobi")
        val start = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 12).atStartOfDay(zone).toInstant().toEpochMilli()
        val rule = CategoryRules.byId(ChallengeTemplates.fulizaFree7Days.categoryId)!!
        val challenge = ChallengeSnapshot(1, fuliza, start, end, ChallengeStatus.ACTIVE)
        val spend = ChallengeTransaction(1, start + 1_000, "Naivas", TransactionType.BUY_GOODS, 50_000)
        val draw = ChallengeTransaction(2, start + 2_000, null, TransactionType.FULIZA, 20_000)

        assertEquals(
            ChallengeStatus.COMPLETED,
            ChallengeEvaluator.evaluate(challenge, listOf(spend), end, rule).status
        )
        val broken = ChallengeEvaluator.evaluate(challenge, listOf(spend, draw), start + 3_000, rule)
        assertEquals(ChallengeStatus.BROKEN, broken.status)
        assertEquals(2L, broken.breakingTransactionId)
        assertEquals(ChallengeTemplates.fulizaFree7Days, ChallengeTemplates.byId(fuliza))
    }

    @Test
    fun quiet_days_break_on_any_outgoing() {
        val zone = ZoneId.of("Africa/Nairobi")
        val start = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 12).atStartOfDay(zone).toInstant().toEpochMilli()
        val rule = CategoryRules.byId(ChallengeTemplates.quietDays7.categoryId)!!
        val challenge = ChallengeSnapshot(1, ChallengeTemplates.quietDays7.id, start, end, ChallengeStatus.ACTIVE)
        val spend = ChallengeTransaction(1, start + 1_000, "Naivas", TransactionType.BUY_GOODS, 50_000)
        val broken = ChallengeEvaluator.evaluate(challenge, listOf(spend), start + 2_000, rule)
        assertEquals(ChallengeStatus.BROKEN, broken.status)
    }

    @Test
    fun zero_fee_breaks_on_fuliza() {
        val zone = ZoneId.of("Africa/Nairobi")
        val start = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 12).atStartOfDay(zone).toInstant().toEpochMilli()
        val rule = CategoryRules.byId(ChallengeTemplates.zeroFee7Days.categoryId)!!
        val challenge = ChallengeSnapshot(1, ChallengeTemplates.zeroFee7Days.id, start, end, ChallengeStatus.ACTIVE)
        val draw = ChallengeTransaction(2, start + 2_000, null, TransactionType.FULIZA, 20_000)
        assertEquals(
            ChallengeStatus.BROKEN,
            ChallengeEvaluator.evaluate(challenge, listOf(draw), start + 3_000, rule).status
        )
    }

    @Test
    fun zero_fee_breaks_on_a_charged_payment_but_not_a_free_one() {
        val zone = ZoneId.of("Africa/Nairobi")
        val start = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 12).atStartOfDay(zone).toInstant().toEpochMilli()
        val rule = CategoryRules.byId(ChallengeTemplates.zeroFee7Days.categoryId)!!
        val challenge = ChallengeSnapshot(1, ChallengeTemplates.zeroFee7Days.id, start, end, ChallengeStatus.ACTIVE)
        val till = ChallengeTransaction(1, start + 1_000, "Java House", TransactionType.BUY_GOODS, 45_000)
        val charged = ChallengeTransaction(2, start + 2_000, "Jane", TransactionType.SENT, 150_000, feeCents = 2_900)
        assertEquals(ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(challenge, listOf(till), end, rule).status)
        val broken = ChallengeEvaluator.evaluate(challenge, listOf(till, charged), start + 3_000, rule)
        assertEquals(ChallengeStatus.BROKEN, broken.status)
        assertEquals(2L, broken.breakingTransactionId)
    }

    @Test
    fun moving_money_to_savings_keeps_a_quiet_week() {
        val zone = ZoneId.of("Africa/Nairobi")
        val start = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 12).atStartOfDay(zone).toInstant().toEpochMilli()
        val rule = CategoryRules.byId(ChallengeTemplates.quietDays7.categoryId)!!
        val challenge = ChallengeSnapshot(1, ChallengeTemplates.quietDays7.id, start, end, ChallengeStatus.ACTIVE)
        val save = ChallengeTransaction(1, start + 1_000, "M-Shwari", TransactionType.SAVINGS, 200_000)
        assertEquals(ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(challenge, listOf(save), end, rule).status)
    }
}
