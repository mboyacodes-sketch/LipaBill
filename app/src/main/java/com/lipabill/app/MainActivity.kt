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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import android.graphics.Color as AndroidColor
import android.view.WindowManager
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
import com.lipabill.app.data.sms.ReceiptOpenRequest
import com.lipabill.app.data.sms.hasMpesaSmsPermission
import com.lipabill.app.data.sms.receiptOpenRequestFrom
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.data.tickets.PkPassIntents
import com.lipabill.app.ui.analytics.MetricsScreen
import com.lipabill.app.ui.auth.AuthGateScreen
import com.lipabill.app.ui.main.MainShellScreen
import com.lipabill.app.ui.navigation.Route
import com.lipabill.app.update.AppUpdates
import com.lipabill.app.ui.permissions.AccessibilityPreferred
import com.lipabill.app.ui.permissions.FirstRunPage
import com.lipabill.app.ui.permissions.HibernationExemptionPrompt
import com.lipabill.app.ui.permissions.FirstRunSetupScreen
import com.lipabill.app.ui.permissions.consentPageFor
import com.lipabill.app.ui.permissions.missingPaymentAccess
import com.lipabill.app.ui.permissions.onNotificationPermission
import com.lipabill.app.ui.permissions.onNotificationsDismissed
import com.lipabill.app.ui.permissions.toBlocker
import com.lipabill.app.ui.permissions.toConsentRoute
import com.lipabill.app.ui.permissions.RouteConsent
import com.lipabill.app.ui.permissions.SmsRestrictedSettings
import com.lipabill.app.ui.pay.PayMoneyBottomSheetFragment
import com.lipabill.app.ui.receipt.ProcessingReceiptScreen
import com.lipabill.app.ui.demo.DemoPaymentWalkthrough
import com.lipabill.app.ui.repeat.ManualRepeatScreen
import com.lipabill.app.ui.repeat.RepeatConfirmScreen
import com.lipabill.app.ui.send.SendMoneyBottomSheetFragment
import com.lipabill.app.ui.settings.SettingsScreen
import com.lipabill.app.ui.adapt.AdaptiveFrame
import com.lipabill.app.ui.adapt.ProvideWindowWidth
import com.lipabill.app.ui.privacy.RecordingCapture
import com.lipabill.app.ui.theme.LipaBillTheme
import com.lipabill.app.ui.tickets.TicketDetailScreen
import com.lipabill.app.ui.tickets.TicketsScreen
import com.lipabill.app.ui.util.formatKesMoney
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import com.lipabill.app.device.HandheldDeviceGate
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.SetupBlocker
import com.lipabill.app.ui.device.UnsupportedDeviceScreen
import com.lipabill.app.pay.PayLink
import com.lipabill.app.ui.paylink.PayProfileScreen
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.PendingPaymentReceipt
import com.lipabill.app.ussd.RepeatOutcome
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.UssdAccessibilityService
import com.lipabill.app.ussd.UssdAutomationMask
import com.lipabill.app.viewmodel.PayMoneyViewModel
import com.lipabill.app.viewmodel.PayMethod
import com.lipabill.app.viewmodel.RepeatTransactionViewModel
import com.lipabill.app.viewmodel.SendMoneyViewModel
import com.lipabill.app.viewmodel.SettingsViewModel
import com.lipabill.app.viewmodel.TicketDetailViewModel
import com.lipabill.app.viewmodel.TicketsViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.viewModelFactory
import com.lipabill.app.BuildConfig
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(),
    SendMoneyBottomSheetFragment.Host,
    PayMoneyBottomSheetFragment.Host {

    var sendConfirmHandler: (() -> Unit)? = null
    var payConfirmHandler: (() -> Unit)? = null
    var requestConsent: ((FirstRunPage, (() -> Unit)?) -> Unit)? = null

    fun askConsent(page: FirstRunPage, onComplete: (() -> Unit)? = null) {
        requestConsent?.invoke(page, onComplete)
    }

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
        applyCaptureProtection()
        hideOtherAppOverlays()
        acceptReceiptIntent(intent)
        acceptPayIntent(intent)
        captureIncomingDocument(intent)
        val app = application as LipaBillApp
        setContent {
            val fontSize by app.fontSizeSp.collectAsStateWithLifecycle()
            val darkMode by app.darkMode.collectAsStateWithLifecycle()
            val recording by app.recordingPrivacy.collectAsStateWithLifecycle()
            SideEffect { applyCaptureProtection(recording) }
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

    /**
     * FLAG_SECURE is on by default. Debug / internal builds clear it only when
     * the Settings “Screen recording” developer toggle is on (blur + capturable).
     */
    fun applyCaptureProtection(
        recording: Boolean = (application as LipaBillApp).recordingPrivacy.value
    ) {
        if (RecordingCapture.available && recording) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /**
     * Android 12+: other apps' overlays are hidden while LipaBill is on screen, so
     * nothing can draw over amounts, recipients, or confirm buttons. System and
     * accessibility overlays (the USSD mask) are unaffected.
     */
    private fun hideOtherAppOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.setHideOverlayWindows(true)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A new tap must open again, even if this payment was already shown.
        deliveredReceiptKey = null
        deliveredPayToken = null
        acceptReceiptIntent(intent)
        acceptPayIntent(intent)
        captureIncomingDocument(intent)
    }

    override fun onResume() {
        super.onResume()
        // The task can come forward with the notification intent already on the activity.
        acceptReceiptIntent(intent)
        acceptPayIntent(intent)
    }

    /**
     * Receipt a notification asked to open. Held on the activity so the lock screen
     * can come and go without dropping it.
     */
    var pendingReceipt by mutableStateOf<ReceiptOpenRequest?>(null)
        private set

    /** Stops a resume from opening the same receipt again after it has been shown. */
    private var deliveredReceiptKey: String? = null

    /** Pay link that should open Send. Held across the lock screen. */
    var pendingPay by mutableStateOf<PayLink.Target?>(null)
        private set

    private var deliveredPayToken: String? = null

    fun clearPendingPay() {
        pendingPay?.let { deliveredPayToken = it.token }
        pendingPay = null
        val current = intent ?: return
        if (PayLink.parse(current.dataString) != null) {
            current.data = null
        }
    }

    private fun acceptPayIntent(intent: Intent?) {
        val target = PayLink.parse(intent?.dataString) ?: return
        if (target.token == deliveredPayToken) return
        pendingPay = target
    }

    fun clearPendingReceipt() {
        pendingReceipt?.let { deliveredReceiptKey = it.deliveryKey() }
        pendingReceipt = null
        val current = intent ?: return
        current.removeExtra(EXTRA_OPEN_RECEIPT_ID)
        current.removeExtra(EXTRA_OPEN_RECEIPT_CODE)
        if (current.dataString?.startsWith("lipabill://receipt/") == true) {
            current.data = null
        }
        if (current.action == ACTION_OPEN_RECEIPT) {
            current.action = null
        }
    }

    private fun acceptReceiptIntent(intent: Intent?) {
        if (intent == null) return
        val request = receiptOpenRequestFrom(
            extraId = intent.getLongExtra(EXTRA_OPEN_RECEIPT_ID, 0L),
            extraCode = intent.getStringExtra(EXTRA_OPEN_RECEIPT_CODE),
            dataString = intent.dataString
        ) ?: return
        if (request.deliveryKey() == deliveredReceiptKey) return
        pendingReceipt = request
    }

    /** Local copy of a pass opened from another app. Survives the lock screen. */
    var incomingDocumentUri by mutableStateOf<Uri?>(null)
        private set

    /**
     * Shared `.pkpass` URIs held until biometric unlock so import does not
     * write to the encrypted DB while the session is locked.
     */
    var pendingPassImports by mutableStateOf<List<Uri>>(emptyList())
        private set

    fun clearIncomingDocument() {
        incomingDocumentUri = null
    }

    fun consumePendingPassImports(): List<Uri> {
        val out = pendingPassImports
        pendingPassImports = emptyList()
        return out
    }

    private fun captureIncomingDocument(intent: Intent?) {
        val passes = PkPassIntents.preparePkPasses(this, intent)
        if (passes.isNotEmpty()) {
            pendingPassImports = passes
            return
        }
        PkPassIntents.capture(this, intent)?.let { incomingDocumentUri = it }
    }

    companion object {
        const val ACTION_OPEN_RECEIPT = "com.lipabill.app.action.OPEN_RECEIPT"
        const val EXTRA_RETURN_TO_AMOUNT_AFTER_PIN_CANCEL =
            "com.lipabill.app.RETURN_TO_AMOUNT_AFTER_PIN_CANCEL"
        const val EXTRA_OPEN_RECEIPT_ID = "com.lipabill.app.OPEN_RECEIPT_ID"
        const val EXTRA_OPEN_RECEIPT_CODE = "com.lipabill.app.OPEN_RECEIPT_CODE"
        const val EXTRA_OPEN_CHECK_IN = "com.lipabill.app.OPEN_CHECK_IN"
        const val EXTRA_OPEN_TICKET_ID = "com.lipabill.app.OPEN_TICKET_ID"
    }
}

@Composable
private fun LipaBillRoot(
    activity: AppCompatActivity,
    app: LipaBillApp
) {
    val authState by app.authManager.state.collectAsStateWithLifecycle()
    var authError by remember { mutableStateOf<String?>(null) }
    var phoneOrTablet by remember {
        mutableStateOf(HandheldDeviceGate.isPhoneOrTablet(activity))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                phoneOrTablet = HandheldDeviceGate.isPhoneOrTablet(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!phoneOrTablet) {
        AdaptiveFrame(expandedMax = 520.dp) { UnsupportedDeviceScreen() }
        return
    }

    when (authState) {
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
                    onOpenLockSettings = {
                        activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
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

@Composable
private fun AuthenticatedApp(
    activity: AppCompatActivity,
    app: LipaBillApp
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val host = activity as MainActivity
    LaunchedEffect(Unit) {
        AppUpdates.check(activity, manual = false)
    }
    val pendingPassImports = host.pendingPassImports
    LaunchedEffect(pendingPassImports) {
        if (pendingPassImports.isEmpty()) return@LaunchedEffect
        val uris = host.consumePendingPassImports()
        if (uris.isNotEmpty()) app.importOpenedPasses(uris)
    }
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
    val openReceipt = host.pendingReceipt
    var openCheckIn by remember {
        mutableStateOf(activity.intent?.getBooleanExtra(MainActivity.EXTRA_OPEN_CHECK_IN, false) == true)
    }
    var openTicketId by remember {
        mutableStateOf(activity.intent?.getLongExtra(MainActivity.EXTRA_OPEN_TICKET_ID, 0L) ?: 0L)
    }
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
            if (intent.getBooleanExtra(MainActivity.EXTRA_OPEN_CHECK_IN, false)) {
                openCheckIn = true
            }
            val ticketId = intent.getLongExtra(MainActivity.EXTRA_OPEN_TICKET_ID, 0L)
            if (ticketId != 0L) openTicketId = ticketId
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

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotificationsPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

    var smsGranted by remember { mutableStateOf(hasSmsPermission()) }
    var a11yEnabled by remember {
        mutableStateOf(AccessibilityHelper.isLipaBillServiceEnabled(context))
    }
    var extraAccessTick by remember { mutableStateOf(0) }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onNotificationPermission(granted)
        extraAccessTick++
    }
    val hibernationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }
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
            a11yEnabled = AccessibilityHelper.isLipaBillServiceEnabled(context)
            extraAccessTick++
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

    var pendingConsentComplete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var accessibilityNote by remember { mutableStateOf<String?>(null) }

    fun dismissMoneySheets() {
        val fm = activity.supportFragmentManager
        listOf(SendMoneyBottomSheetFragment.TAG, PayMoneyBottomSheetFragment.TAG).forEach { tag ->
            (fm.findFragmentByTag(tag) as? BottomSheetDialogFragment)
                ?.dismissAllowingStateLoss()
        }
    }

    fun openConsent(
        page: FirstRunPage,
        note: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        if (!app.securePreferences.firstRunSetupDone) return
        val fm = activity.supportFragmentManager
        val reopenSend = fm.findFragmentByTag(SendMoneyBottomSheetFragment.TAG) != null
        val reopenPay = fm.findFragmentByTag(PayMoneyBottomSheetFragment.TAG) != null
        dismissMoneySheets()
        pendingConsentComplete = {
            onComplete?.invoke()
            if (reopenSend && fm.findFragmentByTag(SendMoneyBottomSheetFragment.TAG) == null) {
                SendMoneyBottomSheetFragment.newInstance()
                    .show(fm, SendMoneyBottomSheetFragment.TAG)
            }
            if (reopenPay && fm.findFragmentByTag(PayMoneyBottomSheetFragment.TAG) == null) {
                PayMoneyBottomSheetFragment.newInstance()
                    .show(fm, PayMoneyBottomSheetFragment.TAG)
            }
        }
        accessibilityNote = note
        navController.navigate(Route.Consent.of(page.toConsentRoute())) {
            launchSingleTop = true
        }
    }

    host.requestConsent = { page, onComplete -> openConsent(page, onComplete = onComplete) }

    LaunchedEffect(showAccessibilityReenable) {
        if (!showAccessibilityReenable) return@LaunchedEffect
        showAccessibilityReenable = false
        dismissedA11yReenableThisSession = true
        openConsent(
            FirstRunPage.Accessibility,
            note = "Android turns LipaBill’s Accessibility Service off after each update."
        )
    }

    fun openPaymentOrExplain(open: () -> Unit) {
        val missing = context.missingPaymentAccess()
        if (missing.isEmpty()) {
            open()
        } else {
            missing.first().toBlocker()?.let(AppMetrics::setupBlocked)
            openConsent(consentPageFor(missing)) {
                if (context.missingPaymentAccess().isEmpty()) open()
            }
        }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val askedCall = result.containsKey(Manifest.permission.CALL_PHONE)
        val askedState = result.containsKey(Manifest.permission.READ_PHONE_STATE)
        val callOk = result[Manifest.permission.CALL_PHONE] == true
        val stateOk = result[Manifest.permission.READ_PHONE_STATE] == true ||
            hasPhoneStatePermission()
        if (askedCall || askedState) {
            val granted = (!askedCall || callOk) && (!askedState || result[Manifest.permission.READ_PHONE_STATE] == true)
            AppMetrics.phonePermission(granted)
        }
        phoneStateRefreshKey++
        val txId = pendingDialTxId
        pendingDialTxId = null
        when {
            callOk && stateOk && txId != null ->
                Toast.makeText(context, "Permissions granted — tap Confirm again", Toast.LENGTH_LONG)
                    .show()
            !callOk ->
                Toast.makeText(context, "Phone permission is required for Send and Pay", Toast.LENGTH_LONG)
                    .show()
            else ->
                Toast.makeText(context, "SIM access updated — pick your line, then Confirm", Toast.LENGTH_LONG)
                    .show()
        }
    }

    /** Opens system Accessibility settings. Call only after prominent disclosure. */
    fun openPaymentAccessibilitySettings() {
        app.securePreferences.accessibilityOnboardingSeen = true
        AccessibilityPreferred.markUserTurningOn(app.securePreferences)
        AccessibilityHelper.openAppAccessibilityDetails(context)
    }

    fun requestPhoneStateWithGuide() {
        if (hasPhoneStatePermission()) {
            phoneStateRefreshKey++
            return
        }
        openConsent(FirstRunPage.Phone)
    }

    val listVm: TransactionListViewModel = viewModel(viewModelStoreOwner = activity)
    val sendVm: SendMoneyViewModel = viewModel(viewModelStoreOwner = activity)
    val payVm: PayMoneyViewModel = viewModel(viewModelStoreOwner = activity)
    val settingsVm: SettingsViewModel = viewModel(viewModelStoreOwner = activity)

    LaunchedEffect(smsGranted) {
        listVm.setSmsPermission(smsGranted)
    }

    val mainActivity = activity as MainActivity

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        AppMetrics.cameraPermission(granted)
        extraAccessTick++
    }

    fun openHibernationSettings() {
        val intent = runCatching {
            IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)
        }.getOrNull() ?: return
        hibernationSettingsLauncher.launch(intent)
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

    /** Send/Pay from Home. Missing access opens the wizard; a missing balance only explains. */
    fun attemptPayment(open: () -> Unit) {
        val state = listVm.uiState.value
        val funded = state.canFundPayment()
        val missing = context.missingPaymentAccess()
        if (missing.isNotEmpty()) {
            missing.first().toBlocker()?.let(AppMetrics::setupBlocked)
            openConsent(consentPageFor(missing)) {
                val now = listVm.uiState.value
                if (context.missingPaymentAccess().isNotEmpty()) return@openConsent
                if (now.canFundPayment()) open() else toastUnfundedPayment()
            }
            return
        }
        if (!funded) {
            toastUnfundedPayment()
            return
        }
        open()
    }

    fun openIncomingPay(target: PayLink.Target) {
        attemptPayment {
            val amount = target.amountShillings?.toString().orEmpty()
            val fm = activity.supportFragmentManager
            when (target.kind) {
                PayLink.Kind.PHONE -> {
                    listOf(SendMoneyBottomSheetFragment.TAG, PayMoneyBottomSheetFragment.TAG)
                        .forEach { tag ->
                            (fm.findFragmentByTag(tag) as? BottomSheetDialogFragment)
                                ?.dismissAllowingStateLoss()
                        }
                    sendVm.restoreAmountEntry(
                        phone = target.identifier,
                        name = null,
                        amountInput = amount
                    )
                    SendMoneyBottomSheetFragment.newInstance()
                        .show(fm, SendMoneyBottomSheetFragment.TAG)
                }
                PayLink.Kind.TILL -> {
                    listOf(SendMoneyBottomSheetFragment.TAG, PayMoneyBottomSheetFragment.TAG)
                        .forEach { tag ->
                            (fm.findFragmentByTag(tag) as? BottomSheetDialogFragment)
                                ?.dismissAllowingStateLoss()
                        }
                    payVm.restoreAmountEntry(
                        method = PayMethod.TILL,
                        amountInput = amount,
                        tillNumber = target.identifier
                    )
                    showPaySheet(preserveState = true)
                }
                PayLink.Kind.PAYBILL -> {
                    listOf(SendMoneyBottomSheetFragment.TAG, PayMoneyBottomSheetFragment.TAG)
                        .forEach { tag ->
                            (fm.findFragmentByTag(tag) as? BottomSheetDialogFragment)
                                ?.dismissAllowingStateLoss()
                        }
                    payVm.restoreAmountEntry(
                        method = PayMethod.PAYBILL,
                        amountInput = amount,
                        businessNumber = target.identifier,
                        accountNumber = target.account.orEmpty()
                    )
                    showPaySheet(preserveState = true)
                }
            }
        }
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

    /**
     * Debug walkthrough: land the ledger row before Home resumes so a cancelled
     * scroll animation cannot drop the queued reveal. Home still scrolls to it.
     */
    fun navigateHomeAfterDemoReceipt() {
        scope.launch {
            if (BuildConfig.DEBUG) {
                PendingPaymentReceipt.peekDemoHomeReveal()?.let { snap ->
                    runCatching { app.repository.insertDemoPaymentResult(snap) }
                }
            }
            navigateHome()
        }
    }

    LaunchedEffect(openCheckIn) {
        if (!openCheckIn || !app.securePreferences.firstRunSetupDone) return@LaunchedEffect
        navController.navigate(Route.Habits.path) { launchSingleTop = true }
    }

    LaunchedEffect(openTicketId) {
        val id = openTicketId
        if (id == 0L || !app.securePreferences.firstRunSetupDone) return@LaunchedEffect
        navController.navigate(Route.TicketDetail(id).path) { launchSingleTop = true }
        openTicketId = 0L
        activity.intent?.removeExtra(MainActivity.EXTRA_OPEN_TICKET_ID)
    }

    fun navigateRepeat(txId: Long, amount: String = "") {
        if (!app.repeatCoordinator.isFeatureEnabled()) {
            navController.navigate(Route.RepeatManual(txId, amount).path)
            return
        }
        openPaymentOrExplain {
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

    LaunchedEffect(openReceipt) {
        if (openReceipt == null || !app.securePreferences.firstRunSetupDone) return@LaunchedEffect
        dismissMoneySheets()
        // Already on Home: navigating again recreates the list and drops the receipt.
        if (navController.currentDestination?.route == Route.List.path) return@LaunchedEffect
        val popped = navController.popBackStack(Route.List.path, inclusive = false)
        if (!popped) {
            navController.navigate(Route.List.path) { launchSingleTop = true }
        }
    }

    fun runStepUpAndDial(
        flow: String,
        prepare: suspend () -> RepeatTransactionCoordinator.PreparedRepeat?,
        startDial: (RepeatTransactionCoordinator.PreparedRepeat) -> Boolean,
        pendingId: Long? = null
    ) {
        val needCall = !hasCallPermission()
        val needState = !hasPhoneStatePermission()
        if (needCall || needState) {
            pendingDialTxId = pendingId
            openConsent(FirstRunPage.Phone) {
                if (hasCallPermission() && hasPhoneStatePermission()) {
                    runStepUpAndDial(flow, prepare, startDial, pendingId)
                }
            }
            return
        }
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
            if (!startDial(prepared)) {
                Toast.makeText(
                    context,
                    "Payment didn’t start. Turn off other accessibility services, then try again.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            PendingPaymentReceipt.capture(prepared)
            dismissMoneySheets()
            navController.navigate(Route.Processing(prepared.auditId).path) {
                popUpTo(Route.List.path) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    suspend fun finishDemoWalkthrough(auditId: Long, cancelled: Boolean) {
        if (cancelled) {
            app.repeatRepository.finish(
                auditId,
                RepeatOutcome.USER_CANCELLED,
                "demo_cancelled"
            )
            PendingPaymentReceipt.clear()
            navigateHome()
            return
        }
        val snap = PendingPaymentReceipt.peek(auditId)
        if (snap != null) {
            PendingPaymentReceipt.queueDemoHomeReveal(snap)
        }
        app.repeatRepository.finish(
            auditId,
            RepeatOutcome.COMPLETED_TO_PIN,
            "demo_walkthrough"
        )
        navController.navigate(Route.Processing(auditId).path) {
            popUpTo(Route.List.path) { inclusive = false }
            launchSingleTop = true
        }
    }

    /**
     * Debug / Maestro only. Prefers the real USSD mask (progress → Yes/No → PIN)
     * when Accessibility is on; otherwise the Compose fallback. Never dials *334#
     * and never stores PIN digits.
     */
    fun runDemoPaymentWalkthrough(
        flow: String,
        prepare: suspend () -> RepeatTransactionCoordinator.PreparedRepeat?,
        summary: () -> Triple<String, String, String>?
    ) {
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
            PendingPaymentReceipt.capture(prepared)
            dismissMoneySheets()
            val labels = summary()
            val amountLabel = labels?.third ?: formatKesMoney(prepared.amount)
            val detail = labels?.let { "${it.first} · ${it.second}" }
                ?: listOfNotNull(
                    prepared.transaction.counterpartyName,
                    prepared.transaction.counterpartyPhone
                ).joinToString(" · ").ifBlank { "Demo payment" }
            val auditId = prepared.auditId
            val maskSummary = "$amountLabel\n$detail\nDemo walkthrough — no USSD dial"
            val usedMask = UssdAutomationMask.startDebugWalkthrough(
                totalSteps = prepared.plan.actions.size.coerceAtLeast(1),
                summary = maskSummary,
                onComplete = {
                    scope.launch { finishDemoWalkthrough(auditId, cancelled = false) }
                },
                onCancel = {
                    scope.launch { finishDemoWalkthrough(auditId, cancelled = true) }
                }
            )
            if (!usedMask) {
                navController.navigate(
                    Route.DemoPayment(
                        auditId = auditId,
                        amountLabel = amountLabel,
                        detail = detail
                    ).path
                ) {
                    popUpTo(Route.List.path) { inclusive = false }
                    launchSingleTop = true
                }
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
        if (BuildConfig.DEBUG) {
            runDemoPaymentWalkthrough(
                flow = "send",
                prepare = { sendVm.prepare() },
                summary = { sendVm.confirmSummary() }
            )
            return
        }
        runStepUpAndDial(
            flow = "send",
            prepare = { sendVm.prepare() },
            startDial = { sendVm.startDial(it) },
            pendingId = null
        )
    }

    fun runStepUpAndDial(payVm: PayMoneyViewModel) {
        if (BuildConfig.DEBUG) {
            runDemoPaymentWalkthrough(
                flow = "pay",
                prepare = { payVm.prepare() },
                summary = { payVm.confirmSummary() }
            )
            return
        }
        runStepUpAndDial(
            flow = "pay",
            prepare = { payVm.prepare() },
            startDial = { payVm.startDial(it) },
            pendingId = null
        )
    }

    fun runFulizaOptIn() {
        val missing = context.missingPaymentAccess()
        if (missing.isNotEmpty()) {
            missing.first().toBlocker()?.let(AppMetrics::setupBlocked)
            openConsent(consentPageFor(missing)) {
                if (context.missingPaymentAccess().isEmpty()) runFulizaOptIn()
            }
            return
        }
        settingsVm.refresh()
        val state = settingsVm.uiState.value
        when {
            !state.lipaBillA11yEnabled -> openConsent(FirstRunPage.Accessibility) {
                if (context.missingPaymentAccess().isEmpty()) runFulizaOptIn()
            }
            state.simLines.size > 1 && state.preferredSimSubscriptionId == null ->
                Toast.makeText(
                    context,
                    "Choose the payment SIM in Settings first",
                    Toast.LENGTH_LONG
                ).show()
            else -> scope.launch {
                val prepared = settingsVm.prepareFulizaOptIn()
                AppMetrics.paymentStarted("fuliza_opt_in")
                if (!settingsVm.startFulizaOptIn(prepared)) {
                    Toast.makeText(
                        context,
                        "Payment didn’t start. Turn off other accessibility services, then try again.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    SideEffect {
        fun gateConfirm(
            refresh: () -> Unit,
            readState: () -> Pair<Boolean, Boolean>,
            needsSimSetup: () -> Boolean,
            dial: () -> Unit
        ) {
            refresh()
            if (UssdAccessibilityService.foreignServicesEnabled(context)) {
                Toast.makeText(
                    context,
                    context.getString(R.string.pay_blocked_other_accessibility),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            val (accessibilityEnabled, needsPhoneState) = readState()
            when {
                !accessibilityEnabled -> {
                    AppMetrics.setupBlocked(SetupBlocker.NoAccessibility)
                    openConsent(FirstRunPage.Accessibility)
                }
                needsPhoneState -> {
                    AppMetrics.setupBlocked(SetupBlocker.NoSim)
                    requestPhoneStateWithGuide()
                }
                needsSimSetup() -> {
                    AppMetrics.setupBlocked(SetupBlocker.NoSim)
                    navController.navigate(Route.Settings.path)
                }
                else -> dial()
            }
        }
        mainActivity.sendConfirmHandler = {
            if (BuildConfig.DEBUG) {
                runStepUpAndDial(sendVm)
            } else {
                gateConfirm(
                    refresh = sendVm::refreshGates,
                    readState = {
                        val s = sendVm.uiState.value
                        s.accessibilityEnabled to s.needsPhoneStatePermission
                    },
                    needsSimSetup = {
                        val s = sendVm.uiState.value
                        s.simLines.size > 1 && !s.hasSavedSimPreference
                    },
                    dial = { runStepUpAndDial(sendVm) }
                )
            }
        }
        mainActivity.payConfirmHandler = {
            if (BuildConfig.DEBUG) {
                runStepUpAndDial(payVm)
            } else {
                gateConfirm(
                    refresh = payVm::refreshGates,
                    readState = {
                        val s = payVm.uiState.value
                        s.accessibilityEnabled to s.needsPhoneStatePermission
                    },
                    needsSimSetup = {
                        val s = payVm.uiState.value
                        s.simLines.size > 1 && !s.hasSavedSimPreference
                    },
                    dial = { runStepUpAndDial(payVm) }
                )
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val pendingPay = host.pendingPay
    var confirmIncomingPay by remember { mutableStateOf<PayLink.Target?>(null) }
    LaunchedEffect(pendingPay, navBackStackEntry?.destination?.route) {
        val target = pendingPay ?: return@LaunchedEffect
        if (!app.securePreferences.firstRunSetupDone) return@LaunchedEffect
        if (navBackStackEntry?.destination?.route == Route.FirstRunSetup.path) return@LaunchedEffect
        host.clearPendingPay()
        if (navController.currentDestination?.route != Route.List.path) {
            val popped = navController.popBackStack(Route.List.path, inclusive = false)
            if (!popped) {
                navController.navigate(Route.List.path) { launchSingleTop = true }
            }
        }
        confirmIncomingPay = target
    }
    confirmIncomingPay?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmIncomingPay = null },
            title = { Text(stringResource(R.string.pay_link_confirm_title)) },
            text = { Text(incomingPayConfirmMessage(target)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmIncomingPay = null
                        openIncomingPay(target)
                    }
                ) {
                    Text(stringResource(R.string.pay_link_confirm_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmIncomingPay = null }) {
                    Text(stringResource(R.string.pay_link_confirm_cancel))
                }
            }
        )
    }
    LaunchedEffect(navBackStackEntry?.destination?.route) {
        val route = navBackStackEntry?.destination?.route ?: return@LaunchedEffect
        val screen = route.substringBefore('/').substringBefore('?').ifBlank { route }
        AppMetrics.screen(screen)
    }

    @Composable
    fun BoundWizard(
        firstRun: Boolean,
        startPage: FirstRunPage = FirstRunPage.Welcome,
        note: String? = null,
        onFinished: () -> Unit
    ) {
        extraAccessTick
        phoneStateRefreshKey
        val region by app.appRegion.collectAsStateWithLifecycle()
        FirstRunSetupScreen(
            onFinished = onFinished,
            smsGranted = smsGranted,
            permanentlyDenied = permanentlyDenied,
            showRestrictedSettingsHelp = showRestrictedSettingsHelp,
            onRequestSms = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS
                    )
                )
            },
            onOpenSmsSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            },
            phoneGranted = hasCallPermission(),
            simGranted = hasPhoneStatePermission(),
            onRequestPhone = {
                val need = buildList {
                    if (!hasCallPermission()) add(Manifest.permission.CALL_PHONE)
                    if (!hasPhoneStatePermission()) add(Manifest.permission.READ_PHONE_STATE)
                }
                if (need.isNotEmpty()) {
                    callPermissionLauncher.launch(need.toTypedArray())
                }
            },
            accessibilityEnabled = a11yEnabled,
            onAgreeAccessibility = { openPaymentAccessibilitySettings() },
            cameraGranted = hasCameraPermission(),
            notificationsGranted = hasNotificationsPermission(),
            onRequestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            onRequestNotifications = {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onSkipNotifications = { onNotificationsDismissed() },
            onSkipHibernation = { app.securePreferences.hibernationPromptShown = true },
            onOpenHibernationSettings = { openHibernationSettings() },
            startPage = startPage,
            firstRun = firstRun,
            accessibilityNote = note,
            region = region,
            onRegionSelected = { app.setAppRegion(it, rescan = false) },
            onProvidersSelected = { app.setEnabledSources(it, rescan = false) }
        )
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
            AdaptiveFrame {
                BoundWizard(
                    firstRun = true,
                    onFinished = {
                        app.securePreferences.firstRunSetupDone = true
                        AppMetrics.firstRunCompleted()
                        smsGranted = hasSmsPermission()
                        listVm.setSmsPermission(smsGranted)
                        app.ensureDemoLedger()
                        navController.navigate(Route.List.path) {
                            popUpTo(Route.FirstRunSetup.path) { inclusive = true }
                        }
                    }
                )
            }
        }
        composable(
            route = Route.Consent.pattern,
            arguments = listOf(
                navArgument("page") { type = NavType.StringType }
            )
        ) { entry ->
            val startPage = RouteConsent.pageOf(entry.arguments?.getString("page"))
            AdaptiveFrame {
                BoundWizard(
                    firstRun = false,
                    startPage = startPage,
                    note = accessibilityNote,
                    onFinished = {
                        accessibilityNote = null
                        val done = pendingConsentComplete
                        pendingConsentComplete = null
                        navController.popBackStack()
                        done?.invoke()
                    }
                )
            }
        }
        composable(Route.List.path) {
            MainShellScreen(
                listVm = listVm,
                openReceipt = openReceipt,
                onReceiptOpened = { host.clearPendingReceipt() },
                onOpenHabits = {
                    navController.navigate(Route.Habits.path) { launchSingleTop = true }
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
                        openConsent(FirstRunPage.Sms)
                    }
                },
                onOpenSettings = {
                    navController.navigate(Route.Settings.path)
                },
                onOpenProfile = {
                    navController.navigate(Route.Profile.path)
                }
            )
        }
        composable(Route.Profile.path) {
            BackHandler { navController.popBackStack() }
            AdaptiveFrame {
                PayProfileScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Route.Habits.path) {
            BackHandler { navigateHome() }
            AdaptiveFrame {
                com.lipabill.app.ui.engage.HabitsScreen(
                    startQuiz = openCheckIn,
                    onQuizOpened = {
                        openCheckIn = false
                        activity.intent?.removeExtra(MainActivity.EXTRA_OPEN_CHECK_IN)
                    },
                    onBack = { navigateHome() }
                )
            }
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
                onNeedAccessibility = { openConsent(FirstRunPage.Accessibility) },
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
            AdaptiveFrame {
                SettingsScreen(
                    viewModel = settingsVm,
                    onBack = { navigateHome() },
                    onRequestPhoneStatePermission = { requestPhoneStateWithGuide() },
                    onRequestAccessibilityConsent = { openConsent(FirstRunPage.Accessibility) },
                    onOptInFuliza = { runFulizaOptIn() },
                    onCheckForUpdate = { AppUpdates.check(activity, manual = true) }
                )
            }
        }
        composable(
            route = Route.Processing.pattern,
            arguments = listOf(navArgument("auditId") { type = NavType.LongType })
        ) { entry ->
            val auditId = entry.arguments?.getLong("auditId") ?: return@composable
            BackHandler { navigateHomeAfterDemoReceipt() }
            AdaptiveFrame(expandedMax = 720.dp) {
                ProcessingReceiptScreen(
                    auditId = auditId,
                    onDone = { navigateHomeAfterDemoReceipt() },
                    onBack = { navigateHomeAfterDemoReceipt() }
                )
            }
        }
        if (BuildConfig.DEBUG) {
            composable(
                route = Route.DemoPayment.pattern,
                arguments = listOf(
                    navArgument("auditId") { type = NavType.LongType },
                    navArgument("amount") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("detail") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { entry ->
                val auditId = entry.arguments?.getLong("auditId") ?: return@composable
                val amountLabel = entry.arguments?.getString("amount").orEmpty()
                    .ifBlank { "KES" }
                val detail = entry.arguments?.getString("detail").orEmpty()
                    .ifBlank { "Demo payment" }
                BackHandler {
                    PendingPaymentReceipt.clear()
                    navigateHome()
                }
                DemoPaymentWalkthrough(
                    amountLabel = amountLabel,
                    detail = detail,
                    onCancel = {
                        scope.launch { finishDemoWalkthrough(auditId, cancelled = true) }
                    },
                    onComplete = {
                        scope.launch { finishDemoWalkthrough(auditId, cancelled = false) }
                    }
                )
            }
        }
    }

    val onHome = navBackStackEntry?.destination?.route == Route.List.path
    HibernationExemptionPrompt(
        enabled = onHome &&
            !showAccessibilityReenable &&
            navBackStackEntry?.destination?.route != Route.Consent.pattern,
        onNeedConsent = { openConsent(FirstRunPage.Hibernation) }
    )
}

@Composable
private fun incomingPayConfirmMessage(target: PayLink.Target): String {
    val destination = when (target.kind) {
        PayLink.Kind.PHONE -> target.identifier
        PayLink.Kind.TILL -> "till ${target.identifier}"
        PayLink.Kind.PAYBILL -> buildString {
            append("paybill ${target.identifier}")
            val account = target.account
            if (!account.isNullOrBlank()) append(" / $account")
        }
    }
    val amount = target.amountShillings
    return if (amount != null) {
        stringResource(
            R.string.pay_link_confirm_body_amount,
            formatKesMoney(amount.toDouble()),
            destination
        )
    } else {
        stringResource(R.string.pay_link_confirm_body, destination)
    }
}
