package com.lipabill.app.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.AccessibilitySettingsStyle

/** Short help for sideload installs that need Allow restricted settings. */
@Composable
fun RestrictedSettingsUnlockSteps() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        Text(
            text = "One unlock for dial + SMS",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Try Turn on first. If Android shows “Restricted setting”, open App info → " +
                "⋮ or More → Allow restricted settings, then Turn on again.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** After restricted settings are allowed — enable Accessibility then SMS. */
@Composable
fun SensitiveAccessSteps() {
    val step1 = when (AccessibilityHelper.settingsStyle()) {
        AccessibilitySettingsStyle.SAMSUNG_INSTALLED_APPS ->
            "1. Turn on → Installed apps → LipaBill Repeat Payment"
        AccessibilitySettingsStyle.DIRECT_TOGGLE ->
            "1. Turn on LipaBill Repeat Payment"
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.gap)
    ) {
        Text(
            text = step1,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "2. Allow SMS",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "3. Return here when both are on",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
