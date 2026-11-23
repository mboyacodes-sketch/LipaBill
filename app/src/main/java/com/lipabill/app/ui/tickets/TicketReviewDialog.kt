package com.lipabill.app.ui.tickets

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.model.firstNotesValue
import com.lipabill.app.data.model.notesValue
import com.lipabill.app.data.model.withNotesValue
import com.lipabill.app.data.tickets.EventPresets
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.data.tickets.TicketImport
import com.lipabill.app.data.tickets.intelligence.ExtractedField
import com.lipabill.app.data.tickets.intelligence.PassExtraction
import com.lipabill.app.data.tickets.intelligence.PassField
import com.lipabill.app.data.tickets.intelligence.PassKind
import com.lipabill.app.data.tickets.intelligence.PassLifecycleResolver
import com.lipabill.app.data.tickets.intelligence.PassValidator
import com.lipabill.app.ui.LipaChoiceChip
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.util.formatEventSchedule

/**
 * Review step for PDF / photo event tickets: shows what was read, flags what wasn't found,
 * and lets the user fix it before the pass is saved. Saving needs the entry code, a date, and
 * the event name or the venue (gate, seat and tier stay optional). A ticket that mentions a
 * known upcoming event ([EventPresets]) gets that event's blanks filled in, and a one-tap chip
 * does the same for a QR-only ticket.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TicketReviewDialog(
    draft: TicketImport,
    /** Last flag: also save the file's other tickets as their own passes. */
    onSave: (TicketImport, TicketDocumentKind, Boolean) -> Unit,
    onCancel: () -> Unit,
    /** How each field was read; flags the ones worth a second look. */
    extraction: PassExtraction? = null,
    /** Kind chosen when adding; overridden when classification asks the user. */
    initialKind: TicketDocumentKind = TicketDocumentKind.EVENT,
    /** Draws the original file small, so fields can be checked against it. */
    loadPreview: (suspend () -> Bitmap?)? = null,
    /** Other gate codes in the same file (a group order prints one ticket per page). */
    extraTicketCount: Int = 0
) {
    val preview by produceState<Bitmap?>(null, draft) { value = loadPreview?.invoke() }
    val context = LocalContext.current
    val guessedKind = when (extraction?.classification?.kind) {
        PassKind.RAIL -> TicketDocumentKind.SGR_TICKET
        else -> TicketDocumentKind.EVENT
    }
    var confirmedKind by remember(initialKind, guessedKind) {
        mutableStateOf(
            when {
                initialKind == TicketDocumentKind.EVENT ||
                    initialKind == TicketDocumentKind.SGR_TICKET -> initialKind
                else -> guessedKind
            }
        )
    }
    // Recognised event: fill its blanks up front; printed values on the ticket stay.
    val recognised = remember { EventPresets.match(draft.title, draft.venue, draft.notes) }
    val prefilled = remember { recognised?.let { EventPresets.applyTo(draft, it) } ?: draft }
    var title by remember { mutableStateOf(prefilled.title.takeUnless { EventPresets.isGenericTitle(it) }.orEmpty()) }
    var venue by remember { mutableStateOf(prefilled.venue.orEmpty()) }
    var startsAt by remember { mutableStateOf(prefilled.startsAtMillis) }
    var filledFrom by remember { mutableStateOf(recognised?.takeIf { prefilled != draft }) }
    var tier by remember { mutableStateOf(draft.notes.firstNotesValue("Tier").orEmpty()) }
    var guest by remember { mutableStateOf(draft.notes.firstNotesValue("Guest", "Attendee", "Name").orEmpty()) }
    val hasCode = draft.barcodeValue.isNotBlank()
    var saveAll by remember { mutableStateOf(extraTicketCount > 0) }
    val needsKindPick = extraction?.classification?.needsUserConfirmation == true
    // Sparse rule: code + date + (name or venue). Gate, seat and tier are optional.
    val canSave = hasCode && startsAt != null && (title.isNotBlank() || venue.isNotBlank())

    fun build(): TicketImport {
        val originalStart = draft.startsAtMillis
        val ends = draft.notes.notesValue("Ends")?.toLongOrNull()
        // Moving the start moves a known end by the same amount.
        val newEnds = if (ends != null && originalStart != null && startsAt != null) {
            ends + (startsAt!! - originalStart)
        } else {
            ends
        }
        val notes = draft.notes
            .withNotesValue("Tier", tier)
            .withNotesValue("Guest", guest)
            .withNotesValue("Ends", newEnds?.toString())
        val edited = draft.copy(
            // A venue-only ticket is still a pass: name it after the venue.
            title = title.trim().ifBlank { "Event at ${venue.trim().substringBefore(',')}" },
            venue = venue.trim().ifBlank { null },
            startsAtMillis = startsAt,
            notes = notes
        )
        // Date edits (and cancelled wording the user typed) re-decide Active / Past due / Used.
        return edited.copy(status = PassLifecycleResolver.resolveDraft(edited).status)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                "Check your ticket",
                fontFamily = GeometricSansFamily,
                color = Ink
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Here's what we read. Fix anything that's off before saving.",
                    color = Mute,
                    fontSize = 13.sp
                )
                preview?.let { bitmap ->
                    // The original carries the entry code and the holder's name.
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Your original ticket",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Hairline)
                            .recordingPrivacyCover(true)
                    )
                }
                val presets = EventPresets.upcoming()
                if (presets.isNotEmpty() && (title.isBlank() || venue.isBlank() || startsAt == null)) {
                    Text("Quick fill", color = Mute, fontSize = 12.sp)
                    presets.forEach { preset ->
                        LipaChoiceChip(
                            label = preset.label,
                            selected = filledFrom == preset,
                            onClick = {
                                // Fill only what is missing; anything already there stays.
                                if (title.isBlank()) title = preset.title
                                if (venue.isBlank()) venue = preset.venue
                                if (startsAt == null) startsAt = preset.startsAtMillis
                                filledFrom = preset
                            }
                        )
                    }
                }
                filledFrom?.let {
                    Text(
                        "Details filled from ${it.label}. Check them against your ticket; its printed time wins.",
                        color = Mute,
                        fontSize = 12.sp
                    )
                }
                if (needsKindPick) {
                    Text(
                        "What kind of pass is this?",
                        color = Expense,
                        fontSize = 12.sp
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LipaChoiceChip(
                            label = "Event",
                            selected = confirmedKind == TicketDocumentKind.EVENT,
                            onClick = { confirmedKind = TicketDocumentKind.EVENT }
                        )
                        LipaChoiceChip(
                            label = "SGR",
                            selected = confirmedKind == TicketDocumentKind.SGR_TICKET,
                            onClick = { confirmedKind = TicketDocumentKind.SGR_TICKET }
                        )
                    }
                }
                fun check(field: PassField, current: String, original: String?) =
                    extraction?.needsReview(field) == true && current == original.orEmpty()
                ReviewField(
                    "Event", title, required = venue.isBlank(),
                    check = check(PassField.TITLE, title, extraction?.fields?.get(PassField.TITLE)?.value),
                    onChange = { title = it }
                )
                ReviewField(
                    "Venue", venue, required = title.isBlank(),
                    check = check(PassField.VENUE, venue, draft.venue),
                    onChange = { venue = it }
                )
                DateRow(
                    startsAt = startsAt,
                    notes = draft.notes,
                    onPick = {
                        pickEventDateTime(context, startsAt) { startsAt = it }
                    }
                )
                ReviewField("Ticket type", tier, required = false, onChange = { tier = it })
                ReviewField("Guest", guest, required = false, private = true, onChange = { guest = it })
                CodeRow(hasCode, extraction?.fields?.get(PassField.BARCODE))
                if (extraTicketCount > 0) {
                    val total = extraTicketCount + 1
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = saveAll, role = Role.Checkbox, onValueChange = { saveAll = it })
                    ) {
                        Checkbox(checked = saveAll, onCheckedChange = null)
                        Text(
                            "This file has $total tickets. Save each as its own pass.",
                            color = Ink,
                            fontSize = 13.sp
                        )
                    }
                }
                // Non-blocking checks (date in the past, end before start, code still pending).
                val validateKind = if (confirmedKind == TicketDocumentKind.SGR_TICKET) {
                    PassKind.RAIL
                } else {
                    PassKind.EVENT
                }
                val edited = build()
                val lifecycleIssue = PassLifecycleResolver.resolveDraft(edited).issue
                (PassValidator.validate(edited, validateKind) + listOfNotNull(lifecycleIssue))
                    .distinct()
                    .filterNot { it.blocking }
                    .forEach { issue -> Text(issue.message, color = Expense, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(build(), confirmedKind, saveAll) }, enabled = canSave) {
                Text(
                    if (saveAll) "Save ${extraTicketCount + 1} passes" else "Save pass",
                    fontFamily = GeometricSansFamily
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", fontFamily = GeometricSansFamily) }
        }
    )
}

@Composable
private fun ReviewField(
    label: String,
    value: String,
    required: Boolean,
    private: Boolean = false,
    /** Read with low confidence and not yet edited. */
    check: Boolean = false,
    onChange: (String) -> Unit
) {
    val missing = value.isBlank()
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(if (required) "$label (required)" else label) },
        singleLine = true,
        isError = required && missing,
        supportingText = when {
            missing -> {
                {
                    Text(
                        if (required) "Not found on the ticket. Please add it." else "Not found on the ticket.",
                        color = if (required) Expense else Mute
                    )
                }
            }
            check -> {
                { Text("Hard to read on the ticket. Check it.", color = Expense) }
            }
            else -> null
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier
            .fillMaxWidth()
            .recordingPrivacyCover(private && !missing)
    )
}

@Composable
private fun DateRow(startsAt: Long?, notes: String?, onPick: () -> Unit) {
    val schedule = formatEventSchedule(startsAt, notes)
    val label = listOfNotNull(schedule.date, schedule.time).joinToString(" · ")
    val missing = startsAt == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClickLabel = "Change date", onClick = onPick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Event,
            contentDescription = null,
            tint = if (missing) Expense else Ink,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Date & time (required)", color = if (missing) Expense else Mute, fontSize = 12.sp)
            Text(
                if (missing) "Not found. Tap to set it." else label,
                color = if (missing) Expense else Ink,
                fontSize = 15.sp
            )
        }
        Text("Change", color = Ink, fontSize = 13.sp)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Hairline)
    )
}

@Composable
private fun CodeRow(hasCode: Boolean, code: ExtractedField? = null) {
    // Never show the code itself here: it is the ticket. Say only how sure the read is.
    // Missing provenance or non-gate sources → ask the user to check (fail closed).
    val typedOut = hasCode && (code == null || !code.source.isGateDecoded)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (hasCode) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = if (hasCode) Income else Expense,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            when {
                !hasCode -> "No entry code found on this ticket"
                typedOut -> "Entry code read from the printed text. Check it on the pass after saving."
                else -> "Entry code found"
            },
            color = if (hasCode) Ink else Expense,
            fontSize = 13.sp
        )
    }
}
