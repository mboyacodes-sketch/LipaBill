package com.lipabill.app.ui.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lipabill.app.MainActivity
import com.lipabill.app.ui.permissions.PaymentAccessNeed
import com.lipabill.app.ui.permissions.consentPageFor
import com.lipabill.app.ui.permissions.missingPaymentAccess
import com.lipabill.app.ui.util.findComponentActivity

/**
 * Send and Pay share the same access gate.
 * Missing access opens the consent wizard — not a popup.
 */
data class MoneySheetAccess(
    val missing: List<PaymentAccessNeed>,
    val block: PaymentAccessNeed?,
    val allow: (PaymentAccessNeed) -> Unit,
    val Dialog: @Composable () -> Unit
)

@Composable
fun rememberMoneySheetAccess(onChanged: () -> Unit): MoneySheetAccess {
    val context = LocalContext.current
    val activity = context.findComponentActivity() as? MainActivity
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
    return MoneySheetAccess(
        missing = missing,
        block = missing.firstOrNull(),
        allow = { need ->
            activity?.askConsent(consentPageFor(listOf(need)))
        },
        Dialog = {}
    )
}
