package com.lipabill.app.ui.sheet

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.util.SheetCaret
import com.lipabill.app.ui.util.bringIntoViewOnFocus
import com.lipabill.app.ui.util.caretScrollTarget
import com.lipabill.app.ui.util.sanitizeSheetLine
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Cursor for one Send, Pay, or search field. The view-model still owns the string;
 * this keeps the caret when that string has not changed.
 */
class SheetTextEditor(initial: String = "") {
    var caret by mutableStateOf(SheetCaret(initial, initial.length, initial.length))
        private set

    /** Last string observed from the view-model, so a slow update cannot wipe a keystroke. */
    private var seenExternal = initial
    private var localPending = false

    val text: String get() = caret.text

    val fieldValue: TextFieldValue
        get() = TextFieldValue(caret.text, TextRange(caret.start, caret.end))

    fun bind(external: String) {
        if (external == caret.text) {
            seenExternal = external
            localPending = false
            return
        }
        if (localPending && external == seenExternal) return
        seenExternal = external
        localPending = false
        caret = SheetCaret(external, external.length, external.length)
    }

    fun insert(chunk: String, sanitize: (String) -> String = ::sanitizeSheetLine): String =
        publish(caret.insert(chunk, sanitize))

    fun backspace(sanitize: (String) -> String = ::sanitizeSheetLine): String =
        publish(caret.backspace(sanitize))

    fun applyUser(
        next: TextFieldValue,
        sanitize: (String) -> String = ::sanitizeSheetLine
    ): String = publish(
        caret.applyUser(next.text, next.selection.start, next.selection.end, sanitize)
    )

    /** Replace the whole value, such as a clear button, without waiting for the view-model. */
    fun overwrite(text: String) {
        localPending = true
        caret = SheetCaret(text, text.length, text.length)
    }

    private fun publish(next: SheetCaret): String {
        caret = next
        localPending = true
        return caret.text
    }
}

@Composable
fun rememberSheetEditor(external: String): SheetTextEditor {
    val editor = remember { SheetTextEditor(external) }
    if (editor.text != external) editor.bind(external)
    return editor
}

/**
 * Real text field: drag sideways to reach hidden text, tap to place the cursor,
 * long-press to select, then copy, cut, or paste. The in-app keyboard writes at
 * the caret. [onBulkText] can claim a multi-character insert (a paste) and return
 * true to keep it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableSheetField(
    editor: SheetTextEditor,
    onText: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sanitize: (String) -> String = ::sanitizeSheetLine,
    onBulkText: ((String) -> Boolean)? = null,
    onFocused: (Boolean) -> Unit = {},
    shape: Shape = RoundedCornerShape(14.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val interaction = remember { MutableInteractionSource() }
    val textStyle = SheetInputStyle.copy(color = if (enabled) Ink else Mute)
    val textPx = textMeasurer.measure(
        text = editor.text,
        style = textStyle,
        softWrap = false,
        maxLines = 1
    ).size.width
    val innerWidth = with(density) { (textPx + 16.dp.roundToPx()).toDp().coerceAtLeast(1.dp) }
    var viewportPx by remember { mutableIntStateOf(0) }
    var cursorX by remember { mutableFloatStateOf(0f) }
    val caretPad = with(density) { 24.dp.toPx() }

    LaunchedEffect(cursorX, viewportPx, scroll.maxValue) {
        val target = caretScrollTarget(cursorX, viewportPx, scroll.value, caretPad) ?: return@LaunchedEffect
        scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
    }

    BasicTextField(
        value = editor.fieldValue,
        onValueChange = { next ->
            val before = editor.fieldValue
            val selected = if (before.selection.collapsed) 0 else before.selection.length
            val added = next.text.length - (before.text.length - selected)
            if (added > 1 && onBulkText?.invoke(next.text) == true) {
                return@BasicTextField
            }
            onText(editor.applyUser(next, sanitize))
        },
        modifier = modifier
            .fillMaxWidth()
            .recordingPrivacyCover(editor.text.isNotBlank())
            .bringIntoViewOnFocus(delayMs = 80L)
            .onFocusChanged { onFocused(it.isFocused) },
        enabled = enabled,
        singleLine = false,
        maxLines = 1,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        cursorBrush = SolidColor(if (enabled) Ink else Mute),
        interactionSource = interaction,
        onTextLayout = { result ->
            val offset = editor.fieldValue.selection.end.coerceIn(0, editor.text.length)
            if (offset <= result.layoutInput.text.length) {
                cursorX = result.getCursorRect(offset).left
            }
        },
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = editor.text,
                innerTextField = {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .onSizeChanged { viewportPx = it.width }
                            .scrollTextByDrag(scroll)
                            .horizontalScroll(scroll)
                    ) {
                        Box(Modifier.width(innerWidth)) {
                            innerTextField()
                        }
                    }
                },
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                placeholder = {
                    Text(placeholder, style = HomeType.body, color = Mute)
                },
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
                colors = colors,
                contentPadding = contentPadding,
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interaction,
                        colors = colors,
                        shape = shape
                    )
                }
            )
        }
    )
}

/**
 * Horizontal drag scrolls the line. Taps and long-presses still reach the text
 * field so the cursor, selection, and copy/paste menu keep working.
 */
private fun Modifier.scrollTextByDrag(scroll: ScrollState): Modifier = composed {
    val scope = rememberCoroutineScope()
    var fling by remember { mutableStateOf<Job?>(null) }
    pointerInput(scroll) {
        val decay = splineBasedDecay<Float>(this)
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fling?.cancel()
            val tracker = VelocityTracker()
            var scrolling = false
            var lastX = down.position.x
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                tracker.addPosition(change.uptimeMillis, change.position)
                val dx = change.position.x - lastX
                lastX = change.position.x
                val traveled = change.position - down.position
                if (!scrolling && traveled.getDistance() > viewConfiguration.touchSlop) {
                    scrolling = abs(traveled.x) > abs(traveled.y) && scroll.maxValue > 0
                }
                if (scrolling) {
                    change.consume()
                    scroll.dispatchRawDelta(-dx)
                }
            }
            if (!scrolling) return@awaitEachGesture
            val vx = tracker.calculateVelocity().x
            if (abs(vx) < 200f) return@awaitEachGesture
            fling = scope.launch {
                var latest = scroll.value.toFloat()
                AnimationState(
                    initialValue = latest,
                    initialVelocity = -vx
                ).animateDecay(decay) {
                    val delta = value - latest
                    val consumed = scroll.dispatchRawDelta(delta)
                    latest += consumed
                    if (abs(delta - consumed) > 0.5f) cancelAnimation()
                }
            }
        }
    }
}
