package com.lipabill.app.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.auth.AuthUiState
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.GeometricSansFamily
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space

private val AuthCream = Color(0xFFF7F6F0)
private val AuthHeadline = Accent
private val AuthRing = Accent.copy(alpha = 0.12f)

@Composable
fun AuthGateScreen(
    state: AuthUiState,
    errorMessage: String?,
    onUnlockClick: () -> Unit,
    onContinueWithoutLock: () -> Unit,
    onAutoPrompt: () -> Unit
) {
    when (state) {
        AuthUiState.Locked -> {
            LaunchedEffect(Unit) { onAutoPrompt() }
            LockedContent(
                errorMessage = errorMessage,
                onUnlockClick = onUnlockClick
            )
        }
        AuthUiState.LockScreenMissing -> {
            NoLockScreenContent(onContinue = onContinueWithoutLock)
        }
        AuthUiState.Unlocked -> Unit
    }
}

@Composable
private fun LockedContent(
    errorMessage: String?,
    onUnlockClick: () -> Unit
) {
    val breath by rememberInfiniteTransition(label = "unlock_breath").animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "unlock_breath_alpha"
    )
    val ringPulse by rememberInfiniteTransition(label = "ring_pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthCream)
    ) {
        AuthBackdropBlobs()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LipaBillWordmark()

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ON THIS PHONE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 2.4.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = GeometricSansFamily
                        ),
                        color = Mute,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "Your payments,\nready when you are.",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = GeometricSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            lineHeight = 32.sp
                        ),
                        color = AuthHeadline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Unlock with fingerprint, face, or your phone PIN.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = GeometricSansFamily,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        color = Mute,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    FingerprintBadge(
                        breath = breath,
                        ringPulse = ringPulse
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            Button(
                onClick = onUnlockClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = CardWhite
                )
            ) {
                Text(
                    text = "Open LipaBill",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GeometricSansFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun LipaBillWordmark() {
    Text(
        text = "LipaBill",
        style = MaterialTheme.typography.displayLarge.copy(
            fontFamily = GeometricSansFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 44.sp,
            letterSpacing = (-0.6).sp,
            lineHeight = 48.sp
        ),
        color = Accent,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun FingerprintBadge(
    breath: Float,
    ringPulse: Float
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(140.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().alpha(ringPulse)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.48f
            drawCircle(color = AuthRing, radius = maxR, center = c)
            drawCircle(color = AuthRing.copy(alpha = 0.18f), radius = maxR * 0.78f, center = c)
            drawCircle(color = AuthRing.copy(alpha = 0.28f), radius = maxR * 0.58f, center = c)
        }
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = Color.Black.copy(alpha = 0.12f),
                    ambientColor = Color.Black.copy(alpha = 0.06f)
                )
                .background(CardWhite, RoundedCornerShape(22.dp))
                .alpha(breath),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = "Unlock with biometrics",
                tint = Accent,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun AuthBackdropBlobs() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Soft mint wash top-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(SoftBlue.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(w * 0.92f, h * 0.08f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.92f, h * 0.08f)
        )
        // Soft green wash bottom-left behind illustration
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Accent.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(w * 0.15f, h * 0.72f),
                radius = w * 0.7f
            ),
            radius = w * 0.7f,
            center = Offset(w * 0.15f, h * 0.72f)
        )
        val hill = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.2f, h * 0.70f, w * 0.35f, h * 0.88f, w * 0.55f, h * 0.80f)
            cubicTo(w * 0.75f, h * 0.72f, w * 0.9f, h * 0.86f, w, h * 0.78f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hill, color = Accent.copy(alpha = 0.06f))
    }
}

@Composable
private fun NoLockScreenContent(onContinue: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthCream)
    ) {
        AuthBackdropBlobs()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
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
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.block))
            Text(
                text = "This device has no biometric hardware enrolled and no PIN, " +
                    "pattern, or password. For financial history, set a lock screen " +
                    "in system Settings. You can continue for testing without one.",
                style = MaterialTheme.typography.bodyLarge,
                color = Mute,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Space.section))
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Continue without lock")
            }
            TextButton(onClick = onContinue) {
                Text("I understand the risk")
            }
        }
    }
}
