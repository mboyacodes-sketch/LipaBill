package com.lipabill.app.ui.auth

import androidx.compose.foundation.Canvas as DrawCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lipabill.app.auth.AuthUiState
import com.lipabill.app.ui.components.LipaBillMark
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.ui.theme.ActionPay
import com.lipabill.app.ui.theme.ActionTickets
import com.lipabill.app.ui.theme.BrandNavy
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.OnPastel
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space

/**
 * Lock screen: FINS soft-UI layout, LipaBill navy / soft-blue palette
 * (logo → Send/Pay/Insights/Passes card fan → quote → body → Open LipaBill).
 */
/** Deeper navy for the CTA gradient foot (paired with [Accent]). */
private val AccentDeep = Color(0xFF1A2F4A)

@Composable
fun AuthGateScreen(
    state: AuthUiState,
    errorMessage: String?,
    onUnlockClick: () -> Unit,
    onOpenLockSettings: () -> Unit,
    onAutoPrompt: () -> Unit,
    autoPrompt: Boolean = true
) {
    when (state) {
        AuthUiState.Locked -> {
            if (autoPrompt) {
                LaunchedEffect(Unit) { onAutoPrompt() }
            }
            LockedContent(
                errorMessage = errorMessage,
                onUnlockClick = onUnlockClick
            )
        }
        AuthUiState.LockScreenMissing -> {
            NoLockScreenContent(onOpenSettings = onOpenLockSettings)
        }
        AuthUiState.Unlocked -> Unit
    }
}

@Composable
private fun LockedContent(
    errorMessage: String?,
    onUnlockClick: () -> Unit
) {
    val glow = SoftBlue
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        // Soft navy-blue wash behind copy + CTA (LipaBill SoftBlue, not FINS lime).
        DrawCanvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.88f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glow.copy(alpha = 0.70f),
                        glow.copy(alpha = 0.28f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = size.width * 0.95f
                ),
                radius = size.width * 0.95f,
                center = Offset(cx, cy)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Brand + fan as one mid-screen cluster. Logo stays *above* the fan
            // box — the rotated Send card already peeks to the top edge, so
            // drawing the brand inside that box overlaps.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BrandHeader(
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    WalletCardFan()
                }
            }
            Text(
                text = "“Your money.\nOne clear story.”",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Unlock to see your money, send or pay, and show your passes. " +
                    "Everything is encrypted and stays on this phone.",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = GeometricSansFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Mute,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            )
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            UnlockCta(
                onClick = onUnlockClick,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BrandHeader(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        LipaBillMark(size = 24.dp, elevated = false)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "LipaBill",
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                letterSpacing = (-0.2).sp
            ),
            color = Ink
        )
    }
}

/**
 * Hero fan: the four home actions — Send, Pay, Insights, Passes —
 * with Accent + on the top-right of the stack.
 */
@Composable
private fun WalletCardFan() {
    val navy = Accent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            // Decorative preview of home actions — not tappable until unlock.
            .clearAndSetSemantics {
                contentDescription = "Preview of Send, Pay, Insights, and Passes after unlock"
            },
        contentAlignment = Alignment.Center
    ) {
        // Send — navy peek, spills left edge.
        SoftActionCard(
            fill = navy,
            ink = OnAccent,
            icon = Icons.Outlined.NorthEast,
            iconFace = SoftBlue,
            iconTint = BrandNavy,
            label = "Send",
            caption = "Skip the stress",
            detail = "Right SIM, PIN never stored",
            showDetails = false,
            modifier = Modifier
                .offset(x = (-96).dp, y = (-56).dp)
                .rotate(-18f)
                .zIndex(0f),
            width = 248.dp,
            height = 154.dp
        )
        // Insights — cream peek, spills right edge.
        SoftActionCard(
            fill = ActionMetrics,
            ink = OnPastel,
            icon = Icons.Outlined.BarChart,
            iconFace = CardWhite,
            iconTint = BrandNavy,
            label = "Insights",
            caption = "Where it went",
            detail = "Wallets and banks, one picture",
            showDetails = false,
            modifier = Modifier
                .offset(x = 108.dp, y = (-28).dp)
                .rotate(18f)
                .zIndex(1f),
            width = 248.dp,
            height = 154.dp
        )
        // Passes — white hero, lower right, overflows right.
        SoftActionCard(
            fill = CardWhite,
            ink = Ink,
            icon = Icons.Outlined.ConfirmationNumber,
            iconFace = ActionTickets,
            iconTint = BrandNavy,
            label = "Passes",
            caption = "Your passes",
            detail = "Tickets and boarding, ready",
            showDetails = true,
            modifier = Modifier
                .offset(x = 56.dp, y = 52.dp)
                .rotate(8f)
                .zIndex(2f),
            width = 268.dp,
            height = 168.dp,
            elevated = true
        )
        // Pay — front left hero, overflows left.
        SoftActionCard(
            fill = ActionPay,
            ink = OnPastel,
            icon = Icons.Outlined.Payments,
            iconFace = CardWhite,
            iconTint = BrandNavy,
            label = "Pay",
            caption = "Till, paybill, buy goods",
            detail = "Confirm on this phone",
            showDetails = true,
            modifier = Modifier
                .offset(x = (-52).dp, y = (-4).dp)
                .rotate(-10f)
                .zIndex(3f),
            width = 284.dp,
            height = 176.dp,
            elevated = true
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 138.dp, y = (-64).dp)
                .zIndex(6f)
                .size(48.dp)
                .shadow(10.dp, CircleShape, spotColor = navy.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(navy),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = OnAccent,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun SoftActionCard(
    fill: Color,
    ink: Color,
    icon: ImageVector,
    iconFace: Color,
    iconTint: Color,
    label: String,
    caption: String,
    detail: String,
    showDetails: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 220.dp,
    height: Dp = 136.dp,
    elevated: Boolean = false
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(
                elevation = if (elevated) 18.dp else 8.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = if (elevated) 0.12f else 0.07f),
                ambientColor = Color.Black.copy(alpha = 0.04f)
            )
            .clip(shape)
            .background(fill)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Icon + label | home action name (was SIM / VISA).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(iconFace),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (showDetails) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = ink
                    )
                }
            }
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.0.sp
                ),
                color = ink.copy(alpha = 0.40f)
            )
        }
        if (showDetails) {
            Column {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = GeometricSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        letterSpacing = (-0.4).sp
                    ),
                    color = ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = GeometricSansFamily,
                        fontSize = 13.sp
                    ),
                    color = ink.copy(alpha = 0.50f)
                )
            }
        } else {
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

@Composable
private fun UnlockCta(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val top = Accent
    val bottom = AccentDeep
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(16.dp, shape, spotColor = bottom.copy(alpha = 0.35f))
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(top, bottom)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Open LipaBill",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = GeometricSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            ),
            color = OnAccent
        )
    }
}

@Composable
private fun NoLockScreenContent(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.WarningAmber,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(Space.section))
            Text(
                text = "No screen lock found",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = GeometricSansFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = "Set up a screen lock, fingerprint, or face unlock first. " +
                    "LipaBill uses it to keep your money private.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mute,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.section))
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Set screen lock")
            }
        }
    }
}
