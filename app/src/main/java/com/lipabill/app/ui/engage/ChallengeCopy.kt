package com.lipabill.app.ui.engage

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lipabill.app.R
import com.lipabill.app.viewmodel.ChallengeCardKind

@Composable
fun challengeTitle(kind: ChallengeCardKind, day: Int, totalDays: Int): String = when (kind) {
    ChallengeCardKind.OFFER -> stringResource(R.string.challenge_offer_title)
    ChallengeCardKind.ACTIVE -> stringResource(R.string.challenge_active_title, day, totalDays)
    ChallengeCardKind.COMPLETED -> stringResource(R.string.challenge_completed_title)
    ChallengeCardKind.BROKEN -> stringResource(R.string.challenge_ended_title)
}

@Composable
fun challengeBody(kind: ChallengeCardKind, day: Int, totalDays: Int): String = when (kind) {
    ChallengeCardKind.OFFER -> stringResource(R.string.challenge_offer_body)
    ChallengeCardKind.ACTIVE -> challengeActiveBody(day, totalDays)
    ChallengeCardKind.COMPLETED -> stringResource(R.string.challenge_completed_body)
    ChallengeCardKind.BROKEN -> stringResource(R.string.challenge_ended_body)
}

@Composable
fun challengeActiveBody(day: Int, totalDays: Int): String {
    val left = (totalDays - day).coerceAtLeast(0)
    return when {
        left <= 0 -> stringResource(R.string.challenge_active_body_last)
        left == 1 -> stringResource(R.string.challenge_active_body_one)
        else -> stringResource(R.string.challenge_active_body_more, left)
    }
}
