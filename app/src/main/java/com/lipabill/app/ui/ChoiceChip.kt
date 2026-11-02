package com.lipabill.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space

@Composable
fun LipaChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unselectedContainer: Color = SoftBlue,
    unselectedBorder: Color = SoftBlue,
    selectedContainer: Color = Accent,
    selectedContent: Color = OnAccent,
    role: Role = Role.RadioButton
) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = label,
        style = HomeType.label,
        color = if (selected) selectedContent else Ink,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .background(if (selected) selectedContainer else unselectedContainer)
            .border(1.dp, if (selected) selectedContainer else unselectedBorder, shape)
            .clickable(role = role, onClick = onClick)
            .padding(horizontal = Space.card, vertical = Space.gap)
    )
}

@Composable
fun SourceFilterRow(
    sources: List<MoneySource>,
    selected: MoneySource?,
    onSelect: (MoneySource?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ProviderChip(
            label = "All",
            selected = selected == null,
            onClick = { onSelect(null) }
        )
        sources.forEach { source ->
            ProviderChip(
                label = source.label,
                selected = source == selected,
                onClick = { onSelect(source) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderPicker(
    catalog: List<MoneySource>,
    selected: Set<MoneySource>,
    onToggle: (MoneySource) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<Pair<String, List<MoneySource>>> = emptyList()
) {
    val sections = groups.ifEmpty { listOf("" to catalog) }
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.block),
        modifier = modifier.fillMaxWidth()
    ) {
        sections.forEach { (title, sources) ->
            if (sources.isEmpty()) return@forEach
            Column(
                verticalArrangement = Arrangement.spacedBy(Space.gap),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = HomeType.caption,
                        color = Mute
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Space.gap),
                    verticalArrangement = Arrangement.spacedBy(Space.gap),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    sources.forEach { source ->
                        LipaChoiceChip(
                            label = source.label,
                            selected = source in selected,
                            onClick = { onToggle(source) },
                            role = Role.Checkbox
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = label,
        style = HomeType.caption,
        color = if (selected) OnAccent else Ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Accent else SoftBlue)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}
