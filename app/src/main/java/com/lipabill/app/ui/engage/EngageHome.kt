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
import com.lipabill.app.viewmodel.ChallengeSubject
import com.lipabill.app.viewmodel.EngageUiState

@Composable
fun EngageHomeSection(
    state: EngageUiState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val card = state.card
    val showChallenge = card != null
    val title = if (showChallenge) {
        challengeTitle(card.kind, card.day, card.totalDays)
    } else {
        stringResource(R.string.checkin_offer_title)
    }
    val body = if (showChallenge) {
        challengeBody(
            kind = card.kind,
            day = card.day,
            totalDays = card.totalDays,
            payee = card.payee,
            fuliza = card.subject == ChallengeSubject.FULIZA,
            generic = card.subject in setOf(
                ChallengeSubject.QUIET,
                ChallengeSubject.FEE,
                ChallengeSubject.PAYDAY,
                ChallengeSubject.DIY,
                ChallengeSubject.OTHER
            )
        )
    } else {
        stringResource(R.string.checkin_offer_body)
    }
    val checkInReady = !state.checkInDone && showChallenge
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
                modifier = Modifier.size(72.dp),
                animated = false
            )
        } else {
            WeekGlyph(
                modifier = Modifier.width(96.dp).height(22.dp),
                animated = false
            )
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
