package com.lipabill.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space

@Composable
fun RegionPicker(
    selected: AppRegion,
    onSelect: (AppRegion) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Space.gap),
        modifier = modifier.fillMaxWidth()
    ) {
        AppRegion.entries.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.gap),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { option ->
                    RegionCard(
                        region = option,
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RegionCard(
    region: AppRegion,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Accent else SoftBlue)
            .border(1.dp, if (selected) Accent else Hairline, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text = region.flagEmoji,
            fontFamily = FontFamily.Default,
            fontSize = 28.sp,
            lineHeight = 32.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = region.label,
            style = HomeType.rowTitle,
            color = if (selected) OnAccent else Ink,
            maxLines = 1
        )
        Text(
            text = region.currencyCode,
            style = HomeType.caption,
            color = if (selected) OnAccent.copy(alpha = 0.78f) else Mute,
            maxLines = 1
        )
    }
}
