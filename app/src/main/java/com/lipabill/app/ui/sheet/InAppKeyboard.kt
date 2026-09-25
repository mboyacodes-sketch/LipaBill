package com.lipabill.app.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.KeyboardCapslock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.LocalAppType
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val KeyboardCanvas = Color(0xFFF7F7F8)

/** Large type on compact keys — keep overall keyboard height tight. */
private val KeyHeight = 42.dp
private val KeyIconSize = 22.dp
private val KeyGap = 4.dp
private val RowGap = 6.dp

/** Hold delete: wait then auto-repeat, like a system keyboard. */
private const val BackspaceHoldDelayMs = 400L
private const val BackspaceRepeatMs = 55L

@Composable
private fun keyLetterStyle(): TextStyle {
    val scale = LocalAppType.current.scale
    return TextStyle(
        fontFamily = GeometricSansFamily,
        fontSize = (24f * scale).sp,
        lineHeight = (28f * scale).sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun keyLabelStyle(): TextStyle {
    val scale = LocalAppType.current.scale
    return TextStyle(
        fontFamily = GeometricSansFamily,
        fontSize = (18f * scale).sp,
        lineHeight = (22f * scale).sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

private val LetterRows = listOf(
    listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
    listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
    listOf("Z", "X", "C", "V", "B", "N", "M")
)

private val DigitRows = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("-", "/", ":", ";", "(", ")", "@", "&", ".", ","),
    listOf("#", "%", "*", "+", "=", "_", "'", "!", "?", "\"")
)

/**
 * In-app QWERTY / digits keyboard that lives in the sheet layout (no system IME).
 * Pair with [InterceptSystemIme] around the focused text field.
 */
@Composable
fun InAppKeyboard(
    onChar: (String) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Start on the digit pad (useful for phone / account fields). */
    startOnDigits: Boolean = false
) {
    var digitsMode by remember(startOnDigits) { mutableStateOf(startOnDigits) }
    var shift by remember { mutableStateOf(false) }
    val rows = if (digitsMode) DigitRows else LetterRows

    val letterStyle = keyLetterStyle()
    val labelStyle = keyLabelStyle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(KeyboardCanvas)
            .padding(Space.gap),
        verticalArrangement = Arrangement.spacedBy(RowGap)
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KeyGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!digitsMode && rowIndex == 2) {
                    InAppKey(
                        weight = 1.35f,
                        accent = shift,
                        onClick = { shift = !shift }
                    ) {
                        Icon(
                            Icons.Outlined.KeyboardCapslock,
                            contentDescription = "Shift",
                            tint = if (shift) Accent else Ink,
                            modifier = Modifier.size(KeyIconSize)
                        )
                    }
                }
                row.forEach { key ->
                    val shown = when {
                        digitsMode -> key
                        shift -> key
                        else -> key.lowercase()
                    }
                    InAppKey(
                        weight = 1f,
                        onClick = {
                            onChar(shown)
                            if (shift && !digitsMode) shift = false
                        }
                    ) {
                        Text(
                            text = shown,
                            style = letterStyle,
                            color = Ink,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
                // Delete beside M (letters) / last symbol key (digits)
                if (rowIndex == 2) {
                    InAppKey(weight = 1.35f, onClick = onBackspace, repeatOnHold = true) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Backspace,
                            contentDescription = "Delete",
                            tint = Ink,
                            modifier = Modifier.size(KeyIconSize)
                        )
                    }
                }
            }
        }

        // Bottom row stretches full width — no delete here
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KeyGap)
        ) {
            InAppKey(
                weight = 1.5f,
                accent = true,
                onClick = {
                    digitsMode = !digitsMode
                    shift = false
                }
            ) {
                Text(
                    text = if (digitsMode) "ABC" else "123",
                    style = labelStyle,
                    color = Accent
                )
            }
            InAppKey(weight = 4f, onClick = { onChar(" ") }) {
                Text("space", style = labelStyle, color = Mute)
            }
            InAppKey(weight = 1.6f, accent = true, onClick = onDone) {
                Text("Done", style = labelStyle, color = Accent)
            }
        }
    }
}

@Composable
private fun RowScope.InAppKey(
    weight: Float,
    onClick: () -> Unit,
    accent: Boolean = false,
    /** Press once, then auto-fire while held (backspace). */
    repeatOnHold: Boolean = false,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val pressModifier = if (repeatOnHold) {
        Modifier.pointerInput(onClick) {
            detectTapGestures(
                onPress = {
                    onClick()
                    val repeater = scope.launch {
                        delay(BackspaceHoldDelayMs)
                        while (isActive) {
                            onClick()
                            delay(BackspaceRepeatMs)
                        }
                    }
                    try {
                        tryAwaitRelease()
                    } finally {
                        repeater.cancel()
                    }
                }
            )
        }
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = Modifier
            .weight(weight)
            .height(KeyHeight)
            .shadow(1.dp, RoundedCornerShape(10.dp), spotColor = Color.Black.copy(alpha = 0.05f))
            .clip(RoundedCornerShape(10.dp))
            .background(if (accent) SoftBlue else CardWhite)
            .then(pressModifier),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
