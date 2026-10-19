package com.lipabill.app.ui.sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.LipaBillApp
import com.lipabill.app.ui.permissions.AccessibilityPreferred
import com.lipabill.app.ui.permissions.PaymentAccessNeed
import com.lipabill.app.ui.permissions.rememberAccessibilityEnableRequest
import com.lipabill.app.ui.permissions.rememberPaymentAccess
import com.lipabill.app.ussd.AccessibilityHelper

/**
 * Send and Pay share the same access gate and the balance-visibility preference.
 */
data class MoneySheetAccess(
    val missing: List<PaymentAccessNeed>,
    val block: PaymentAccessNeed?,
    val allow: (PaymentAccessNeed) -> Unit,
    val alwaysShowBalance: Boolean,
    val Dialog: @Composable () -> Unit
)

@Composable
fun rememberMoneySheetAccess(onChanged: () -> Unit): MoneySheetAccess {
    val context = LocalContext.current
    val app = context.applicationContext as LipaBillApp
    val a11yEnable = rememberAccessibilityEnableRequest {
        app.securePreferences.accessibilityOnboardingSeen = true
        AccessibilityPreferred.markUserTurningOn(app.securePreferences)
        AccessibilityHelper.openAppAccessibilityDetails(context)
    }
    val paymentAccess = rememberPaymentAccess(
        onAccessibility = { a11yEnable.request() },
        onChanged = onChanged
    )
    val alwaysShowBalance by app.alwaysShowBalance.collectAsStateWithLifecycle()
    return MoneySheetAccess(
        missing = paymentAccess.missing,
        block = paymentAccess.missing.firstOrNull(),
        allow = paymentAccess.allow,
        alwaysShowBalance = alwaysShowBalance,
        Dialog = a11yEnable.Dialog
    )
}
