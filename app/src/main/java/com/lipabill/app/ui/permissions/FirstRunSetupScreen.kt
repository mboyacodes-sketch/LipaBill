package com.lipabill.app.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.Space

/**
 * Welcome only. Permissions are explained later, at the feature that needs them.
 */
@Composable
fun FirstRunSetupScreen(
    onFinished: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page, vertical = Space.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to LipaBill",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.section))
            Text(
                text = "Look around first. When something needs access, LipaBill explains why " +
                    "and asks only for that. If you already allowed it, that step is skipped.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = "History uses SMS, kept encrypted on this phone. " +
                    "Send and Pay use the phone and your Safaricom SIM. " +
                    "Filling M-Pesa screens uses Accessibility, only for a payment you confirmed. " +
                    "Looking up a name uses contacts. Scanning a pass uses the camera. " +
                    "Payment alerts use notifications.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.section))
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open LipaBill")
            }
        }
    }
}
