package com.lipabill.app.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.SimLineHelper

private enum class SetupStep {
    /** Safe runtime permissions (no SMS when restricted settings apply). */
    Permissions,
    Sim,
    /** Sideload only: trigger + Allow restricted settings (SMS still denied). */
    RestrictedUnlock,
    /** After unlock (or Play install): Accessibility + SMS. */
    SensitiveAccess,
    Done
}

/**
 * First-install wizard.
 *
 * Sideload / App Distribution (restricted settings):
 *   safe permissions → SIM → unlock restricted settings → Accessibility + SMS → done
 *
 * Play / unrestricted:
 *   all permissions (incl. SMS) → SIM → Accessibility → done
 */
@Composable
fun FirstRunSetupScreen(
    onOpenAppSettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSelectSim: (Int) -> Unit,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val restrictedFlow = remember {
        SideloadRestrictedSettings.needsRestrictedSettingsFlow(context)
    }
    val totalSteps = if (restrictedFlow) 4 else 3

    var step by remember { mutableStateOf(SetupStep.Permissions) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var simLines by remember { mutableStateOf<List<SimLine>>(emptyList()) }
    var selectedSubId by remember { mutableStateOf<Int?>(null) }
    var a11yEnabled by remember {
        mutableStateOf(AccessibilityHelper.isLipaBillServiceEnabled(context))
    }
    var refreshKey by remember { mutableIntStateOf(0) }

    fun refreshSims() {
        simLines = if (SimLineHelper.hasPhoneStatePermission(context)) {
            SimLineHelper.listActiveLines(context)
        } else {
            emptyList()
        }
        selectedSubId = SimLineHelper.findSafaricom(simLines)?.subscriptionId
            ?: selectedSubId
    }

    fun smsGranted(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
        val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
        return read == PackageManager.PERMISSION_GRANTED &&
            receive == PackageManager.PERMISSION_GRANTED
    }

    fun safePermissionsGranted(): Boolean =
        safeRuntimePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun allIncludingSmsGranted(): Boolean =
        fullRuntimePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { !it }.keys
        permanentlyDenied = denied.isNotEmpty() &&
            denied.any { !activityShouldShowRationale(context, it) }
        refreshSims()
        step = SetupStep.Sim
    }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshKey++
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, refreshKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            a11yEnabled = AccessibilityHelper.isLipaBillServiceEnabled(context)
            refreshSims()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun stepLabel(): String = when (step) {
        SetupStep.Permissions -> "Step 1 of $totalSteps · Permissions"
        SetupStep.Sim -> {
            val n = 2
            "Step $n of $totalSteps · SIM"
        }
        SetupStep.RestrictedUnlock -> "Step 3 of $totalSteps · Unlock restricted settings"
        SetupStep.SensitiveAccess -> {
            val n = if (restrictedFlow) 4 else 3
            if (restrictedFlow) {
                "Step $n of $totalSteps · Accessibility & SMS"
            } else {
                "Step $n of $totalSteps · Accessibility"
            }
        }
        SetupStep.Done -> "You’re ready"
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.page, vertical = Space.section),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Set up LipaBill",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(Space.gap))
            Text(
                text = stepLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(Space.section))

            when (step) {
                SetupStep.Permissions -> {
                    Text(
                        text = if (restrictedFlow) {
                            "First allow the everyday permissions. SMS and Accessibility " +
                                "come later — after you unlock restricted settings " +
                                "(they share one App info switch on sideloaded installs)."
                        } else {
                            "LipaBill needs a few permissions up front so Send, Pay, " +
                                "SMS import, and tickets work without interrupting you later. " +
                                "SMS is used only for M-Pesa confirmation receipts on this device."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Space.block))
                    if (!restrictedFlow) {
                        PermissionBullet(
                            "SMS — import M-Pesa confirmations only (encrypted on device)"
                        )
                    }
                    PermissionBullet("Phone — dial *334# on your Safaricom line")
                    PermissionBullet("Phone state — detect Safaricom on multi-SIM")
                    PermissionBullet("Contacts — look up send / pochi recipients")
                    PermissionBullet("Camera — scan boarding passes")
                    if (Build.VERSION.SDK_INT >= 33) {
                        PermissionBullet("Notifications — payment status updates")
                    }
                    if (restrictedFlow) {
                        Spacer(modifier = Modifier.height(Space.gap))
                        Text(
                            text = "Held for later: SMS + LipaBill Repeat Payment (Accessibility)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(Space.section))
                    Button(
                        onClick = {
                            val ready = if (restrictedFlow) {
                                safePermissionsGranted()
                            } else {
                                allIncludingSmsGranted()
                            }
                            if (ready) {
                                refreshSims()
                                step = SetupStep.Sim
                            } else {
                                permissionLauncher.launch(
                                    if (restrictedFlow) {
                                        safeRuntimePermissions()
                                    } else {
                                        fullRuntimePermissions()
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val ready = if (restrictedFlow) {
                            safePermissionsGranted()
                        } else {
                            allIncludingSmsGranted()
                        }
                        Text(if (ready) "Continue" else "Allow permissions")
                    }
                    if (permanentlyDenied) {
                        Spacer(modifier = Modifier.height(Space.gap))
                        OutlinedButton(
                            onClick = onOpenAppSettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open App info")
                        }
                    }
                }

                SetupStep.Sim -> {
                    Text(
                        text = "Choose the Safaricom SIM LipaBill will use for M-Pesa. " +
                            "You can still browse the app without one — Send / Pay stay blocked.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Space.block))
                    when {
                        !SimLineHelper.hasPhoneStatePermission(context) -> {
                            Text(
                                text = "Phone state permission is needed to list SIMs. " +
                                    "Go back and allow permissions, or continue without dialing.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                        simLines.isEmpty() -> {
                            Text(
                                text = "No SIMs detected yet. You can continue and set this in Profile later.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        simLines.none { it.isSafaricom } -> {
                            Text(
                                text = "No Safaricom SIM found. Insert one to unlock Send, Pay, and Repeat.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(Space.gap))
                            simLines.forEach { line ->
                                Text(
                                    text = line.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Space.tight)
                                )
                            }
                        }
                        else -> {
                            simLines.filter { it.isSafaricom }.forEach { line ->
                                val selected = selectedSubId == line.subscriptionId
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = selected,
                                            onClick = {
                                                selectedSubId = line.subscriptionId
                                                onSelectSim(line.subscriptionId)
                                            },
                                            role = Role.RadioButton
                                        )
                                        .padding(vertical = Space.gap)
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            selectedSubId = line.subscriptionId
                                            onSelectSim(line.subscriptionId)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(Space.gap))
                                    Text(line.label, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(Space.section))
                    Button(
                        onClick = {
                            selectedSubId?.let(onSelectSim)
                            step = if (restrictedFlow) {
                                SetupStep.RestrictedUnlock
                            } else {
                                SetupStep.SensitiveAccess
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue")
                    }
                }

                SetupStep.RestrictedUnlock -> {
                    Text(
                        text = "SMS and LipaBill Repeat Payment (Accessibility) are locked " +
                            "behind one App info switch on Firebase App Distribution installs. " +
                            "Do not allow SMS yet — unlock restricted settings first.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Space.section))
                    RestrictedSettingsUnlockSteps()
                    Spacer(modifier = Modifier.height(Space.section))
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("1 · Open LipaBill toggle (trigger)")
                    }
                    Spacer(modifier = Modifier.height(Space.gap))
                    Button(
                        onClick = onOpenAppSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("2 · Open App info → Allow restricted settings")
                    }
                    Spacer(modifier = Modifier.height(Space.gap))
                    Button(
                        onClick = { step = SetupStep.SensitiveAccess },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("I've unlocked — continue to SMS & Accessibility")
                    }
                    Spacer(modifier = Modifier.height(Space.gap))
                    OutlinedButton(
                        onClick = { step = SetupStep.SensitiveAccess },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skip unlock for now")
                    }
                }

                SetupStep.SensitiveAccess -> {
                    val smsOk = smsGranted()
                    Text(
                        text = if (restrictedFlow) {
                            "Restricted settings should be allowed. Now turn on LipaBill " +
                                "Accessibility and allow SMS — both work after that unlock."
                        } else {
                            "Turn on LipaBill Accessibility so Send, Pay, and Repeat can " +
                                "assist M-Pesa USSD after you Confirm a payment. " +
                                "You flip the switch yourself in system Settings."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (!restrictedFlow) {
                        Spacer(modifier = Modifier.height(Space.block))
                        Text(
                            text = AccessibilityDisclosure.WHAT_IT_DOES,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(Space.gap))
                        Text(
                            text = AccessibilityDisclosure.WHAT_IT_DOES_NOT,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(Space.block))
                    Text(
                        text = buildString {
                            append(
                                if (a11yEnabled) {
                                    "Accessibility: on"
                                } else {
                                    "Accessibility: still off"
                                }
                            )
                            if (restrictedFlow) {
                                append(" · ")
                                append(if (smsOk) "SMS: on" else "SMS: not allowed")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (
                            a11yEnabled && (!restrictedFlow || smsOk)
                        ) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        textAlign = TextAlign.Center
                    )
                    if (restrictedFlow) {
                        Spacer(modifier = Modifier.height(Space.section))
                        Text(
                            text = SmsPermissionDisclosure.STANDARD_BODY,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(Space.section))
                        SensitiveAccessSteps()
                        Spacer(modifier = Modifier.height(Space.section))
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open LipaBill toggle")
                        }
                        Spacer(modifier = Modifier.height(Space.gap))
                        Button(
                            onClick = {
                                if (!smsOk) {
                                    smsLauncher.launch(smsPermissions())
                                } else {
                                    onOpenAppSettings()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (smsOk) "Open App info (SMS)" else "Allow SMS")
                        }
                    } else {
                        Spacer(modifier = Modifier.height(Space.section))
                        Button(
                            onClick = onOpenAccessibilitySettings,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open LipaBill toggle")
                        }
                    }
                    Spacer(modifier = Modifier.height(Space.gap))
                    OutlinedButton(
                        onClick = {
                            a11yEnabled = AccessibilityHelper.isLipaBillServiceEnabled(context)
                            refreshKey++
                            step = SetupStep.Done
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val ready = a11yEnabled && (!restrictedFlow || smsOk)
                        Text(if (ready) "Continue" else "I've finished — continue")
                    }
                    Spacer(modifier = Modifier.height(Space.gap))
                    OutlinedButton(
                        onClick = { step = SetupStep.Done },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Skip for now")
                    }
                }

                SetupStep.Done -> {
                    Text(
                        text = "Setup is complete. You can change SIM and permissions anytime in Profile.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(Space.section))
                    Button(
                        onClick = onFinished,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open LipaBill")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionBullet(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.tight),
        horizontalArrangement = Arrangement.Start
    ) {
        Text("•", style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.width(Space.gap))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Permissions that are safe to request before restricted-settings unlock. */
private fun safeRuntimePermissions(): Array<String> = buildList {
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun smsPermissions(): Array<String> = arrayOf(
    Manifest.permission.READ_SMS,
    Manifest.permission.RECEIVE_SMS
)

/** Full set when restricted settings do not apply (e.g. Play Store). */
private fun fullRuntimePermissions(): Array<String> =
    (safeRuntimePermissions().toList() + smsPermissions().toList()).toTypedArray()

private fun activityShouldShowRationale(
    context: android.content.Context,
    permission: String
): Boolean {
    val activity = context as? android.app.Activity ?: return false
    return activity.shouldShowRequestPermissionRationale(permission)
}
