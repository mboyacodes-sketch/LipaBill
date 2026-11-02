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
 * Confirm before leaving LipaBill to turn the AccessibilityService off.
 * Turning on uses the consent wizard, not this coach.
 */
@Composable
fun AccessibilityToggleCoachDialog(
    onConfirmOpen: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn off ${AccessibilityServiceDisclosure.SERVICE_LABEL}") },
        text = {
            Text(
                "Send, Pay, and Repeat stay off until this is on again. " +
                    AccessibilityHelper.coachMessage(
                        turningOn = false,
                        serviceLabel = AccessibilityServiceDisclosure.SERVICE_LABEL
                    )
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
        requestToggle = { if (currentlyEnabled) showCoach = true },
        Dialog = {
            if (showCoach) {
                AccessibilityToggleCoachDialog(
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
