package com.lipabill.app.ui.tickets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lipabill.app.data.tickets.smart.DraftField
import com.lipabill.app.data.tickets.smart.FieldSource
import com.lipabill.app.data.tickets.smart.NameCheck
import com.lipabill.app.data.tickets.smart.PassDraft
import com.lipabill.app.data.tickets.smart.PassField
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue

/** Amber for "check this"; dark ink keeps it readable on the light fill in both themes. */
private val CheckFill = Color(0xFFFDF0DC)
private val CheckInk = Color(0xFF5C3205)

/** The four ways in from the design: camera, photo or PDF, .pkpass, pasted message. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAddSheet(
    onDismiss: () -> Unit,
    onScan: () -> Unit,
    onUpload: () -> Unit,
    onPkPass: () -> Unit,
    onPaste: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardWhite) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Add a pass", style = HomeType.section, fontWeight = FontWeight.Bold, color = Ink)
            Text(
                "Airline, SGR or event. The phone reads it, you check it, then it lands in Passes.",
                style = HomeType.caption,
                color = Mute
            )
            Spacer(modifier = Modifier.height(4.dp))
            AddOption(Icons.Outlined.DocumentScanner, "Scan with camera", "Boarding pass barcode or ticket QR", primary = true, onClick = onScan)
            AddOption(Icons.Outlined.UploadFile, "Upload photo or PDF", "E-tickets, screenshots, email attachments", onClick = onUpload)
            AddOption(Icons.Outlined.Description, "Open a .pkpass file", "Wallet passes from airlines and event sites", onClick = onPkPass)
            AddOption(Icons.Outlined.Chat, "Paste a booking message", "SGR SMS, airline or event email text", onClick = onPaste)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Canvas)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Income, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Read on this phone. Nothing is uploaded.", style = HomeType.caption, color = Ink)
            }
        }
    }
}

@Composable
private fun AddOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (primary) Accent else SoftBlue)
        ) {
            Icon(icon, contentDescription = null, tint = if (primary) OnAccent else Accent, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = HomeType.rowTitle, color = Ink)
            Text(subtitle, style = HomeType.caption, color = Mute)
        }
    }
}

@Composable
fun PastePassDialog(onDismiss: () -> Unit, onRead: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste a booking message") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("SMS or email text") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onRead(text) }, enabled = text.isNotBlank()) { Text("Read it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * The review step: every value with where it came from, anything unsure marked,
 * and the passenger compared with the person's own name. Nothing saves before "Add pass".
 */
@Composable
fun PassReviewScreen(
    draft: PassDraft,
    holderName: String?,
    saving: Boolean,
    onEdit: (PassField, String) -> Unit,
    onHolderName: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
                }
                Column {
                    Text("Check your pass", style = HomeType.section, fontWeight = FontWeight.Bold, color = Ink)
                    Text(draft.kind.label, style = HomeType.caption, color = Mute)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SourceSummary(draft)
                NameBanner(draft, holderName, onHolderName)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardWhite)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    PassField.wantedFor(draft.kind).forEach { field ->
                        ReviewFieldRow(field, draft.fields[field], onEdit)
                    }
                }
                draft.blockers.forEach { Notice(it) }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Button(
                onClick = onSave,
                enabled = draft.canSave && !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)
            ) {
                Text(if (saving) "Adding…" else "Add pass", style = HomeType.rowTitle)
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", style = HomeType.label, color = Accent)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SourceSummary(draft: PassDraft) {
    val counts = draft.countBySource()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardWhite)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Read on this phone", style = HomeType.label, fontWeight = FontWeight.SemiBold, color = Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldSource.entries.filter { (counts[it] ?: 0) > 0 && it != FieldSource.USER }.forEach { source ->
                SourceChip("${source.label}: ${counts[source]}", source)
            }
        }
    }
}

@Composable
private fun NameBanner(draft: PassDraft, holderName: String?, onHolderName: (String) -> Unit) {
    val holder = draft[PassField.HOLDER] ?: return
    when {
        holderName == null -> {
            var name by remember { mutableStateOf("") }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardWhite)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Is this your pass? Add the name on your tickets once and the phone checks every pass.",
                    style = HomeType.caption,
                    color = Ink
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name on tickets") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = { onHolderName(name) }, enabled = name.isNotBlank()) {
                    Text("Save name", color = Accent)
                }
            }
        }
        draft.nameCheck == NameCheck.MATCH -> Banner(
            icon = Icons.Outlined.CheckCircle,
            text = "Passenger matches your name.",
            fill = Income.copy(alpha = 0.12f),
            ink = Income
        )
        draft.nameCheck == NameCheck.MISMATCH -> Banner(
            icon = Icons.Outlined.WarningAmber,
            text = "This pass is for $holder, not $holderName. You can still add it.",
            fill = CheckFill,
            ink = CheckInk
        )
        else -> Unit
    }
}

@Composable
private fun Banner(icon: ImageVector, text: String, fill: Color, ink: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, style = HomeType.caption, color = ink, modifier = Modifier.recordingPrivacyCover())
    }
}

@Composable
private fun Notice(text: String) {
    Banner(icon = Icons.Outlined.WarningAmber, text = text, fill = CheckFill, ink = CheckInk)
}

@Composable
private fun ReviewFieldRow(field: PassField, value: DraftField?, onEdit: (PassField, String) -> Unit) {
    val flagged = value?.warning != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (flagged) CheckFill.copy(alpha = 0.45f) else Color.Transparent)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value?.value.orEmpty(),
                onValueChange = { onEdit(field, it) },
                label = { Text(field.label) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .recordingPrivacyCover()
            )
            if (value != null) {
                Spacer(modifier = Modifier.width(8.dp))
                SourceChip(value.source.label, value.source)
            }
        }
        value?.warning?.let { Text(it, style = HomeType.caption, color = CheckInk) }
    }
}

@Composable
private fun SourceChip(text: String, source: FieldSource) {
    val (fill, ink) = when (source) {
        FieldSource.BARCODE, FieldSource.PKPASS -> Income.copy(alpha = 0.12f) to Income
        FieldSource.TEXT -> SoftBlue to Accent
        FieldSource.AI -> CheckFill to CheckInk
        FieldSource.USER -> Canvas to Ink
    }
    Text(
        text = text,
        style = HomeType.caption,
        fontWeight = FontWeight.SemiBold,
        color = ink,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}
