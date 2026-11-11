package com.lipabill.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.ui.components.LedgerSourceChips
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
    @Suppress("UNUSED_PARAMETER") role: Role = Role.RadioButton
) {
    LipaFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        selectedContainer = selectedContainer,
        selectedContent = selectedContent,
        unselectedContainer = unselectedContainer,
        unselectedBorder = unselectedBorder
    )
}

@Composable
fun SourceFilterRow(
    sources: List<MoneySource>,
    selected: MoneySource?,
    onSelect: (MoneySource?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sources.size <= 1) return
    LedgerSourceChips(
        sources = sources,
        showAllChip = true,
        allSelected = selected == null,
        selectedSource = selected,
        onSelectAll = { onSelect(null) },
        onSelectSource = { onSelect(it) },
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    )
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
