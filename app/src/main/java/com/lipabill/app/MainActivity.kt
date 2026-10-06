package com.lipabill.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.graphics.Color as AndroidColor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lipabill.app.auth.AuthUiState
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.sms.hasMpesaSmsPermission
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.data.tickets.PkPassIntents
import com.lipabill.app.ui.analytics.MetricsScreen
import com.lipabill.app.ui.auth.AuthGateScreen
import com.lipabill.app.ui.main.MainShellScreen
import com.lipabill.app.ui.navigation.Route
import com.lipabill.app.ui.permissions.AccessibilityPreferred
import com.lipabill.app.ui.permissions.AccessibilityToggleCoachDialog
import com.lipabill.app.ui.permissions.FirstRunSetupScreen
import com.lipabill.app.ui.permissions.PaymentAccessDialog
import com.lipabill.app.ui.permissions.PaymentAccessNeed
import com.lipabill.app.ui.permissions.PermissionGuideDialog
import com.lipabill.app.ui.permissions.PermissionPromptMemory
import com.lipabill.app.ui.permissions.permissionGranted
import com.lipabill.app.ui.permissions.missingPaymentAccess
import com.lipabill.app.ui.permissions.PermissionLesson
import com.lipabill.app.ui.permissions.SmsPermissionScreen
import com.lipabill.app.ui.permissions.phoneLessonFor
import com.lipabill.app.ui.permissions.SmsRestrictedSettings
import com.lipabill.app.ui.pay.PayMoneyBottomSheetFragment
import com.lipabill.app.ui.receipt.ProcessingReceiptScreen
import com.lipabill.app.ui.region.KenyaOnlyScreen
import com.lipabill.app.ui.repeat.ManualRepeatScreen
import com.lipabill.app.ui.repeat.RepeatConfirmScreen
import com.lipabill.app.ui.send.SendMoneyBottomSheetFragment
import com.lipabill.app.ui.settings.SettingsScreen
import com.lipabill.app.ui.adapt.AdaptiveFrame
import com.lipabill.app.ui.adapt.ProvideWindowWidth
import com.lipabill.app.ui.theme.LipaBillTheme
import com.lipabill.app.ui.tickets.TicketDetailScreen
import com.lipabill.app.ui.tickets.TicketsScreen
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import com.lipabill.app.device.HandheldDeviceGate
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.region.KenyaRegionGate
import com.lipabill.app.ui.device.UnsupportedDeviceScreen
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.PendingPaymentReceipt
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.viewmodel.PayMoneyViewModel
import com.lipabill.app.viewmodel.PayMethod
import com.lipabill.app.viewmodel.RepeatTransactionViewModel
import com.lipabill.app.viewmodel.SendMoneyViewModel
import com.lipabill.app.viewmodel.SettingsViewModel
import com.lipabill.app.viewmodel.TicketDetailViewModel
import com.lipabill.app.viewmodel.TicketsViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.viewModelFactory
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(),
    SendMoneyBottomSheetFragment.Host,
    PayMoneyBottomSheetFragment.Host {

    var sendConfirmHandler: (() -> Unit)? = null
    var payConfirmHandler: (() -> Unit)? = null

    override fun onSendSheetConfirm() {
        sendConfirmHandler?.invoke()
    }

    override fun onPaySheetConfirm() {
        payConfirmHandler?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate so Android 14 and earlier match the edge-to-edge
        // layout Android 15 enforces for targetSdk 35+.
        val night = (application as LipaBillApp).darkMode.value
        val barStyle = if (night) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                scrim = AndroidColor.TRANSPARENT,
                darkScrim = AndroidColor.TRANSPARENT
            )
        }
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )
        super.onCreate(savedInstanceState)
        captureIncomingDocument(intent)
        val app = application as LipaBillApp
        setContent {
            val fontSize by app.fontSizeSp.collectAsStateWithLifecycle()
            val darkMode by app.darkMode.collectAsStateWithLifecycle()
            LipaBillTheme(fontSizeSp = fontSize, darkTheme = darkMode) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .hideKeyboardOnOutsideTap()
                ) {
                    ProvideWindowWidth {
                        LipaBillRoot(activity = this@MainActivity, app = app)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureIncomingDocument(intent)
    }

    /** Local copy of a pass opened from another app. Survives the lock screen. */
    var incomingDocumentUri by mutableStateOf<Uri?>(null)
        private set

    fun clearIncomingDocument() {
        incomingDocumentUri = null
    }

    private fun captureIncomingDocument(intent: Intent?) {
        val passes = PkPassIntents.preparePkPasses(this, intent)
        if (passes.isNotEmpty()) {
            (application as LipaBillApp).importOpenedPasses(passes)
            return
        }
        PkPassIntents.capture(this, intent)?.let { incomingDocumentUri = it }
    }

    companion object {
        const val EXTRA_RETURN_TO_AMOUNT_AFTER_PIN_CANCEL =
            "com.lipabill.app.RETURN_TO_AMOUNT_AFTER_PIN_CANCEL"
        const val EXTRA_OPEN_RECEIPT_ID = "com.lipabill.app.OPEN_RECEIPT_ID"
    }
}

private fun receiptIdOf(intent: Intent?): Long? {
    val id = intent?.getLongExtra(MainActivity.EXTRA_OPEN_RECEIPT_ID, 0L) ?: return null
    return id.takeIf { it > 0L }
}

@Composable
private fun LipaBillRoot(
    activity: AppCompatActivity,
    app: LipaBillApp
) {
    val authState by app.authManager.state.collectAsStateWithLifecycle()
    var authError by remember { mutableStateOf<String?>(null) }
    var regionVerdict by remember {
        mutableStateOf(KenyaRegionGate.evaluate(activity))
    }
    var phoneOrTablet by remember {
        mutableStateOf(HandheldDeviceGate.isPhoneOrTablet(activity))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                phoneOrTablet = HandheldDeviceGate.isPhoneOrTablet(activity)
                regionVerdict = KenyaRegionGate.evaluate(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!phoneOrTablet) {
        AdaptiveFrame(expandedMax = 520.dp) { UnsupportedDeviceScreen() }
        return
    }

    when (val region = regionVerdict) {
        is KenyaRegionGate.Verdict.Blocked -> {
            AdaptiveFrame(expandedMax = 520.dp) {
                KenyaOnlyScreen(
                    detectedIso = region.detectedIso,
                    onTryAgain = { regionVerdict = KenyaRegionGate.evaluate(activity) }
                )
            }
        }
        KenyaRegionGate.Verdict.Allowed -> when (authState) {
            AuthUiState.Unlocked -> {
                AuthenticatedApp(activity = activity, app = app)
            }
            else -> {
                AdaptiveFrame(expandedMax = 520.dp) {
                AuthGateScreen(
                    state = authState,
                    errorMessage = authError,
                    onUnlockClick = {
                        authError = null
                        app.authManager.authenticate(activity) { authError = it }
                    },
                    onContinueWithoutLock = {
                        app.authManager.continueWithoutLockScreen()
                    },
                    onAutoPrompt = {
                        authError = null
                        app.authManager.authenticate(activity) { authError = it }
                    }
                )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedApp(
    activity: AppCompatActivity,
    app: LipaBillApp
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val host = activity as MainActivity
    val pendingPkPassUri = host.incomingDocumentUri
    val openedPass by app.openedPass.collectAsStateWithLifecycle()
    var returnToAmountAfterPinCancel by remember {
        mutableStateOf(
            activity.intent?.getBooleanExtra(
                MainActivity.EXTRA_RETURN_TO_AMOUNT_AFTER_PIN_CANCEL,
                false
            ) == true
        )
    }
    var openReceiptId by remember { mutableStateOf(receiptIdOf(activity.intent)) }
    var instantHomeReturn by remember { mutableStateOf(false) }
    LaunchedEffect(instantHomeReturn) {
        if (instantHomeReturn) instantHomeReturn = false
    }

    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<Intent> { intent ->
            if (intent.getBooleanExtra(
                    MainActivity.EXTRA_RETURN_TO_AMOUNT_AFTER_PIN_CANCEL,
                    false
                )
            ) {
                returnToAmountAfterPinCancel = true
            }
            receiptIdOf(intent)?.let { openReceiptId = it }
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    val openPasses = pendingPkPassUri != null || openedPass !is OpenedPass.Idle
    LaunchedEffect(openPasses) {
        if (openPasses) {
            navController.navigate(Route.Tickets.path) {
                launchSingleTop = true
            }
        }
    }

    fun hasSmsPermission(): Boolean = context.hasMpesaSmsPermission()

    fun hasCallPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    fun hasPhoneStatePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    var smsGranted by remember { mutableStateOf(hasSmsPermission()) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var pendingDialTxId by remember { mutableStateOf<Long?>(null) }
    var phoneStateRefreshKey by remember { mutableStateOf(0) }
    var showAccessibilityReenable by remember { mutableStateOf(false) }
    var dismissedA11yReenableThisSession by remember { mutableStateOf(false) }
    // Android 15–24 only: sideloaded SMS needs "Allow restricted settings".
    // Android 25+ uses the normal permission / App info route.
    val supportsRestrictedSmsUnlock = SmsRestrictedSettings.appliesToThisDevice()
    val showRestrictedSettingsHelp =
        supportsRestrictedSmsUnlock && permanentlyDenied && !smsGranted

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        smsGranted = granted
        AppMetrics.smsPermission(granted)
        if (!granted) {
            permanentlyDenied =
                supportsRestrictedSmsUnlock ||
                    !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)
        } else {
            permanentlyDenied = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
            val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
            val ok = read == PackageManager.PERMISSION_GRANTED &&
                receive == PackageManager.PERMISSION_GRANTED
            smsGranted = ok
            if (ok) permanentlyDenied = false
            // Some updates leave Accessibility off. Coach only on release/Play builds —
            // debug installs restore via adb (scripts/install-play-debug.sh) so UI work
            // isn't interrupted every push.
            val needsReenable =
                AccessibilityPreferred.syncFromSystem(context, app.securePreferences)
            if (!BuildConfig.DEBUG &&
                !dismissedA11yReenableThisSession &&
                needsReenable
            ) {
                showAccessibilityReenable = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showAccessibilityReenable) {
        AccessibilityToggleCoachDialog(
            currentlyEnabled = false,
            titleOverride = "Turn Accessibility back on",
            bodyOverride = "Android turns LipaBill Accessibility off after each update. " +
                AccessibilityHelper.coachMessage(turningOn = true),
            dismissLabel = "Not now",
            onConfirmOpen = {
                showAccessibilityReenable = false
                dismissedA11yReenableThisSession = true
                AccessibilityPreferred.markUserTurningOn(app.securePreferences)
                AccessibilityHelper.openAppAccessibilityDetails(context)
            },
            onDismiss = {
                showAccessibilityReenable = false
                dismissedA11yReenableThisSession = true
            }
        )
    }

    var paymentAccessPrompt by remember { mutableStateOf(false) }
    var paymentAccessFootnote by remember { mutableStateOf<String?>(null) }
    var pendingPaymentOpen by remember { mutableStateOf<(() -> Unit)?>(null) }
    var paymentAccessRevision by remember { mutableIntStateOf(0) }
    /** Returning from the Accessibility screen stays on home. The next tap can open Send or Pay. */
    var skipOpenAfterAccessibility by remember { mutableStateOf(false) }

    fun dismissPaymentAccess() {
        paymentAccessPrompt = false
        paymentAccessFootnote = null
        pendingPaymentOpen = null
    }

    fun finishPaymentAccessIfReady() {
        if (!paymentAccessPrompt) return
        paymentAccessRevision++
        if (skipOpenAfterAccessibility) {
            skipOpenAfterAccessibility = false
            dismissPaymentAccess()
            return
        }
        if (context.missingPaymentAccess().isNotEmpty()) return
        val open = pendingPaymentOpen
        dismissPaymentAccess()
        open?.invoke()
    }

    fun openPaymentOrExplain(open: () -> Unit) {
        if (context.missingPaymentAccess().isEmpty()) {
            open()
        } else {
            pendingPaymentOpen = open
            paymentAccessPrompt = true
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val callOk = result[Manifest.permission.CALL_PHONE] == true
        val stateOk = result[Manifest.permission.READ_PHONE_STATE] == true ||
            hasPhoneStatePermission()
        phoneStateRefreshKey++
        if (paymentAccessPrompt) {
            finishPaymentAccessIfReady()
            return@rememberLauncherForActivityResult
        }
        val txId = pendingDialTxId
        pendingDialTxId = null
        when {
            callOk && stateOk && txId != null ->
                Toast.makeText(context, "Permissions granted — tap Confirm again", Toast.LENGTH_LONG)
                    .show()
            !callOk ->
                Toast.makeText(context, "Phone permission is required for M-Pesa payments", Toast.LENGTH_LONG)
                    .show()
            else ->
                Toast.makeText(context, "SIM access updated — pick your line, then Confirm", Toast.LENGTH_LONG)
                    .show()
        }
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        phoneStateRefreshKey++
        if (paymentAccessPrompt) finishPaymentAccessIfReady()
    }

    var phoneLesson by remember { mutableStateOf<PermissionLesson?>(null) }
    var phoneLessonAllow by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun explainPhoneAccess(lesson: PermissionLesson, onAllow: () -> Unit) {
        phoneLesson = lesson
        phoneLessonAllow = onAllow
    }

    fun openPaymentAccessibilitySettings() {
        app.securePreferences.accessibilityOnboardingSeen = true
        AccessibilityPreferred.markUserTurningOn(app.securePreferences)
        AccessibilityHelper.openAppAccessibilityDetails(context)
    }

    fun allowNextPaymentAccess() {
        when (context.missingPaymentAccess().firstOrNull()) {
            PaymentAccessNeed.Phone ->
                callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
            PaymentAccessNeed.PhoneState ->
                phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            PaymentAccessNeed.Accessibility -> {
                skipOpenAfterAccessibility = true
                pendingPaymentOpen = null
                openPaymentAccessibilitySettings()
            }
            null -> finishPaymentAccessIfReady()
        }
    }

    val activePhoneLesson = phoneLesson
    if (activePhoneLesson != null) {
        PermissionGuideDialog(
            lesson = activePhoneLesson,
            onAllow = {
                val allow = phoneLessonAllow
                phoneLesson = null
                phoneLessonAllow = null
                allow?.invoke()
            },
            onNotNow = {
                phoneLesson = null
                phoneLessonAllow = null
                pendingDialTxId = null
            }
        )
    }

    if (paymentAccessPrompt) {
        val missing = remember(paymentAccessRevision) { context.missingPaymentAccess() }
        LaunchedEffect(missing) {
            if (missing.isEmpty()) finishPaymentAccessIfReady()
        }
        if (missing.isNotEmpty()) {
            PaymentAccessDialog(
                missing = missing,
                footnote = paymentAccessFootnote,
                onAllow = { allowNextPaymentAccess() },
                onNotNow = { dismissPaymentAccess() }
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) finishPaymentAccessIfReady()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestPhoneStateWithGuide() {
        if (hasPhoneStatePermission()) {
            phoneStateRefreshKey++
            return
        }
        explainPhoneAccess(PermissionLesson.PhoneState) {
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    val listVm: TransactionListViewModel = viewModel(viewModelStoreOwner = activity)
    val sendVm: SendMoneyViewModel = viewModel(viewModelStoreOwner = activity)
    val payVm: PayMoneyViewModel = viewModel(viewModelStoreOwner = activity)

    LaunchedEffect(smsGranted) {
        listVm.setSmsPermission(smsGranted)
    }

    val mainActivity = activity as MainActivity

    fun refreshPaymentContacts() {
        if (!hasContactsPermission()) return
        sendVm.refreshContactsAccess()
        payVm.refreshContactsAccess()
    }

    fun showSendSheet(preselect: SendContact? = null, preserveState: Boolean = false) {
        openPaymentOrExplain {
            if (!preserveState) {
                sendVm.clearSelection()
                sendVm.setAmountInput("")
                if (preselect != null) {
                    sendVm.select(preselect)
                }
            }
            refreshPaymentContacts()
            val existing = activity.supportFragmentManager.findFragmentByTag(SendMoneyBottomSheetFragment.TAG)
            if (existing == null) {
                SendMoneyBottomSheetFragment.newInstance()
                    .show(activity.supportFragmentManager, SendMoneyBottomSheetFragment.TAG)
            }
        }
    }

    fun showPaySheet(preserveState: Boolean = false) {
        openPaymentOrExplain {
            if (!preserveState) {
                payVm.clearMethod()
                payVm.selectMethod(PayMethod.PAYBILL)
            }
            refreshPaymentContacts()
            val existing = activity.supportFragmentManager.findFragmentByTag(PayMoneyBottomSheetFragment.TAG)
            if (existing == null) {
                PayMoneyBottomSheetFragment.newInstance()
                    .show(activity.supportFragmentManager, PayMoneyBottomSheetFragment.TAG)
            }
        }
    }

    fun toastUnfundedPayment() {
        Toast.makeText(
            context,
            listVm.uiState.value.unfundedPaymentMessage(),
            Toast.LENGTH_LONG
        ).show()
    }

    /** Send/Pay from Home. Permission opens the access prompt; a missing balance only explains. */
    fun attemptPayment(open: () -> Unit) {
        val state = listVm.uiState.value
        val funded = state.canFundPayment()
        if (context.missingPaymentAccess().isNotEmpty()) {
            paymentAccessFootnote = if (funded) null else state.unfundedPaymentMessage()
            pendingPaymentOpen = {
                val now = listVm.uiState.value
                if (now.canFundPayment()) open() else toastUnfundedPayment()
            }
            paymentAccessPrompt = true
            return
        }
        if (!funded) {
            toastUnfundedPayment()
            return
        }
        open()
    }

    val startDestination = if (!app.securePreferences.firstRunSetupDone) {
        Route.FirstRunSetup.path
    } else {
        Route.List.path
    }

    fun navigateHome() {
        navController.navigate(Route.List.path) {
            popUpTo(Route.List.path) { inclusive = false }
            launchSingleTop = true
        }
    }

    LaunchedEffect(openReceiptId) {
        val id = openReceiptId ?: return@LaunchedEffect
        if (id <= 0L || !app.securePreferences.firstRunSetupDone) return@LaunchedEffect
        navigateHome()
    }

    fun navigateRepeat(txId: Long, amount: String = "") {
        if (!app.repeatCoordinator.isFeatureEnabled()) {
            navController.navigate(Route.RepeatManual(txId, amount).path)
        } else {
            navController.navigate(Route.RepeatConfirm(txId, amount).path)
        }
    }

    fun restoreAmountAfterPinCancel() {
        val draft = PendingPaymentReceipt.takeRestoreDraft() ?: return
        PendingPaymentReceipt.clear()
        activity.intent?.removeExtra(MainActivity.EXTRA_RETURN_TO_AMOUNT_AFTER_PIN_CANCEL)
        instantHomeReturn = true
        navigateHome()
        when (draft.flow) {
            PendingPaymentReceipt.Flow.SEND -> {
                val phone = draft.counterpartyPhone ?: return
                sendVm.restoreAmountEntry(
                    phone = phone,
                    name = draft.counterpartyName,
                    amountInput = draft.amountInput()
                )
                showSendSheet(preserveState = true)
            }
            PendingPaymentReceipt.Flow.PAY -> {
                val method = when (draft.type) {
                    TransactionType.PAYBILL -> PayMethod.PAYBILL
                    TransactionType.BUY_GOODS -> PayMethod.TILL
                    TransactionType.POCHI -> PayMethod.POCHI
                    else -> return
                }
                payVm.restoreAmountEntry(
                    method = method,
                    amountInput = draft.amountInput(),
                    businessNumber = draft.counterpartyPhone.orEmpty()
                        .takeIf { method == PayMethod.PAYBILL }
                        .orEmpty(),
                    accountNumber = draft.accountHint.orEmpty(),
                    tillNumber = draft.counterpartyPhone.orEmpty()
                        .takeIf { method == PayMethod.TILL }
                        .orEmpty(),
                    pochiPhone = draft.counterpartyPhone
                        .takeIf { method == PayMethod.POCHI },
                    pochiName = draft.counterpartyName
                        .takeIf { method == PayMethod.POCHI }
                )
                showPaySheet(preserveState = true)
            }
            PendingPaymentReceipt.Flow.REPEAT -> {
                if (draft.transactionId <= 0L) return
                navigateRepeat(draft.transactionId, draft.amountInput())
            }
        }
    }

    LaunchedEffect(returnToAmountAfterPinCancel) {
        if (!returnToAmountAfterPinCancel) return@LaunchedEffect
        returnToAmountAfterPinCancel = false
        restoreAmountAfterPinCancel()
    }

    fun dismissMoneySheets() {
        listOf(SendMoneyBottomSheetFragment.TAG, PayMoneyBottomSheetFragment.TAG).forEach { tag ->
            (activity.supportFragmentManager.findFragmentByTag(tag) as? BottomSheetDialogFragment)
                ?.dismissAllowingStateLoss()
        }
    }

    fun runStepUpAndDial(
        flow: String,
        prepare: suspend () -> RepeatTransactionCoordinator.PreparedRepeat?,
        startDial: (RepeatTransactionCoordinator.PreparedRepeat) -> Unit,
        pendingId: Long? = null
    ) {
        val needCall = !hasCallPermission()
        val needState = !hasPhoneStatePermission()
        val lesson = phoneLessonFor(needCall, needState)
        if (lesson != null) {
            pendingDialTxId = pendingId
            explainPhoneAccess(lesson) {
                callPermissionLauncher.launch(
                    buildList {
                        if (needCall) add(Manifest.permission.CALL_PHONE)
                        if (needState) add(Manifest.permission.READ_PHONE_STATE)
                    }.toTypedArray()
                )
            }
            return
        }
        // After amount confirm: go straight to dial / M-Pesa PIN — no mid-flow biometrics.
        scope.launch {
            val prepared = prepare()
            if (prepared == null) {
                AppMetrics.paymentAborted(flow, "prepare_failed")
                Toast.makeText(
                    context,
                    "Couldn’t start payment — check amount and recipient",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            AppMetrics.paymentStarted(flow)
            startDial(prepared)
            PendingPaymentReceipt.capture(prepared)
            dismissMoneySheets()
            navController.navigate(Route.Processing(prepared.auditId).path) {
                popUpTo(Route.List.path) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    fun runStepUpAndDial(repeatVm: RepeatTransactionViewModel) {
        runStepUpAndDial(
            flow = "repeat",
            prepare = { repeatVm.prepare() },
            startDial = { repeatVm.startDial(it) },
            pendingId = repeatVm.uiState.value.transaction?.id
        )
    }

    fun runStepUpAndDial(sendVm: SendMoneyViewModel) {
        runStepUpAndDial(
            flow = "send",
            prepare = { sendVm.prepare() },
            startDial = { sendVm.startDial(it) },
            pendingId = null
        )
    }

    fun runStepUpAndDial(payVm: PayMoneyViewModel) {
        runStepUpAndDial(
            flow = "pay",
            prepare = { payVm.prepare() },
            startDial = { payVm.startDial(it) },
            pendingId = null
        )
    }

    SideEffect {
        fun gateConfirm(
            refresh: () -> Unit,
            readState: () -> Triple<Boolean, Boolean, Boolean>,
            needsSimSetup: () -> Boolean,
            dial: () -> Unit
        ) {
            refresh()
            val (featureEnabled, accessibilityEnabled, needsPhoneState) = readState()
            when {
                !featureEnabled ->
                    Toast.makeText(
                        context,
                        "Payment automation is off — enable it in Profile",
                        Toast.LENGTH_LONG
                    ).show()
                !accessibilityEnabled -> openPaymentAccessibilitySettings()
                needsPhoneState -> requestPhoneStateWithGuide()
                needsSimSetup() ->
                    navController.navigate(Route.Settings.path)
                else -> dial()
            }
        }
        mainActivity.sendConfirmHandler = {
            gateConfirm(
                refresh = sendVm::refreshGates,
                readState = {
                    val s = sendVm.uiState.value
                    Triple(s.featureEnabled, s.accessibilityEnabled, s.needsPhoneStatePermission)
                },
                needsSimSetup = {
                    val s = sendVm.uiState.value
                    s.simLines.size > 1 && !s.hasSavedSimPreference
                },
                dial = { runStepUpAndDial(sendVm) }
            )
        }
        mainActivity.payConfirmHandler = {
            gateConfirm(
                refresh = payVm::refreshGates,
                readState = {
                    val s = payVm.uiState.value
                    Triple(s.featureEnabled, s.accessibilityEnabled, s.needsPhoneStatePermission)
                },
                needsSimSetup = {
                    val s = payVm.uiState.value
                    s.simLines.size > 1 && !s.hasSavedSimPreference
                },
                dial = { runStepUpAndDial(payVm) }
            )
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry?.destination?.route) {
        val route = navBackStackEntry?.destination?.route ?: return@LaunchedEffect
        val screen = route.substringBefore('/').substringBefore('?').ifBlank { route }
        AppMetrics.screen(screen)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            if (instantHomeReturn) EnterTransition.None
            else fadeIn(tween(120)) + slideInHorizontally(tween(160)) { it / 12 }
        },
        exitTransition = {
            if (instantHomeReturn) ExitTransition.None else fadeOut(tween(100))
        },
        popEnterTransition = {
            if (instantHomeReturn) EnterTransition.None else fadeIn(tween(120))
        },
        popExitTransition = {
            if (instantHomeReturn) ExitTransition.None
            else fadeOut(tween(100)) + slideOutHorizontally(tween(160)) { it / 12 }
        }
    ) {
        composable(Route.FirstRunSetup.path) {
            AdaptiveFrame { FirstRunSetupScreen(
                onFinished = {
                    app.securePreferences.firstRunSetupDone = true
                    AppMetrics.firstRunCompleted()
                    smsGranted = hasSmsPermission()
                    listVm.setSmsPermission(smsGranted)
                    navController.navigate(Route.List.path) {
                        popUpTo(Route.FirstRunSetup.path) { inclusive = true }
                    }
                }
            ) }
        }
        composable(Route.Permission.path) {
            AdaptiveFrame { SmsPermissionScreen(
                permanentlyDenied = permanentlyDenied,
                showRestrictedSettingsHelp = showRestrictedSettingsHelp,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_SMS,
                            Manifest.permission.RECEIVE_SMS
                        )
                    )
                },
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                },
                onContinueWithout = {
                    navController.navigate(Route.List.path) {
                        popUpTo(Route.Permission.path) { inclusive = true }
                    }
                }
            ) }
            LaunchedEffect(smsGranted) {
                if (smsGranted) {
                    navController.navigate(Route.List.path) {
                        popUpTo(Route.Permission.path) { inclusive = true }
                    }
                }
            }
        }
        composable(Route.List.path) {
            var askNotifications by remember { mutableStateOf(false) }
            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (!granted) PermissionPromptMemory.notificationsDeclined = true
            }
            LaunchedEffect(Unit) {
                askNotifications = Build.VERSION.SDK_INT >= 33 &&
                    !context.permissionGranted(Manifest.permission.POST_NOTIFICATIONS) &&
                    !PermissionPromptMemory.notificationsDeclined
            }
            if (askNotifications) {
                PermissionGuideDialog(
                    lesson = PermissionLesson.Notifications,
                    onAllow = {
                        askNotifications = false
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onNotNow = {
                        askNotifications = false
                        PermissionPromptMemory.notificationsDeclined = true
                    }
                )
            }
            MainShellScreen(
                listVm = listVm,
                openReceiptId = openReceiptId,
                onReceiptOpened = {
                    openReceiptId = null
                    activity.intent?.removeExtra(MainActivity.EXTRA_OPEN_RECEIPT_ID)
                },
                onRepeatTransaction = { id -> navigateRepeat(id) },
                onOpenSend = { attemptPayment { showSendSheet() } },
                onOpenSendTo = { contact -> attemptPayment { showSendSheet(preselect = contact) } },
                onOpenPay = { attemptPayment { showPaySheet() } },
                onOpenMetrics = {
                    navController.navigate(Route.Metrics.path)
                },
                onOpenTickets = {
                    navController.navigate(Route.Tickets.path)
                },
                onRequestSms = {
                    if (hasSmsPermission()) {
                        smsGranted = true
                        listVm.setSmsPermission(true)
                    } else {
                        navController.navigate(Route.Permission.path)
                    }
                },
                onOpenSettings = {
                    navController.navigate(Route.Settings.path)
                }
            )
        }
        composable(Route.Metrics.path) {
            BackHandler { navigateHome() }
            AdaptiveFrame {
                MetricsScreen(
                    viewModel = listVm,
                    onBack = { navigateHome() }
                )
            }
        }
        composable(Route.Tickets.path) {
            BackHandler { navigateHome() }
            val ticketsVm: TicketsViewModel = viewModel(
                factory = viewModelFactory { TicketsViewModel(app) }
            )
            TicketsScreen(
                viewModel = ticketsVm,
                onBack = { navigateHome() },
                onOpenTicket = { id ->
                    navController.navigate(Route.TicketDetail(id).path)
                },
                pendingPkPassUri = pendingPkPassUri,
                onPendingPkPassConsumed = { host.clearIncomingDocument() },
                openedPass = openedPass,
                onOpenedPassHandled = { app.clearOpenedPass() }
            )
        }
        composable(
            route = Route.TicketDetail.pattern,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            BackHandler { navController.popBackStack() }
            val detailVm: TicketDetailViewModel = viewModel(
                key = "ticket-$id",
                factory = viewModelFactory { TicketDetailViewModel(app, id) }
            )
            AdaptiveFrame(expandedMax = 840.dp) {
                TicketDetailScreen(
                    viewModel = detailVm,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(
            route = Route.RepeatConfirm.pattern,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("amount") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            val amountArg = entry.arguments?.getString("amount").orEmpty()
            BackHandler { navigateHome() }
            val repeatVm: RepeatTransactionViewModel = viewModel(
                key = "repeat-$id-$amountArg",
                factory = viewModelFactory {
                    RepeatTransactionViewModel(app, id, amountArg.ifBlank { null })
                }
            )
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, phoneStateRefreshKey) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) repeatVm.refreshAccessibility()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                repeatVm.refreshAccessibility()
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            AdaptiveFrame { RepeatConfirmScreen(
                viewModel = repeatVm,
                onBack = { navigateHome() },
                onNeedAccessibility = { openPaymentAccessibilitySettings() },
                onManualFallback = {
                    navController.navigate(Route.RepeatManual(id).path)
                },
                onOpenSimSettings = {
                    navController.navigate(Route.Settings.path)
                },
                onRequestPhoneStatePermission = { requestPhoneStateWithGuide() },
                onConfirm = { runStepUpAndDial(repeatVm) }
            ) }
        }
        composable(
            route = Route.RepeatManual.pattern,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("amount") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            val amountArg = entry.arguments?.getString("amount").orEmpty()
            BackHandler { navigateHome() }
            val repeatVm: RepeatTransactionViewModel = viewModel(
                key = "repeat-manual-$id-$amountArg",
                factory = viewModelFactory {
                    RepeatTransactionViewModel(app, id, amountArg.ifBlank { null })
                }
            )
            AdaptiveFrame { ManualRepeatScreen(
                viewModel = repeatVm,
                onBack = { navigateHome() },
                onCopied = {
                    scope.launch { repeatVm.logManualCopy() }
                }
            ) }
        }
        composable(Route.Settings.path) {
            BackHandler { navigateHome() }
            val settingsVm: SettingsViewModel = viewModel()
            AdaptiveFrame {
                SettingsScreen(
                    viewModel = settingsVm,
                    onBack = { navigateHome() },
                    onRequestPhoneStatePermission = { requestPhoneStateWithGuide() }
                )
            }
        }
        composable(
            route = Route.Processing.pattern,
            arguments = listOf(navArgument("auditId") { type = NavType.LongType })
        ) { entry ->
            val auditId = entry.arguments?.getLong("auditId") ?: return@composable
            BackHandler { navigateHome() }
            AdaptiveFrame(expandedMax = 720.dp) {
                ProcessingReceiptScreen(
                    auditId = auditId,
                    onDone = { navigateHome() },
                    onBack = { navigateHome() }
                )
            }
        }
    }
}
