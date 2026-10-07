package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ChallengeEvaluatorTest {

    private val nairobi = ZoneId.of("Africa/Nairobi")
    private val rule = CategoryRules.foodDelivery
    private val start = LocalDate.of(2026, 10, 5).atStartOfDay(nairobi).toInstant().toEpochMilli()
    private val end = LocalDate.of(2026, 10, 8).atStartOfDay(nairobi).toInstant().toEpochMilli()

    @Test
    fun no_match_stays_active_until_the_window_ends() {
        val during = evaluate(listOf(tx(1, start + 3_600_000, "Naivas")), start + 3_600_000)
        assertEquals(ChallengeStatus.ACTIVE, during.status)
        assertFalse(during.grantAchievement)

        val after = evaluate(emptyList(), end)
        assertEquals(ChallengeStatus.COMPLETED, after.status)
        assertTrue(after.grantAchievement)
        assertNull(after.breakingTransactionId)
    }

    @Test
    fun match_on_the_first_day_breaks() {
        val at = start
        val result = evaluate(listOf(tx(4, at, "Glovo")), at + 1_000)
        assertEquals(ChallengeStatus.BROKEN, result.status)
        assertEquals(4L, result.breakingTransactionId)
    }

    @Test
    fun match_mid_window_breaks() {
        val at = LocalDate.of(2026, 10, 6).atTime(13, 0).atZone(nairobi).toInstant().toEpochMilli()
        val result = evaluate(listOf(tx(7, at, "BOLT FOOD")), at)
        assertEquals(ChallengeStatus.BROKEN, result.status)
        assertEquals(7L, result.breakingTransactionId)
    }

    @Test
    fun match_on_the_last_instant_breaks() {
        val at = end - 1
        val result = evaluate(listOf(tx(8, at, "Jumia Food")), at)
        assertEquals(ChallengeStatus.BROKEN, result.status)
        assertEquals(8L, result.breakingTransactionId)
    }

    @Test
    fun exact_end_boundary_does_not_break() {
        val result = evaluate(listOf(tx(3, end, "Uber Eats")), end - 1)
        assertEquals(ChallengeStatus.ACTIVE, result.status)
        assertNull(result.breakingTransactionId)
    }

    @Test
    fun messy_names_still_match() {
        val at = start + 60_000
        assertEquals(
            1L,
            evaluate(listOf(tx(1, at, "  UBER   EATS  ")), at).breakingTransactionId
        )
        assertEquals(
            2L,
            evaluate(listOf(tx(2, at, "UberEats Nairobi")), at).breakingTransactionId
        )
    }

    @Test
    fun incoming_reversal_and_zero_do_not_break() {
        val at = start + 60_000
        val rows = listOf(
            tx(1, at, "Glovo", TransactionType.RECEIVED, 10_000),
            tx(2, at, "Glovo", TransactionType.REVERSED, 10_000),
            tx(3, at, "Glovo", TransactionType.BUY_GOODS, 0)
        )
        assertEquals(ChallengeStatus.ACTIVE, evaluate(rows, at).status)
    }

    @Test
    fun late_sms_inside_grace_moves_completed_to_broken() {
        val late = tx(11, start + 60_000, "Glovo")
        val now = end + 60L * 60L * 1000L
        val result = evaluate(listOf(late), now, ChallengeStatus.COMPLETED)
        assertEquals(ChallengeStatus.BROKEN, result.status)
        assertEquals(11L, result.breakingTransactionId)
        assertFalse(result.grantAchievement)
    }

    @Test
    fun late_sms_after_grace_leaves_completed() {
        val late = tx(11, start + 60_000, "Glovo")
        val now = end + ChallengeEvaluator.GRACE_MILLIS
        val result = evaluate(listOf(late), now, ChallengeStatus.COMPLETED)
        assertEquals(ChallengeStatus.COMPLETED, result.status)
        assertNull(result.breakingTransactionId)
        assertTrue(result.grantAchievement)
    }

    @Test
    fun stored_window_does_not_follow_a_later_zone() {
        val now = LocalDate.of(2026, 10, 5).atTime(15, 0).atZone(nairobi).toInstant().toEpochMilli()
        val (storedStart, storedEnd) = ChallengeClock.windowFrom(now, 3, nairobi)
        val (shiftedStart, _) = ChallengeClock.windowFrom(now, 3, ZoneId.of("America/New_York"))
        assertTrue(storedStart != shiftedStart)
        val atStoredStart = tx(1, storedStart, "Glovo")
        val result = ChallengeEvaluator.evaluate(
            ChallengeSnapshot(1, "food_delivery_3d", storedStart, storedEnd, ChallengeStatus.ACTIVE),
            listOf(atStoredStart),
            storedStart + 1_000,
            rule
        )
        assertEquals(ChallengeStatus.BROKEN, result.status)
    }

    @Test
    fun duplicate_rows_keep_the_earliest_id() {
        val at = start + 120_000
        val result = evaluate(
            listOf(
                tx(9, at, "Glovo"),
                tx(2, at, "Glovo")
            ),
            at
        )
        assertEquals(2L, result.breakingTransactionId)
    }

    @Test
    fun cancelled_stays_cancelled() {
        val result = evaluate(listOf(tx(1, start, "Glovo")), end, ChallengeStatus.CANCELLED)
        assertEquals(ChallengeStatus.CANCELLED, result.status)
        assertFalse(result.grantAchievement)
    }

    @Test
    fun achievement_id_is_stable() {
        assertEquals("challenge:42", ChallengeEvaluator.achievementId(42))
    }

    private fun evaluate(
        transactions: List<ChallengeTransaction>,
        now: Long,
        status: ChallengeStatus = ChallengeStatus.ACTIVE
    ): ChallengeEvaluation = ChallengeEvaluator.evaluate(
        ChallengeSnapshot(1, "food_delivery_3d", start, end, status),
        transactions,
        now,
        rule
    )

    private fun tx(
        id: Long,
        at: Long,
        name: String?,
        type: TransactionType = TransactionType.BUY_GOODS,
        cents: Long = 45_000
    ) = ChallengeTransaction(id, at, name, type, cents)
}
