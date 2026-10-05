package com.lipabill.app.ui.permissions

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ussd.AccessibilityHelper

/**
 * Access Send and Pay cannot complete without.
 * Contacts stay optional: a number can still be typed.
 */
enum class PaymentAccessNeed(val line: String, val actionLabel: String) {
    Phone(
        line = "Phone — place the M-Pesa call after you confirm",
        actionLabel = "Allow phone"
    ),
    PhoneState(
        line = "SIM access — see which line is Safaricom. Call history is not read",
        actionLabel = "Allow SIM access"
    ),
    Accessibility(
        line = "Accessibility — fill the M-Pesa screens for that payment",
        actionLabel = "Turn on Accessibility"
    )
}

fun Context.missingPaymentAccess(): List<PaymentAccessNeed> = buildList {
    if (!permissionGranted(Manifest.permission.CALL_PHONE)) add(PaymentAccessNeed.Phone)
    if (!permissionGranted(Manifest.permission.READ_PHONE_STATE)) add(PaymentAccessNeed.PhoneState)
    if (!AccessibilityHelper.isLipaBillServiceEnabled(this@missingPaymentAccess)) {
        add(PaymentAccessNeed.Accessibility)
    }
}

fun paymentAccessDirective(missing: List<PaymentAccessNeed>): String =
    missing.joinToString(separator = " ") { it.line }

class PaymentAccessHandle(
    val missing: List<PaymentAccessNeed>,
    val allow: (PaymentAccessNeed) -> Unit
)

@Composable
fun rememberPaymentAccessNeeds(): List<PaymentAccessNeed> =
    rememberPaymentAccess(onAccessibility = {}).missing

/**
 * Asks for the next missing Send/Pay access. The caller already explained why.
 * Accessibility opens the system toggle; phone and SIM open the system prompt.
 */
@Composable
fun rememberPaymentAccess(
    onAccessibility: () -> Unit,
    onChanged: () -> Unit = {}
): PaymentAccessHandle {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(context.missingPaymentAccess()) }
    fun refresh() {
        missing = context.missingPaymentAccess()
        onChanged()
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val simLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh() }
    val allow: (PaymentAccessNeed) -> Unit = { need ->
        when (need) {
            PaymentAccessNeed.Phone -> phoneLauncher.launch(Manifest.permission.CALL_PHONE)
            PaymentAccessNeed.PhoneState ->
                simLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            PaymentAccessNeed.Accessibility -> onAccessibility()
        }
    }
    return PaymentAccessHandle(missing, allow)
}

@Composable
fun PaymentAccessDialog(
    missing: List<PaymentAccessNeed>,
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    footnote: String? = null
) {
    val next = missing.firstOrNull() ?: return
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text("Send and Pay need access") },
        text = {
            Column {
                Text(
                    "They stay off until you allow what’s listed. " +
                        "Each one is used only for a payment you confirm."
                )
                Spacer(modifier = Modifier.height(Space.gap))
                missing.forEach { need ->
                    Text("• ${need.line}")
                }
                if (!footnote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Space.gap))
                    Text(footnote)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAllow) { Text(next.actionLabel) }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) { Text("Not now") }
        }
    )
}
