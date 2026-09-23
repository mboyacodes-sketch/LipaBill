package com.lipabill.app.ui.repeat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import com.lipabill.app.ui.util.imeAndNavBarsPadding
import com.lipabill.app.ui.util.rememberKeyboardDismissActions
import com.lipabill.app.viewmodel.RepeatTransactionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepeatConfirmScreen(
    viewModel: RepeatTransactionViewModel,
    onBack: () -> Unit,
    onNeedAccessibility: () -> Unit,
    onManualFallback: () -> Unit,
    onOpenSimSettings: () -> Unit,
    onRequestPhoneStatePermission: () -> Unit,
    onConfirm: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tx = state.transaction
    val needsSimSetup = state.simLines.size > 1 && !state.hasSavedSimPreference
    val missingSafaricom = !state.needsPhoneStatePermission &&
        state.simLines.isNotEmpty() &&
        state.simLines.none { it.isSafaricom }
    val dismissActions = rememberKeyboardDismissActions()

    Scaffold(
        modifier = Modifier.hideKeyboardOnOutsideTap(),
        topBar = {
            TopAppBar(
                title = { Text("Pay") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .imeAndNavBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(Space.page)
        ) {
            if (tx == null) {
                Text("Transaction not found")
                return@Column
            }

            if (!state.canPay) {
                Text(
                    text = "This payment cannot be started automatically " +
                        "(missing phone/till or unsupported type).",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Space.block))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
                return@Column
            }

            Text(
                text = "To ${tx.counterpartyName ?: "recipient"}",
                style = MaterialTheme.typography.headlineMedium
            )
            if (!tx.counterpartyPhone.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Space.tight))
                Text(
                    text = tx.counterpartyPhone,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Space.block))
            OutlinedTextField(
                value = state.amountInput,
                onValueChange = viewModel::setAmountInput,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = dismissActions,
                isError = state.amountInput.isNotBlank() && !state.amountValid,
                supportingText = if (state.amountInput.isNotBlank() && !state.amountValid) {
                    { Text("Enter a valid amount greater than 0") }
                } else {
                    null
                }
            )

            // Only show blockers that require action — hide USSD/SIM explanation otherwise.
            when {
                state.needsPhoneStatePermission -> {
                    Spacer(modifier = Modifier.height(Space.block))
                    Text(
                        text = "Allow phone access to use your default SIM.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                missingSafaricom -> {
                    Spacer(modifier = Modifier.height(Space.block))
                    Text(
                        text = "A Safaricom SIM is required to dial M-Pesa. " +
                            "You can still browse LipaBill without it.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                needsSimSetup -> {
                    Spacer(modifier = Modifier.height(Space.block))
                    Text(
                        text = "Set your default M-Pesa SIM in Settings first.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                !state.featureEnabled -> {
                    Spacer(modifier = Modifier.height(Space.block))
                    Text(
                        text = "Payment automation is off — use copy-details.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                state.featureEnabled && !state.accessibilityEnabled -> {
                    Spacer(modifier = Modifier.height(Space.block))
                    Text(
                        text = "Enable Accessibility for LipaBill to fill the USSD menu.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            state.statusMessage?.let {
                Spacer(modifier = Modifier.height(Space.block))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(Space.section))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(Space.block))
                Button(
                    onClick = {
                        when {
                            !state.featureEnabled -> onManualFallback()
                            !state.accessibilityEnabled -> onNeedAccessibility()
                            state.needsPhoneStatePermission -> onRequestPhoneStatePermission()
                            missingSafaricom -> onOpenSimSettings()
                            needsSimSetup -> onOpenSimSettings()
                            else -> onConfirm()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.dialStarted && state.amountValid && state.plan != null &&
                        !missingSafaricom
                ) {
                    Text(
                        when {
                            missingSafaricom -> "Need Safaricom"
                            needsSimSetup -> "Set SIM"
                            state.needsPhoneStatePermission -> "Allow"
                            !state.accessibilityEnabled && state.featureEnabled -> "Enable"
                            else -> "Pay"
                        }
                    )
                }
            }
        }
    }
}
