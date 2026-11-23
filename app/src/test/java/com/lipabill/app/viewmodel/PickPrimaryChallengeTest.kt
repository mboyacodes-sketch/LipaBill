package com.lipabill.app.viewmodel

import com.lipabill.app.data.local.entity.ChallengeEntity
import com.lipabill.app.engage.ChallengeStatus
import com.lipabill.app.engage.ChallengeTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickPrimaryChallengeTest {

    private val now = 1_700_000_000_000L

    @Test
    fun unacked_completed_beats_active() {
        val completed = row(id = 10, status = ChallengeStatus.COMPLETED, endAt = now - 1_000)
        val active = row(id = 11, status = ChallengeStatus.ACTIVE, endAt = now + 86_400_000)
        val primary = pickPrimaryChallenge(completed, listOf(active), acknowledgedId = 0L, now = now)
        assertEquals(10L, primary?.id)
    }

    @Test
    fun newest_active_when_completed_acked() {
        val completed = row(id = 10, status = ChallengeStatus.COMPLETED, endAt = now - 1_000)
        val older = row(id = 11, status = ChallengeStatus.ACTIVE, endAt = now + 86_400_000)
        val newer = row(id = 12, status = ChallengeStatus.ACTIVE, endAt = now + 86_400_000)
        val primary = pickPrimaryChallenge(
            latest = completed,
            active = listOf(newer, older),
            acknowledgedId = 10L,
            now = now
        )
        assertEquals(12L, primary?.id)
    }

    @Test
    fun null_when_nothing() {
        assertNull(pickPrimaryChallenge(null, emptyList(), acknowledgedId = 0L, now = now))
    }

    private fun row(
        id: Long,
        status: ChallengeStatus,
        endAt: Long,
        templateId: String = ChallengeTemplates.foodDelivery3Days.id
    ) = ChallengeEntity(
        id = id,
        templateId = templateId,
        startAt = endAt - 3L * 86_400_000L,
        endAt = endAt,
        zoneId = "Africa/Nairobi",
        status = status.name
    )
}
