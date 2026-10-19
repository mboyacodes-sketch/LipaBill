package com.lipabill.app.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Prominent disclosure before leaving LipaBill for the system AccessibilityService toggle.
 * Keep this dialog separate from SMS / phone / contacts disclosures (Play User Data policy).
 */
@Composable
fun AccessibilityServiceDisclosureDialog(
    onContinueToSettings: () -> Unit,
    onNotNow: () -> Unit,
    titleOverride: String? = null,
    bodyPrefix: String? = null
) {
    val body = buildString {
        if (!bodyPrefix.isNullOrBlank()) {
            append(bodyPrefix.trim())
            append(' ')
        }
        append(AccessibilityServiceDisclosure.STANDARD_BODY)
    }
    AlertDialog(
        onDismissRequest = onNotNow,
        title = {
            Text(titleOverride ?: AccessibilityServiceDisclosure.TITLE)
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(body)
            }
        },
        confirmButton = {
            TextButton(onClick = onContinueToSettings) {
                Text(AccessibilityServiceDisclosure.AGREE)
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(AccessibilityServiceDisclosure.NOT_NOW)
            }
        }
    )
}

/**
 * Shows [AccessibilityServiceDisclosureDialog] before invoking [onOpenSettings].
 */
@Composable
fun rememberAccessibilityEnableRequest(
    onOpenSettings: () -> Unit
): AccessibilityEnableRequest {
    var show by remember { mutableStateOf(false) }
    var titleOverride by remember { mutableStateOf<String?>(null) }
    var bodyPrefix by remember { mutableStateOf<String?>(null) }
    return AccessibilityEnableRequest(
        request = { title, prefix ->
            titleOverride = title
            bodyPrefix = prefix
            show = true
        },
        Dialog = {
            if (show) {
                AccessibilityServiceDisclosureDialog(
                    titleOverride = titleOverride,
                    bodyPrefix = bodyPrefix,
                    onContinueToSettings = {
                        show = false
                        onOpenSettings()
                    },
                    onNotNow = { show = false }
                )
            }
        }
    )
}

class AccessibilityEnableRequest(
    private val request: (titleOverride: String?, bodyPrefix: String?) -> Unit,
    val Dialog: @Composable () -> Unit
) {
    fun request(
        titleOverride: String? = null,
        bodyPrefix: String? = null
    ) = request.invoke(titleOverride, bodyPrefix)
}
