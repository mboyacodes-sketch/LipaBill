package com.lipabill.app.ui.demo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lipabill.app.BuildConfig
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Canvas
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Expense
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.util.rememberKeyPressHaptic
import com.lipabill.app.ussd.securePinKeyRows
import kotlinx.coroutines.delay

/**
 * Debug / promo-only walkthrough that mirrors the real USSD mask
 * (steps → Yes/No confirm → PIN) without dialing *334# or storing a PIN.
 */
enum class DemoPayPhase {
    Steps,
    Confirm,
    Pin
}

@Composable
fun DemoPaymentWalkthrough(
    amountLabel: String,
    detail: String,
    totalSteps: Int = 5,
    onCancel: () -> Unit,
    onComplete: () -> Unit
) {
    check(BuildConfig.DEBUG) { "Demo payment is debug-only" }
    var phase by remember { mutableStateOf(DemoPayPhase.Steps) }
    var completed by remember { mutableIntStateOf(0) }
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(phase) {
        if (phase != DemoPayPhase.Steps) return@LaunchedEffect
        completed = 0
        val steps = totalSteps.coerceAtLeast(1)
        repeat(steps) { i ->
            delay(60)
            completed = i + 1
        }
        delay(40)
        phase = DemoPayPhase.Confirm
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF6F7F9), SoftBlue.copy(alpha = 0.55f), Color(0xFFE8EEF5))
                )
            )
    ) {
        when (phase) {
            DemoPayPhase.Steps -> DemoStepsPage(
                completed = completed,
                total = totalSteps.coerceAtLeast(1),
                onCancel = onCancel,
                modifier = Modifier.fillMaxSize()
            )
            DemoPayPhase.Confirm -> DemoConfirmPage(
                amountLabel = amountLabel,
                detail = detail,
                onYes = { phase = DemoPayPhase.Pin },
                onNo = onCancel,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            DemoPayPhase.Pin -> DemoPinPage(
                pinLength = pin.length,
                onDigit = { d -> if (pin.length < 8) pin += d },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                onConfirm = {
                    // Digits are discarded — never stored, never dialed.
                    pin = ""
                    onComplete()
                },
                onCancel = {
                    pin = ""
                    onCancel()
                },
                // Maestro only needs Confirm; digits are optional and discarded.
                confirmEnabled = true,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun DemoStepsPage(
    completed: Int,
    total: Int,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Text(
            text = "Close",
            style = HomeType.rowTitle,
            color = Accent,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardWhite)
                .clickable(onClick = onCancel)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "LipaBill",
                style = HomeType.greeting.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
                color = Accent,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "SECURE PAYMENT",
                style = HomeType.caption.copy(letterSpacing = 2.sp),
                color = Color(0xFF9BB0C9),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = if (completed >= total) "Almost there" else "Working on your payment",
                style = HomeType.section.copy(fontSize = 22.sp),
                color = Ink,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = when {
                    completed <= 0 -> "Step 1 of $total — filling the M-Pesa menu"
                    completed >= total -> "Ready for your PIN"
                    else -> "Step ${completed + 1} of $total — filling the M-Pesa menu"
                },
                style = HomeType.body,
                color = Mute,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(36.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val filled = completed.coerceIn(0, total)
                repeat(total) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (i < filled) Accent else Hairline)
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
            Text(
                text = "Keep this screen open.\nYou’ll enter your M-Pesa PIN next.",
                style = HomeType.body,
                color = Color(0xFF9BB0C9),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            TextButton(onClick = onCancel) {
                Text("Cancel payment", style = HomeType.label, color = Mute)
            }
        }
    }
}

@Composable
private fun DemoConfirmPage(
    amountLabel: String,
    detail: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(CardWhite)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Hairline)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Confirm payment",
            style = HomeType.section.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = Ink,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Same as M-Pesa’s last step — Yes continues, No cancels",
            style = HomeType.caption,
            color = Mute,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SoftBlue)
                .padding(14.dp)
        ) {
            Text(amountLabel, style = HomeType.rowTitle, color = Ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail, style = HomeType.body, color = Ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Demo walkthrough — no USSD dial", style = HomeType.caption, color = Mute)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onYes,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = OnAccent
            )
        ) {
            Text("1. Yes", style = HomeType.rowTitle)
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(
            onClick = onNo,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(SoftBlue)
        ) {
            Text("2. No", style = HomeType.rowTitle, color = Expense)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun DemoPinPage(
    pinLength: Int,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    confirmEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val haptic = rememberKeyPressHaptic()
    val confirmAlpha by animateFloatAsState(
        targetValue = if (confirmEnabled) 1f else 0.38f,
        animationSpec = tween(120),
        label = "pinConfirm"
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(CardWhite)
            .padding(horizontal = 24.dp)
            .padding(top = 18.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Hairline)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "LipaBill",
            style = HomeType.label,
            color = Accent,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enter your M-Pesa PIN",
            style = HomeType.section.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            color = Ink,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Hidden on this screen · never stored · demo only",
            style = HomeType.caption,
            color = Mute,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 22.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (i < pinLength) Ink else Hairline)
                )
            }
            if (pinLength > 4) {
                Text("+${pinLength - 4}", style = HomeType.caption, color = Mute)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        securePinKeyRows().forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (label.isEmpty()) Color.Transparent else Canvas)
                            .then(
                                if (label.isEmpty()) Modifier
                                else Modifier.clickable {
                                    haptic()
                                    when (label) {
                                        "⌫" -> onBackspace()
                                        else -> onDigit(label)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = HomeType.greeting,
                                color = Ink
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent.copy(alpha = confirmAlpha),
                contentColor = OnAccent,
                disabledContainerColor = Accent.copy(alpha = 0.38f),
                disabledContentColor = OnAccent.copy(alpha = 0.7f)
            )
        ) {
            Text("Confirm", style = HomeType.rowTitle)
        }
        TextButton(onClick = onCancel) {
            Text("Cancel payment", style = HomeType.label, color = Mute)
        }
    }
}
