package com.lipabill.app.ui.repeat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ussd.UssdMenuBuilder
import com.lipabill.app.viewmodel.RepeatTransactionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRepeatScreen(
    viewModel: RepeatTransactionViewModel,
    onBack: () -> Unit,
    onCopied: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val details = viewModel.manualDetailsText().ifBlank {
        val tx = state.transaction
        if (tx == null) "Loading…"
        else buildString {
            appendLine("Dial ${UssdMenuBuilder.USSD_CODE}")
            appendLine("To: ${tx.counterpartyName ?: "—"} ${tx.counterpartyPhone ?: ""}")
            appendLine("Amount: ${tx.amount}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Copy payment details") },
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
                .verticalScroll(rememberScrollState())
                .padding(Space.page)
        ) {
            Text(
                text = "Repeat automation is off or unavailable. Copy these details and dial *334# yourself.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(Space.section))
            Button(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("LipaBill repeat", details))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    onCopied()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy details")
            }
        }
    }
}
