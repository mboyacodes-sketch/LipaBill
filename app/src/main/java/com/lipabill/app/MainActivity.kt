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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
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
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.data.tickets.PkPassIntents
import com.lipabill.app.security.AppSecurity
import com.lipabill.app.ui.analytics.MetricsScreen
import com.lipabill.app.ui.auth.AuthGateScreen
import com.lipabill.app.ui.detail.TransactionDetailScreen
import com.lipabill.app.ui.main.MainShellScreen
import com.lipabill.app.ui.navigation.Route
import com.lipabill.app.ui.permissions.FirstRunSetupScreen
import com.lipabill.app.ui.permissions.SideloadRestrictedSettings
import com.lipabill.app.ui.permissions.SmsPermissionScreen
import com.lipabill.app.ui.permissions.SmsRestrictedSettings
import com.lipabill.app.ui.pay.PayMoneyBottomSheetFragment
import com.lipabill.app.ui.receipt.ProcessingReceiptScreen
import com.lipabill.app.ui.repeat.AccessibilityOnboardingScreen
import com.lipabill.app.ui.repeat.ManualRepeatScreen
import com.lipabill.app.ui.repeat.RepeatConfirmScreen
import com.lipabill.app.ui.send.SendMoneyBottomSheetFragment
import com.lipabill.app.ui.settings.SettingsScreen
import com.lipabill.app.ui.theme.LipaBillTheme
import com.lipabill.app.ui.tickets.TicketDetailScreen
import com.lipabill.app.ui.tickets.TicketsScreen
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import com.lipabill.app.metrics.AppMetrics
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
import com.lipabill.app.viewmodel.TransactionDetailViewModel
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
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSecurity.lockScreenCapture(this)
        val app = application as LipaBillApp
        setContent {
            val fontSize by app.fontSizeSp.collectAsStateWithLifecycle()
            LipaBillTheme(fontSizeSp = fontSize) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .hideKeyboardOnOutsideTap()
                ) {
                    LipaBillRoot(activity = this, app = app)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        const val EXTRA_RETURN_TO_AMOUNT_AFTER_PIN_CANCEL =
            "com.lipabill.app.RETURN_TO_AMOUNT_AFTER_PIN_CANCEL"
    }
}

@Composable
private fun LipaBillRoot(
    activity: AppCompatActivity,
    app: LipaBillApp
) {
    val authState by app.authManager.state.collectAsStateWithLifecycle()
    var authError by remember { mutableStateOf<String?>(null) }

    when (authState) {
        AuthUiState.Unlocked -> {
            AuthenticatedApp(activity = activity, app = app)
        }
        else -> {
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

@Composable
private fun AuthenticatedApp(
    activity: AppCompatActivity,
    app: LipaBillApp
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    var pendingPkPassUri by remember {
        mutableStateOf(PkPassIntents.extractUri(activity.intent))
    }
    var returnToAmountAfterPinCancel by remember {
        mutableStateOf(
            activity.intent?.getBooleanExtra(
                MainActivity.EXTRA_RETURN_TO_AMOUNT_AFTER_PIN_CANCEL,
                false
            ) == true
        )
    }

    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<Intent> { intent ->
            PkPassIntents.extractUri(intent)?.let { pendingPkPassUri = it }
            if (intent.getBooleanExtra(
                    MainActivity.EXTRA_RETURN_TO_AMOUNT_AFTER_PIN_CANCEL,
                    false
                )
            ) {
                returnToAmountAfterPinCancel = true
            }
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    LaunchedEffect(pendingPkPassUri) {
        if (pendingPkPassUri != null) {
            navController.navigate(Route.Tickets.path) {
                launchSingleTop = true
            }
        }
    }

    fun hasSmsPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
        val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
        return read == PackageManager.PERMISSION_GRANTED &&
            receive == PackageManager.PERMISSION_GRANTED
    }

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
    var skipPermission by remember { mutableStateOf(false) }
    var pendingDialTxId by remember { mutableStateOf<Long?>(null) }
    var phoneStateRefreshKey by remember { mutableStateOf(0) }
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
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val callOk = result[Manifest.permission.CALL_PHONE] == true
        val stateOk = result[Manifest.permission.READ_PHONE_STATE] == true ||
            hasPhoneStatePermission()
        phoneStateRefreshKey++
        val txId = pendingDialTxId
        pendingDialTxId = null
        when {
            callOk && stateOk && txId != null ->
                Toast.makeText(context, "Permissions granted — tap Confirm again", Toast.LENGTH_LONG)
                    .show()
            !callOk ->
                Toast.makeText(context, "Phone permission is required to dial *334#", Toast.LENGTH_LONG)
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
    }

    val listVm: TransactionListViewModel = viewModel(viewModelStoreOwner = activity)
    val sendVm: SendMoneyViewModel = viewModel(viewModelStoreOwner = activity)
    val payVm: PayMoneyViewModel = viewModel(viewModelStoreOwner = activity)

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        sendVm.refreshContactsAccess()
        payVm.refreshContactsAccess()
    }

    LaunchedEffect(smsGranted) {
        listVm.setSmsPermission(smsGranted)
    }

    val mainActivity = activity as MainActivity

    fun showSendSheet(preselect: SendContact? = null, preserveState: Boolean = false) {
        if (!preserveState) {
            sendVm.clearSelection()
            sendVm.setAmountInput("")
            if (preselect != null) {
                sendVm.select(preselect)
            }
        }
        // Contacts were requested in first-run setup; only re-prompt if still missing.
        if (!hasContactsPermission()) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else {
            sendVm.refreshContactsAccess()
            payVm.refreshContactsAccess()
        }
        val existing = activity.supportFragmentManager.findFragmentByTag(SendMoneyBottomSheetFragment.TAG)
        if (existing == null) {
            SendMoneyBottomSheetFragment.newInstance()
                .show(activity.supportFragmentManager, SendMoneyBottomSheetFragment.TAG)
        }
    }

    fun showPaySheet(preserveState: Boolean = false) {
        if (!preserveState) {
            payVm.clearMethod()
            payVm.selectMethod(PayMethod.PAYBILL)
        }
        if (!hasContactsPermission()) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else {
            sendVm.refreshContactsAccess()
            payVm.refreshContactsAccess()
        }
        val existing = activity.supportFragmentManager.findFragmentByTag(PayMoneyBottomSheetFragment.TAG)
        if (existing == null) {
            PayMoneyBottomSheetFragment.newInstance()
                .show(activity.supportFragmentManager, PayMoneyBottomSheetFragment.TAG)
        }
    }

    val startDestination = when {
        !app.securePreferences.firstRunSetupDone -> Route.FirstRunSetup.path
        smsGranted || skipPermission -> Route.List.path
        else -> Route.Permission.path
    }

    fun navigateHome() {
        navController.navigate(Route.List.path) {
            popUpTo(Route.List.path) { inclusive = false }
            launchSingleTop = true
        }
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
        if (needCall || needState) {
            pendingDialTxId = pendingId
            callPermissionLauncher.launch(
                buildList {
                    if (needCall) add(Manifest.permission.CALL_PHONE)
                    if (needState) add(Manifest.permission.READ_PHONE_STATE)
                }.toTypedArray()
            )
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
                !accessibilityEnabled ->
                    navController.navigate(Route.RepeatOnboarding(0).path)
                needsPhoneState ->
                    phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
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
            fadeIn(tween(120)) + slideInHorizontally(tween(160)) { it / 12 }
        },
        exitTransition = {
            fadeOut(tween(100))
        },
        popEnterTransition = {
            fadeIn(tween(120))
        },
        popExitTransition = {
            fadeOut(tween(100)) + slideOutHorizontally(tween(160)) { it / 12 }
        }
    ) {
        composable(Route.FirstRunSetup.path) {
            FirstRunSetupScreen(
                onOpenAppSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                },
                onOpenAccessibilitySettings = {
                    app.securePreferences.accessibilityOnboardingSeen = true
                    AccessibilityHelper.openAppAccessibilityDetails(context)
                },
                onSelectSim = { subId ->
                    app.securePreferences.preferredSimSubscriptionId = subId
                },
                onFinished = {
                    app.securePreferences.firstRunSetupDone = true
                    app.securePreferences.accessibilityOnboardingSeen = true
                    AppMetrics.firstRunCompleted()
                    AppMetrics.smsPermission(hasSmsPermission())
                    AppMetrics.accessibilityEnabled(
                        AccessibilityHelper.isLipaBillServiceEnabled(context)
                    )
                    smsGranted = hasSmsPermission()
                    listVm.setSmsPermission(smsGranted)
                    navController.navigate(Route.List.path) {
                        popUpTo(Route.FirstRunSetup.path) { inclusive = true }
                    }
                }
            )
        }
        composable(Route.Permission.path) {
            SmsPermissionScreen(
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
                    skipPermission = true
                    navController.navigate(Route.List.path) {
                        popUpTo(Route.Permission.path) { inclusive = true }
                    }
                }
            )
            LaunchedEffect(smsGranted) {
                if (smsGranted) {
                    navController.navigate(Route.List.path) {
                        popUpTo(Route.Permission.path) { inclusive = true }
                    }
                }
            }
        }
        composable(Route.List.path) {
            MainShellScreen(
                listVm = listVm,
                onOpenTransaction = { /* receipt is a popup on the list */ },
                onRepeatTransaction = { id -> navigateRepeat(id) },
                onOpenSend = { showSendSheet() },
                onOpenSendTo = { contact -> showSendSheet(preselect = contact) },
                onOpenPay = { showPaySheet() },
                onOpenMetrics = {
                    navController.navigate(Route.Metrics.path)
                },
                onOpenTickets = {
                    navController.navigate(Route.Tickets.path)
                },
                onOpenSettings = {
                    navController.navigate(Route.Settings.path)
                }
            )
        }
        composable(Route.Metrics.path) {
            BackHandler { navigateHome() }
            MetricsScreen(
                viewModel = listVm,
                onBack = { navigateHome() }
            )
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
                onPendingPkPassConsumed = { pendingPkPassUri = null }
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
            TicketDetailScreen(
                viewModel = detailVm,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Route.Detail.pattern,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            BackHandler { navigateHome() }
            val detailVm: TransactionDetailViewModel = viewModel(
                factory = viewModelFactory {
                    TransactionDetailViewModel(app, id)
                }
            )
            TransactionDetailScreen(
                viewModel = detailVm,
                onBack = { navigateHome() },
                onRepeat = { txId -> navigateRepeat(txId) }
            )
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
            RepeatConfirmScreen(
                viewModel = repeatVm,
                onBack = { navigateHome() },
                onNeedAccessibility = {
                    navController.navigate(Route.RepeatOnboarding(id).path)
                },
                onManualFallback = {
                    navController.navigate(Route.RepeatManual(id).path)
                },
                onOpenSimSettings = {
                    navController.navigate(Route.Settings.path)
                },
                onRequestPhoneStatePermission = {
                    phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                },
                onConfirm = { runStepUpAndDial(repeatVm) }
            )
        }
        composable(
            route = Route.RepeatOnboarding.pattern,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) {
            BackHandler { navigateHome() }
            AccessibilityOnboardingScreen(
                onOpenSettings = {
                    app.securePreferences.accessibilityOnboardingSeen = true
                    AccessibilityHelper.openAppAccessibilityDetails(context)
                },
                onOpenAppInfo = {
                    app.securePreferences.accessibilityOnboardingSeen = true
                    SideloadRestrictedSettings.openAppInfo(context)
                },
                onContinue = {
                    app.securePreferences.accessibilityOnboardingSeen = true
                    if (AccessibilityHelper.isLipaBillServiceEnabled(context)) {
                        navigateHome()
                    } else {
                        Toast.makeText(
                            context,
                            "LipaBill service still off — enable it in Accessibility settings",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                onCancel = { navigateHome() }
            )
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
            ManualRepeatScreen(
                viewModel = repeatVm,
                onBack = { navigateHome() },
                onCopied = {
                    scope.launch { repeatVm.logManualCopy() }
                }
            )
        }
        composable(Route.Settings.path) {
            BackHandler { navigateHome() }
            val settingsVm: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsVm,
                onBack = { navigateHome() },
                onRequestPhoneStatePermission = {
                    phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                }
            )
        }
        composable(
            route = Route.Processing.pattern,
            arguments = listOf(navArgument("auditId") { type = NavType.LongType })
        ) { entry ->
            val auditId = entry.arguments?.getLong("auditId") ?: return@composable
            BackHandler { navigateHome() }
            ProcessingReceiptScreen(
                auditId = auditId,
                onDone = { navigateHome() },
                onBack = { navigateHome() }
            )
        }
    }
}
