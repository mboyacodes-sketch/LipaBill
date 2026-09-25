package com.lipabill.app.ui.permissions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lipabill.app.ussd.AccessibilityHelper

/**
 * Brief coach before leaving LipaBill for the system Accessibility toggle.
 * Copy follows [AccessibilityHelper.settingsStyle] for this device.
 */
@Composable
fun AccessibilityToggleCoachDialog(
    currentlyEnabled: Boolean,
    onConfirmOpen: () -> Unit,
    onDismiss: () -> Unit,
    titleOverride: String? = null,
    bodyOverride: String? = null,
    dismissLabel: String = "Cancel"
) {
    val turningOn = !currentlyEnabled
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                titleOverride
                    ?: if (turningOn) "Turn on LipaBill" else "Turn off LipaBill"
            )
        },
        text = {
            Text(
                bodyOverride
                    ?: AccessibilityHelper.coachMessage(turningOn = turningOn)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirmOpen()
                }
            ) {
                Text("Open settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}

/**
 * Remembers coach visibility and opens Settings only after the user confirms.
 */
@Composable
fun rememberAccessibilityToggleCoach(
    currentlyEnabled: Boolean,
    onOpenSettings: () -> Unit
): AccessibilityToggleCoachState {
    var showCoach by remember { mutableStateOf(false) }
    return AccessibilityToggleCoachState(
        showCoach = showCoach,
        requestToggle = { showCoach = true },
        Dialog = {
            if (showCoach) {
                AccessibilityToggleCoachDialog(
                    currentlyEnabled = currentlyEnabled,
                    onConfirmOpen = onOpenSettings,
                    onDismiss = { showCoach = false }
                )
            }
        }
    )
}

class AccessibilityToggleCoachState(
    val showCoach: Boolean,
    val requestToggle: () -> Unit,
    val Dialog: @Composable () -> Unit
)
