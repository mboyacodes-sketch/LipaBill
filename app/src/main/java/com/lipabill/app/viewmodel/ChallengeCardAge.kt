package com.lipabill.app.viewmodel

import com.lipabill.app.data.local.entity.ChallengeEntity
import com.lipabill.app.engage.ChallengeStatus

/** Shared aging for completed challenge cards on Habits and Insights. */
object ChallengeCardAge {
    const val RECENT_MILLIS = 3L * 24L * 60L * 60L * 1000L
}

/**
 * Primary challenge for Habits / Insights: unacknowledged recent COMPLETED wins,
 * else newest ACTIVE, else the latest row (broken / offer).
 */
internal fun pickPrimaryChallenge(
    latest: ChallengeEntity?,
    active: List<ChallengeEntity>,
    acknowledgedId: Long,
    now: Long = System.currentTimeMillis()
): ChallengeEntity? {
    if (latest != null) {
        val status = ChallengeStatus.fromStored(latest.status)
        if (status == ChallengeStatus.COMPLETED &&
            latest.id != acknowledgedId &&
            now - latest.endAt <= ChallengeCardAge.RECENT_MILLIS
        ) {
            return latest
        }
    }
    return active.firstOrNull() ?: latest
}
