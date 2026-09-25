package com.lipabill.app.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lipabill.app.ui.theme.Space

/** Steps to surface and flip Allow restricted settings (shared by SMS + Accessibility). */
@Composable
fun RestrictedSettingsUnlockSteps() {
    val steps = listOf(
        "Tap Open LipaBill toggle and try turning LipaBill Repeat Payment on " +
            "(you’ll see “Restricted setting” — that unlocks the App info menu)",
        "Tap Open App info → ⋮ or More → Allow restricted settings " +
            "(confirm with PIN / fingerprint)",
        "Return here — next you’ll turn on Accessibility and allow SMS"
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        Text(
            text = "One unlock for dial + SMS",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(Space.gap))
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** After restricted settings are allowed — enable Accessibility then SMS. */
@Composable
fun SensitiveAccessSteps() {
    val steps = listOf(
        "Open LipaBill toggle → turn LipaBill Repeat Payment On",
        "Tap Allow SMS and grant it (or open App info → Permissions → SMS → Allow)",
        "Return here when both are on"
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(Space.gap))
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
