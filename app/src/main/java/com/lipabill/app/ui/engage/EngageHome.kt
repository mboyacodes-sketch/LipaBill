package com.lipabill.app.ui.engage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lipabill.app.R
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.viewmodel.ChallengeCardKind
import com.lipabill.app.viewmodel.EngageUiState

@Composable
fun EngageHomeSection(
    state: EngageUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.challengesOn && !state.checkInOn) return
    val card = state.card
    val showChallenge = state.challengesOn && card != null
    val title = if (showChallenge) {
        when (card.kind) {
            ChallengeCardKind.OFFER -> stringResource(R.string.challenge_offer_title)
            ChallengeCardKind.ACTIVE -> stringResource(R.string.challenge_active_title, card.day, card.totalDays)
            ChallengeCardKind.COMPLETED -> stringResource(R.string.challenge_completed_title)
            ChallengeCardKind.BROKEN -> stringResource(R.string.challenge_ended_title)
        }
    } else {
        stringResource(R.string.checkin_offer_title)
    }
    val body = if (showChallenge) {
        when (card.kind) {
            ChallengeCardKind.OFFER -> stringResource(R.string.challenge_offer_body)
            ChallengeCardKind.ACTIVE -> stringResource(R.string.challenge_active_body)
            ChallengeCardKind.COMPLETED -> stringResource(R.string.challenge_completed_body)
            ChallengeCardKind.BROKEN -> stringResource(R.string.challenge_ended_body)
        }
    } else {
        stringResource(R.string.checkin_offer_body)
    }
    val checkInReady = state.checkInOn && !state.checkInDone && showChallenge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardWhite)
            .clickable(onClick = onOpen)
            .padding(horizontal = Space.card, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showChallenge) {
            HabitMark(
                kind = card.kind,
                day = card.day,
                totalDays = card.totalDays,
                modifier = Modifier.size(72.dp)
            )
        } else {
            WeekGlyph(modifier = Modifier.width(96.dp).height(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = HomeType.rowTitle, color = Ink)
            Spacer(modifier = Modifier.height(2.dp))
            Text(body, style = HomeType.caption, color = Mute)
            if (checkInReady) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.habits_teaser_checkin), style = HomeType.caption, color = Accent)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Mute
        )
    }
}
