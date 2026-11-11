package com.lipabill.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.LipaChipHeight
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.asPillLabel

data class SpeedDialItem(
    val label: String,
    val icon: ImageVector? = null,
    /** Optional custom glyph (e.g. social brand mark) instead of [icon]. */
    val glyph: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit
)

@Composable
fun SpeedDialColumn(
    expanded: Boolean,
    onToggle: () -> Unit,
    actions: List<SpeedDialItem>,
    contentDescriptionExpanded: String = "Close",
    contentDescriptionCollapsed: String = "Add",
    animationLabel: String = "speedDialFab"
) {
    if (actions.isEmpty()) return
    val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = animationLabel)
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(initialScale = 0.85f) + slideInVertically { it / 2 },
            exit = fadeOut() + scaleOut(targetScale = 0.85f) + slideOutVertically { it / 2 }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                actions.forEach { action ->
                    SpeedDialActionRow(action)
                }
            }
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = Accent,
            contentColor = OnAccent
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = if (expanded) {
                    contentDescriptionExpanded
                } else {
                    contentDescriptionCollapsed
                },
                modifier = Modifier.rotate(rotation)
            )
        }
    }
}

@Composable
private fun SpeedDialActionRow(action: SpeedDialItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        val chipShape = RoundedCornerShape(percent = 50)
        Box(
            modifier = Modifier
                .height(LipaChipHeight)
                .shadow(6.dp, chipShape, clip = false)
                .clip(chipShape)
                .background(CardWhite)
                .clickable(onClick = action.onClick)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = action.label,
                style = HomeType.caption.asPillLabel(),
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        SmallFloatingActionButton(
            onClick = action.onClick,
            containerColor = CardWhite,
            contentColor = Accent
        ) {
            val glyph = action.glyph
            if (glyph != null) {
                glyph()
            } else {
                Icon(
                    imageVector = action.icon ?: Icons.Outlined.Add,
                    contentDescription = action.label
                )
            }
        }
    }
}
