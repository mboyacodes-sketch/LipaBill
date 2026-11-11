package com.lipabill.app.ui.engage

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lipabill.app.R
import com.lipabill.app.viewmodel.ChallengeCardKind
import com.lipabill.app.viewmodel.ChallengeSubject

@Composable
fun challengeTitle(kind: ChallengeCardKind, day: Int, totalDays: Int): String = when (kind) {
    ChallengeCardKind.OFFER -> stringResource(R.string.challenge_offer_title)
    ChallengeCardKind.ACTIVE -> stringResource(R.string.challenge_active_title, day, totalDays)
    ChallengeCardKind.COMPLETED -> stringResource(R.string.challenge_completed_title)
    ChallengeCardKind.BROKEN -> stringResource(R.string.challenge_ended_title)
}

/** Compact "Now playing" title for the active challenge card. */
@Composable
fun challengePlayingName(
    subject: ChallengeSubject,
    payee: String?,
    totalDays: Int
): String = when (subject) {
    ChallengeSubject.FOOD_DELIVERY -> if (totalDays >= 7) {
        stringResource(R.string.challenge_playing_food)
    } else {
        stringResource(R.string.challenge_playing_food_days, totalDays)
    }
    ChallengeSubject.FULIZA -> if (totalDays >= 7) {
        stringResource(R.string.challenge_playing_fuliza)
    } else {
        stringResource(R.string.challenge_playing_fuliza_days, totalDays)
    }
    ChallengeSubject.PAYEE -> {
        val name = payee.orEmpty()
        if (totalDays >= 7) {
            stringResource(R.string.challenge_playing_payee, name)
        } else {
            stringResource(R.string.challenge_playing_payee_days, totalDays, name)
        }
    }
    ChallengeSubject.QUIET -> stringResource(R.string.challenge_playing_quiet)
    ChallengeSubject.FEE -> stringResource(R.string.challenge_playing_fee)
    ChallengeSubject.PAYDAY -> stringResource(R.string.challenge_playing_payday)
    ChallengeSubject.DIY -> stringResource(R.string.challenge_playing_diy)
}

@Composable
fun challengeBody(
    kind: ChallengeCardKind,
    day: Int,
    totalDays: Int,
    payee: String? = null,
    fuliza: Boolean = false,
    generic: Boolean = false
): String = when (kind) {
    ChallengeCardKind.OFFER -> stringResource(R.string.challenge_offer_body)
    ChallengeCardKind.ACTIVE -> challengeActiveBody(day, totalDays)
    ChallengeCardKind.COMPLETED -> when {
        generic -> stringResource(R.string.challenge_completed_body_generic, totalDays)
        fuliza -> stringResource(R.string.challenge_completed_body_fuliza, totalDays)
        payee != null -> stringResource(R.string.challenge_completed_body_payee, totalDays, payee)
        else -> stringResource(R.string.challenge_completed_body)
    }
    ChallengeCardKind.BROKEN -> when {
        generic -> stringResource(R.string.challenge_ended_body_generic)
        fuliza -> stringResource(R.string.challenge_ended_body_fuliza)
        payee != null -> stringResource(R.string.challenge_ended_body_payee, payee)
        else -> stringResource(R.string.challenge_ended_body)
    }
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
