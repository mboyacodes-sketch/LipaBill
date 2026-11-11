package com.lipabill.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.asPillLabel

/** Default action pill height — fits one line of [HomeType.rowTitle] without bottom sag. */
val LipaPillHeight = 44.dp

/** Compact filter / choice chip height. */
val LipaChipHeight = 36.dp

/**
 * Primary / outline action pill used across Habits, Challenges, and similar CTAs.
 * Fixed min height + tight label metrics keep the words optically centered.
 */
@Composable
fun LipaPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    accentOutline: Boolean = false,
    minHeight: Dp = LipaPillHeight,
    maxLines: Int = 2
) {
    val shape = RoundedCornerShape(percent = 50)
    val bg = when {
        filled && enabled -> Accent
        filled -> Accent.copy(alpha = 0.35f)
        else -> CardWhite
    }
    val border = when {
        filled -> Color.Transparent
        accentOutline -> Accent
        else -> Hairline
    }
    val fg = when {
        filled -> OnAccent
        accentOutline -> Accent
        else -> Ink
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .clip(shape)
            .background(bg)
            .then(if (!filled) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = HomeType.rowTitle.asPillLabel(),
            color = fg,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Compact selectable chip (filters, DIY options, teach answers). */
@Composable
fun LipaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = LipaChipHeight,
    selectedContainer: Color = Accent,
    selectedContent: Color = OnAccent,
    unselectedContainer: Color = CardWhite,
    unselectedContent: Color = Ink,
    unselectedBorder: Color = Hairline
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (selected) selectedContainer else unselectedContainer)
            .then(
                if (selected) Modifier
                else Modifier.border(1.dp, unselectedBorder, shape)
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = HomeType.label.asPillLabel(),
            color = if (selected) selectedContent else unselectedContent,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Soft selectable chip (DIY category / length), same metrics as [LipaFilterChip]. */
@Composable
fun LipaSoftChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LipaFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        unselectedContainer = SoftBlue.copy(alpha = 0.45f),
        unselectedBorder = Color.Transparent
    )
}
