package com.lipabill.app.engage

import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.viewmodel.ChallengeCardKind
import com.lipabill.app.viewmodel.challengeCardKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ChallengeCardFlowTest {

    @Test
    fun ingested_delivery_shows_the_ended_card() {
        val zone = ZoneId.of("Africa/Nairobi")
        val start = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 8).atStartOfDay(zone).toInstant().toEpochMilli()
        val ingested = listOf(
            ChallengeTransaction(1, start + 1_000, "Naivas", TransactionType.BUY_GOODS, 20_000),
            ChallengeTransaction(
                2,
                start + 86_400_000,
                "GLOVO  KENYA",
                TransactionType.PAYBILL,
                kesToCents(450.0)!!
            )
        )
        val evaluation = ChallengeEvaluator.evaluate(
            ChallengeSnapshot(9, "food_delivery_3d", start, end, ChallengeStatus.ACTIVE),
            ingested,
            start + 90_000_000,
            CategoryRules.foodDelivery
        )
        assertEquals(ChallengeStatus.BROKEN, evaluation.status)
        assertEquals(2L, evaluation.breakingTransactionId)
        assertEquals(ChallengeCardKind.BROKEN, challengeCardKind(evaluation.status))
    }

    @Test
    fun a_clear_window_shows_the_kept_card() {
        val zone = ZoneId.of("Africa/Nairobi")
        val start = LocalDate.of(2026, 10, 5).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(2026, 10, 8).atStartOfDay(zone).toInstant().toEpochMilli()
        val evaluation = ChallengeEvaluator.evaluate(
            ChallengeSnapshot(9, "food_delivery_3d", start, end, ChallengeStatus.ACTIVE),
            listOf(ChallengeTransaction(1, start + 1_000, "KPLC", TransactionType.PAYBILL, 5_000)),
            end,
            CategoryRules.foodDelivery
        )
        assertEquals(ChallengeStatus.COMPLETED, evaluation.status)
        assertEquals(ChallengeCardKind.COMPLETED, challengeCardKind(evaluation.status))
    }
}
