package com.lipabill.app.ui.repeat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lipabill.app.ui.theme.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessibilityOnboardingScreen(
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enable Repeat Payment") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Space.page)
        ) {
            Icon(
                imageVector = Icons.Outlined.AccessibilityNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Space.block)
            )
            Text(
                text = "LipaBill needs Accessibility access",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = "Android requires you to turn this on yourself. " +
                    "We’ll open the LipaBill Accessibility screen — just flip the switch.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.pageV + Space.block))
            Text("What it will do", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Space.gap))
            Text(
                text = "• Only while you start a confirmed payment\n" +
                    "• Read M-Pesa USSD menu screens\n" +
                    "• Type the next menu number, phone, or amount you already approved\n" +
                    "• Show a secure keypad for your M-Pesa PIN (hidden digits, not stored)",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text("What it will not do", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(Space.gap))
            Text(
                text = "• Never store your M-Pesa PIN\n" +
                    "• Never watch other apps when no payment is in progress\n" +
                    "• Never send money without your Confirm tap and PIN\n" +
                    "• Never claim a payment succeeded without a new M-Pesa SMS",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(Space.section))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Turn on LipaBill Accessibility")
            }
            Spacer(modifier = Modifier.height(Space.gap))
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I've enabled it — continue")
            }
            Spacer(modifier = Modifier.height(Space.gap))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
