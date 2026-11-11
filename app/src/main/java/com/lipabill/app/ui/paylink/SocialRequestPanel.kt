package com.lipabill.app.ui.paylink

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.social.SocialDialCountries
import com.lipabill.app.social.SocialDialCountry
import com.lipabill.app.social.SocialLink
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.sheet.EditableSheetField
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.SheetTextEditor
import com.lipabill.app.ui.sheet.rememberSheetEditor
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Debit
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.LipaFilterChip
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.copyPlainText
import com.lipabill.app.ui.util.sanitizeDigits
import com.lipabill.app.ui.util.sharePlainText
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay

/** Kept for PayProfileScreen call sites; keyboard now lives inside the social sheet. */
data class SocialKeyboardBinding(
    val startOnDigits: Boolean,
    val onChar: (String) -> Unit,
    val onBackspace: () -> Unit,
    val onDone: () -> Unit
)

/**
 * Social Request codes — Linktree-style hub. FAB / empty CTA open a bottom sheet
 * for that exact network (add → QR). Linked rows open the same sheet on the QR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialRequestPanel(
    prefs: SecurePreferences,
    mark: ImageBitmap?,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedPlatformKey: String = prefs.socialPlatformKey,
    onPlatformKeyChange: (String) -> Unit = { prefs.socialPlatformKey = it },
    onHandlesChanged: () -> Unit = {},
    pendingAddPlatform: SocialLink.Platform? = null,
    onPendingAddConsumed: () -> Unit = {},
    forceAdd: Boolean = false,
    onForceAddConsumed: () -> Unit = {},
    onEditingChange: (Boolean) -> Unit = {},
    onKeyboardChange: (SocialKeyboardBinding?) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val fieldFocusRequester = remember { FocusRequester() }
    var platform by remember {
        mutableStateOf(SocialLink.fromStorageKey(selectedPlatformKey))
    }
    var savedHandle by remember {
        mutableStateOf(prefs.socialHandle(platform.storageKey).orEmpty())
    }
    var draft by remember { mutableStateOf(savedHandle) }
    val editor = rememberSheetEditor(draft)
    var fieldFocused by remember { mutableStateOf(false) }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetEditing by remember { mutableStateOf(false) }
    var listEpoch by remember { mutableIntStateOf(0) }
    var dialCountry by remember {
        mutableStateOf(SocialDialCountries.defaultFor(prefs.appRegion))
    }
    var dialPickerOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dialPickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun loadPhoneDraftFromSaved(handle: String) {
        val (country, national) = SocialDialCountries.splitInternational(handle, dialCountry)
        dialCountry = country
        draft = national
        editor.overwrite(national)
    }

    fun syncFromPrefs(next: SocialLink.Platform) {
        platform = next
        onPlatformKeyChange(next.storageKey)
        val handle = prefs.socialHandle(next.storageKey).orEmpty()
        savedHandle = handle
        if (next.usesPhone && handle.isNotBlank()) {
            loadPhoneDraftFromSaved(handle)
        } else {
            draft = handle
            editor.overwrite(handle)
        }
        fieldError = null
    }

    fun linkedSlides(): List<RequestQrSlide.Social> =
        SocialLink.Platform.entries.mapNotNull { entry ->
            val handle = if (entry == platform) {
                savedHandle
            } else {
                prefs.socialHandle(entry.storageKey).orEmpty()
            }
            val clean = SocialLink.canonicalHandle(entry, handle) ?: return@mapNotNull null
            RequestQrSlide.Social(SocialLink.Target(entry, clean))
        }

    val socialSlides = remember(platform, savedHandle, listEpoch) { linkedSlides() }
    val target = remember(platform, savedHandle) {
        val handle = SocialLink.canonicalHandle(platform, savedHandle) ?: return@remember null
        SocialLink.Target(platform, handle)
    }

    fun dismissSheet() {
        sheetOpen = false
        sheetEditing = false
        fieldFocused = false
        fieldError = null
        focusManager.clearFocus(force = true)
        onKeyboardChange(null)
        // Drop unsaved add — restore selection to a linked network if needed.
        if (SocialLink.canonicalHandle(platform, savedHandle) == null) {
            val fallback = SocialLink.Platform.entries.firstOrNull { entry ->
                !prefs.socialHandle(entry.storageKey).isNullOrBlank()
            }
            if (fallback != null) syncFromPrefs(fallback)
            else {
                savedHandle = ""
                draft = ""
                editor.overwrite("")
            }
        } else if (platform.usesPhone) {
            loadPhoneDraftFromSaved(savedHandle)
        } else {
            draft = savedHandle
            editor.overwrite(savedHandle)
        }
    }

    fun openView(next: SocialLink.Platform) {
        syncFromPrefs(next)
        sheetEditing = false
        dialPickerOpen = false
        fieldFocused = false
        focusManager.clearFocus(force = true)
        onKeyboardChange(null)
        sheetOpen = true
    }

    fun openAdd(next: SocialLink.Platform) {
        platform = next
        onPlatformKeyChange(next.storageKey)
        savedHandle = ""
        dialCountry = SocialDialCountries.defaultFor(prefs.appRegion)
        draft = ""
        editor.overwrite("")
        fieldError = null
        fieldFocused = false
        dialPickerOpen = false
        focusManager.clearFocus(force = true)
        sheetEditing = true
        sheetOpen = true
    }

    fun openEdit() {
        if (platform.usesPhone) {
            loadPhoneDraftFromSaved(savedHandle)
        } else {
            draft = savedHandle
            editor.overwrite(savedHandle)
        }
        fieldError = null
        dialPickerOpen = false
        sheetEditing = true
        sheetOpen = true
    }

    fun openEditFor(next: SocialLink.Platform) {
        syncFromPrefs(next)
        openEdit()
    }

    fun saveHandle() {
        val raw = if (platform.usesPhone) {
            SocialDialCountries.composeInternational(dialCountry.dial, draft)
        } else {
            draft
        }
        val clean = SocialLink.canonicalHandle(platform, raw)
        if (clean == null) {
            fieldError = if (platform.usesPhone) {
                "Enter a valid mobile number for +${dialCountry.dial}."
            } else {
                "Enter a ${platform.label} ${platform.placeholder}."
            }
            return
        }
        prefs.setSocialHandle(platform.storageKey, clean)
        savedHandle = clean
        if (platform.usesPhone) {
            loadPhoneDraftFromSaved(clean)
        } else {
            draft = clean
            editor.overwrite(clean)
        }
        fieldFocused = false
        fieldError = null
        focusManager.clearFocus(force = true)
        onKeyboardChange(null)
        sheetEditing = false
        sheetOpen = true
        onHandlesChanged()
    }

    fun startAddFirstUnset() {
        val next = SocialLink.Platform.entries.firstOrNull { entry ->
            prefs.socialHandle(entry.storageKey).isNullOrBlank()
        } ?: return
        openAdd(next)
    }

    fun deletePlatform(toRemove: SocialLink.Platform) {
        val label = toRemove.label
        prefs.setSocialHandle(toRemove.storageKey, null)
        if (toRemove == platform) {
            savedHandle = ""
            draft = ""
            editor.overwrite("")
            fieldError = null
            fieldFocused = false
            focusManager.clearFocus(force = true)
            onKeyboardChange(null)
            sheetEditing = false
            sheetOpen = false
            val fallback = SocialLink.Platform.entries.firstOrNull { entry ->
                entry != toRemove && !prefs.socialHandle(entry.storageKey).isNullOrBlank()
            }
            if (fallback != null) syncFromPrefs(fallback)
        }
        listEpoch += 1
        onHandlesChanged()
        onNotice("$label removed")
    }

    LaunchedEffect(sheetOpen, sheetEditing) {
        onEditingChange(sheetOpen && sheetEditing)
        if (!sheetOpen || !sheetEditing) onKeyboardChange(null)
    }

    LaunchedEffect(sheetOpen, sheetEditing, platform.storageKey) {
        if (!sheetOpen || !sheetEditing) return@LaunchedEffect
        delay(80)
        runCatching { fieldFocusRequester.requestFocus() }
        fieldFocused = true
    }

    LaunchedEffect(selectedPlatformKey) {
        if (sheetOpen) return@LaunchedEffect
        val next = SocialLink.fromStorageKey(selectedPlatformKey)
        if (next != platform) syncFromPrefs(next)
    }

    LaunchedEffect(pendingAddPlatform) {
        val next = pendingAddPlatform ?: return@LaunchedEffect
        openAdd(next)
        onPendingAddConsumed()
    }

    LaunchedEffect(forceAdd) {
        if (!forceAdd) return@LaunchedEffect
        startAddFirstUnset()
        onForceAddConsumed()
    }

    // If selection has no handle, land on the first linked network.
    LaunchedEffect(sheetOpen, target, socialSlides) {
        if (sheetOpen || target != null || socialSlides.isEmpty()) return@LaunchedEffect
        syncFromPrefs(socialSlides.first().platform)
    }

    BackHandler(enabled = sheetOpen) {
        if (sheetEditing) {
            val stored = prefs.socialHandle(platform.storageKey).orEmpty()
            if (stored.isNotBlank()) {
                savedHandle = stored
                draft = stored
                editor.overwrite(stored)
                sheetEditing = false
                fieldFocused = false
                fieldError = null
                focusManager.clearFocus(force = true)
                onKeyboardChange(null)
            } else {
                dismissSheet()
            }
        } else {
            dismissSheet()
        }
    }

    if (socialSlides.isEmpty()) {
        SocialEmptyOrbit(modifier = modifier)
    } else {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            SocialProfileHeader(
                mark = mark,
                linkedCount = socialSlides.size
            )
            Spacer(modifier = Modifier.height(Space.block))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                socialSlides.forEach { slide ->
                    key(slide.platform) {
                        SocialSwipeActionRow(
                            platform = slide.platform,
                            handle = displayHandle(slide.platform, slide.target.handle),
                            selected = sheetOpen && slide.platform == platform,
                            onClick = { openView(slide.platform) },
                            onEdit = { openEditFor(slide.platform) },
                            onDelete = { deletePlatform(slide.platform) }
                        )
                    }
                }
            }
            Text(
                text = "Tap for the code · right to edit · left to remove",
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { dismissSheet() },
            sheetState = sheetState,
            containerColor = Canvas,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            SocialNetworkSheet(
                platform = platform,
                target = target,
                mark = mark,
                editing = sheetEditing,
                editingExisting = !prefs.socialHandle(platform.storageKey).isNullOrBlank(),
                editor = editor,
                fieldError = fieldError,
                dialCountry = dialCountry,
                onOpenDialPicker = { dialPickerOpen = true },
                focusRequester = fieldFocusRequester,
                onDraft = {
                    draft = it
                    fieldError = null
                },
                onFocused = { fieldFocused = it },
                onSave = ::saveHandle,
                onCancelEdit = {
                    val stored = prefs.socialHandle(platform.storageKey).orEmpty()
                    if (stored.isBlank()) {
                        dismissSheet()
                    } else {
                        savedHandle = stored
                        if (platform.usesPhone) {
                            loadPhoneDraftFromSaved(stored)
                        } else {
                            draft = stored
                            editor.overwrite(stored)
                        }
                        sheetEditing = false
                        fieldFocused = false
                        fieldError = null
                        focusManager.clearFocus(force = true)
                        onKeyboardChange(null)
                    }
                },
                onCopy = {
                    val t = target ?: return@SocialNetworkSheet
                    if (context.copyPlainText("${t.platform.label} profile", t.shareUrl)) {
                        onNotice("Link copied")
                    } else {
                        onNotice("Couldn’t copy right now")
                    }
                },
                onShare = {
                    val t = target ?: return@SocialNetworkSheet
                    if (!context.sharePlainText(
                            "Share ${t.platform.label}",
                            SocialLink.shareText(t)
                        )
                    ) {
                        onNotice("Couldn’t share right now")
                    }
                },
                onKeyboardChar = { ch ->
                    draft = editor.insert(ch) { raw -> sanitizeSocialDraft(platform, raw) }
                    fieldError = null
                },
                onKeyboardBackspace = {
                    draft = editor.backspace { raw -> sanitizeSocialDraft(platform, raw) }
                },
                onKeyboardDone = {
                    fieldFocused = false
                    focusManager.clearFocus(force = true)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
            )
        }
    }

    if (dialPickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { dialPickerOpen = false },
            sheetState = dialPickerState,
            containerColor = Canvas,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            SocialDialCountryPicker(
                selected = dialCountry,
                onSelect = { country ->
                    dialCountry = country
                    dialPickerOpen = false
                    fieldError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun SocialNetworkSheet(
    platform: SocialLink.Platform,
    target: SocialLink.Target?,
    mark: ImageBitmap?,
    editing: Boolean,
    editingExisting: Boolean,
    editor: SheetTextEditor,
    fieldError: String?,
    dialCountry: SocialDialCountry,
    onOpenDialPicker: () -> Unit,
    focusRequester: FocusRequester,
    onDraft: (String) -> Unit,
    onFocused: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onKeyboardChar: (String) -> Unit,
    onKeyboardBackspace: () -> Unit,
    onKeyboardDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SocialBrandMark(platform = platform, size = 40.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                editing && editingExisting -> "Edit ${platform.label}"
                editing -> "Add ${platform.label}"
                else -> platform.label
            },
            style = HomeType.section.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            color = Ink,
            textAlign = TextAlign.Center
        )
        if (editing) {
            Text(
                text = if (platform.usesPhone) {
                    "Pick a country code, then enter the number."
                } else {
                    "Enter your ${platform.placeholder}."
                },
                style = HomeType.body,
                color = Mute,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(Space.block))
            if (platform.usesPhone) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardWhite)
                            .border(1.dp, SoftBlue, RoundedCornerShape(14.dp))
                            .clickable(onClick = onOpenDialPicker)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = dialCountry.chipLabel,
                            style = HomeType.rowTitle,
                            color = Ink,
                            maxLines = 1
                        )
                        Icon(
                            Icons.Outlined.ArrowDropDown,
                            contentDescription = "Country code",
                            tint = Mute,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    InterceptSystemIme {
                        EditableSheetField(
                            editor = editor,
                            onText = onDraft,
                            placeholder = "7XX XXX XXX",
                            sanitize = { sanitizeSocialDraft(platform, it) },
                            onFocused = onFocused,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                        )
                    }
                }
            } else {
                InterceptSystemIme {
                    EditableSheetField(
                        editor = editor,
                        onText = onDraft,
                        placeholder = if (platform.prefixHint.isNotEmpty()) {
                            "${platform.prefixHint}${platform.placeholder}"
                        } else {
                            platform.placeholder
                        },
                        sanitize = { sanitizeSocialDraft(platform, it) },
                        onFocused = onFocused,
                        leadingIcon = {
                            SocialBrandMark(platform = platform, size = 22.dp)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }
            Text(
                text = fieldError ?: if (platform.usesPhone) {
                    "Saved as +${dialCountry.dial}… on this phone only."
                } else {
                    "Saved on this phone only."
                },
                style = HomeType.caption,
                color = if (fieldError != null) Debit else Mute,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, top = 8.dp)
            )
            Spacer(modifier = Modifier.height(Space.block))
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent
                )
            ) {
                Text(
                    text = if (editingExisting) "Save changes" else "Save profile",
                    style = HomeType.rowTitle
                )
            }
            Text(
                text = "Cancel",
                style = HomeType.rowTitle,
                color = Mute,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(onClick = onCancelEdit)
                    .padding(vertical = 10.dp)
            )
            Spacer(modifier = Modifier.height(Space.gap))
            InAppKeyboard(
                startOnDigits = platform.usesPhone,
                onChar = onKeyboardChar,
                onBackspace = onKeyboardBackspace,
                onDone = onKeyboardDone,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (target != null) {
            Text(
                text = displayHandle(platform, target.handle),
                style = HomeType.body,
                color = Mute,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .recordingPrivacyCover()
            )
            Spacer(modifier = Modifier.height(20.dp))
            RequestQrSlideCard(
                slide = RequestQrSlide.Social(target),
                mark = mark,
                onCopy = onCopy
            )
            Text(
                text = "Tap the code to copy the link",
                style = HomeType.caption,
                color = Mute,
                modifier = Modifier.padding(top = 12.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent
                )
            ) {
                Text("Share", style = HomeType.rowTitle)
                Spacer(modifier = Modifier.size(8.dp))
                Icon(
                    Icons.Outlined.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SocialProfileHeader(
    mark: ImageBitmap?,
    linkedCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(CardWhite)
                .border(1.dp, SoftBlue, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (mark != null) {
                Image(
                    bitmap = mark,
                    contentDescription = "LipaBill",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    Icons.Outlined.PersonAdd,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Your socials",
            style = HomeType.section.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = Ink,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (linkedCount == 1) "1 linked" else "$linkedCount linked",
            style = HomeType.caption,
            color = Mute,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SocialSwipeActionRow(
    platform: SocialLink.Platform,
    handle: String,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var removing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val dismissState = rememberSwipeToDismissBoxState(
        // Commit a bit earlier so the gesture feels decisive without a long drag.
        positionalThreshold = { distance -> distance * 0.28f },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    removing = true
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false // spring back into place
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )
    LaunchedEffect(removing) {
        if (!removing) return@LaunchedEffect
        delay(220)
        onDelete()
    }

    AnimatedVisibility(
        visible = !removing,
        modifier = modifier.fillMaxWidth(),
        exit = fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
            shrinkVertically(
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                shrinkTowards = Alignment.Top
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val direction = dismissState.dismissDirection
            val offsetPx = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val dragProgress = (offsetPx.absoluteValue / (widthPx * 0.42f)).coerceIn(0f, 1f)
            val reveal by animateFloatAsState(
                targetValue = if (direction == SwipeToDismissBoxValue.Settled) {
                    0f
                } else {
                    dragProgress
                },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "socialSwipeReveal"
            )
            val iconPulse by animateFloatAsState(
                targetValue = if (dragProgress >= 0.85f) 1.08f else 0.82f + 0.18f * reveal,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "socialSwipeIcon"
            )
            val rowDim by animateFloatAsState(
                targetValue = 1f - 0.06f * reveal,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "socialSwipeRow"
            )

            SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier.fillMaxWidth(),
                enableDismissFromStartToEnd = true,
                enableDismissFromEndToStart = true,
                backgroundContent = {
                    val deleteSide = direction == SwipeToDismissBoxValue.EndToStart ||
                        (direction == SwipeToDismissBoxValue.Settled &&
                            dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                    val editSide = direction == SwipeToDismissBoxValue.StartToEnd ||
                        (direction == SwipeToDismissBoxValue.Settled &&
                            dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                when {
                                    deleteSide -> Expense.copy(alpha = 0.18f + 0.82f * reveal)
                                    editSide -> Accent.copy(alpha = 0.18f + 0.82f * reveal)
                                    else -> Color.Transparent
                                }
                            )
                            .padding(horizontal = 22.dp),
                        contentAlignment = when {
                            deleteSide -> Alignment.CenterEnd
                            editSide -> Alignment.CenterStart
                            else -> Alignment.Center
                        }
                    ) {
                        if (deleteSide || editSide) {
                            Icon(
                                imageVector = if (deleteSide) {
                                    Icons.Outlined.Delete
                                } else {
                                    Icons.Outlined.Edit
                                },
                                contentDescription = if (deleteSide) {
                                    "Delete ${platform.label}"
                                } else {
                                    "Edit ${platform.label}"
                                },
                                tint = if (deleteSide) Color.White else OnAccent,
                                modifier = Modifier.graphicsLayer {
                                    alpha = reveal
                                    scaleX = iconPulse
                                    scaleY = iconPulse
                                    translationX = if (deleteSide) {
                                        (1f - reveal) * 18f
                                    } else {
                                        (reveal - 1f) * 18f
                                    }
                                }
                            )
                        }
                    }
                }
            ) {
                SocialLinkedRow(
                    platform = platform,
                    handle = handle,
                    selected = selected,
                    onClick = onClick,
                    modifier = Modifier.graphicsLayer {
                        alpha = rowDim
                        scaleX = rowDim
                        scaleY = rowDim
                    }
                )
            }
        }
    }
}

@Composable
private fun SocialLinkedRow(
    platform: SocialLink.Platform,
    handle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardWhite)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Accent else SoftBlue,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SocialBrandMark(platform = platform, size = 36.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = platform.label,
                style = HomeType.rowTitle,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = handle,
                style = HomeType.caption,
                color = Mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover()
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Accent)
            )
        }
    }
}

@Composable
private fun SocialEmptyOrbit(
    modifier: Modifier = Modifier
) {
    val platforms = SocialLink.Platform.entries
    val density = LocalDensity.current
    val orbit = rememberInfiniteTransition(label = "socialEmptyOrbit")
    val angle by orbit.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )
    val radiusPx = with(density) { 84.dp.toPx() }
    val markSize = 38.dp

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = "Share your socials",
                style = HomeType.section.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                color = Ink,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tap + and pick a network. Friends scan the code and land on your page — app if they have it, web if they don’t.",
                style = HomeType.body,
                color = Mute,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .border(1.dp, SoftBlue.copy(alpha = 0.85f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SoftBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.PersonAdd,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                platforms.forEachIndexed { index, entry ->
                    val step = 360f / platforms.size
                    val deg = angle + index * step
                    val rad = Math.toRadians(deg.toDouble())
                    val x = (kotlin.math.cos(rad) * radiusPx).toFloat()
                    val y = (kotlin.math.sin(rad) * radiusPx).toFloat()
                    SocialBrandMark(
                        platform = entry,
                        size = markSize,
                        modifier = Modifier.graphicsLayer {
                            translationX = x
                            translationY = y
                            // Keep brand marks upright while the ring turns.
                            rotationZ = -deg
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun RequestEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    cta: String,
    onCta: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(SoftBlue),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = HomeType.section.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = Ink,
            textAlign = TextAlign.Center
        )
        Text(
            text = body,
            style = HomeType.body,
            color = Mute,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 8.dp, end = 8.dp)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onCta,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = OnAccent
            )
        ) {
            Text(cta, style = HomeType.rowTitle)
        }
    }
}

@Composable
internal fun RequestChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LipaFilterChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        unselectedContainer = CardWhite,
        unselectedBorder = SoftBlue
    )
}

@Composable
private fun SocialDialCountryPicker(
    selected: SocialDialCountry,
    onSelect: (SocialDialCountry) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Country code",
            style = HomeType.section.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            color = Ink
        )
        Text(
            text = "Used for WhatsApp and other phone-based profiles.",
            style = HomeType.caption,
            color = Mute,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SocialDialCountries.all.forEach { country ->
                val on = country == selected ||
                    (country.dial == selected.dial && country.iso == selected.iso)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (on) Accent else CardWhite)
                        .border(
                            width = 1.dp,
                            color = if (on) Accent else SoftBlue,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelect(country) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = country.flagEmoji,
                        fontSize = 22.sp,
                        modifier = Modifier.width(36.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = country.label,
                            style = HomeType.rowTitle,
                            color = if (on) OnAccent else Ink
                        )
                        Text(
                            text = "+${country.dial}",
                            style = HomeType.caption,
                            color = if (on) OnAccent.copy(alpha = 0.85f) else Mute
                        )
                    }
                }
            }
        }
    }
}

private fun sanitizeSocialDraft(platform: SocialLink.Platform, raw: String): String =
    if (platform.usesPhone) {
        // National number only — country code is chosen separately.
        sanitizeDigits(raw, max = 12)
    } else {
        raw.filter {
            it.isLetterOrDigit() || it == '@' || it == '.' ||
                it == '_' || it == '-' || it == '/'
        }.take(64)
    }

private fun displayHandle(platform: SocialLink.Platform, handle: String): String =
    when (platform) {
        SocialLink.Platform.WHATSAPP -> "+$handle"
        SocialLink.Platform.LINKEDIN -> "/in/$handle"
        SocialLink.Platform.YOUTUBE,
        SocialLink.Platform.INSTAGRAM,
        SocialLink.Platform.X,
        SocialLink.Platform.TIKTOK,
        SocialLink.Platform.TELEGRAM,
        SocialLink.Platform.THREADS -> "@$handle"
        SocialLink.Platform.FACEBOOK -> handle
    }
