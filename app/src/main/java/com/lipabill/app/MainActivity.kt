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
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lipabill.app.auth.AuthUiState
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.data.tickets.PkPassIntents
import com.lipabill.app.security.AppSecurity
import com.lipabill.app.ui.analytics.MetricsScreen
import com.lipabill.app.ui.auth.AuthGateScreen
import com.lipabill.app.ui.detail.TransactionDetailScreen
import com.lipabill.app.ui.main.MainShellScreen
import com.lipabill.app.ui.navigation.Route
import com.lipabill.app.ui.permissions.SmsPermissionScreen
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    LipaBillRoot(activity = this, app = app)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<Intent> { intent ->
            PkPassIntents.extractUri(intent)?.let { pendingPkPassUri = it }
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        smsGranted = granted
        if (!granted) {
            permanentlyDenied =
                !activity.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)
        }
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

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* optional */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val ok = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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

    fun ensureContactsPermission() {
        if (!hasContactsPermission()) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else {
            sendVm.refreshContactsAccess()
            payVm.refreshContactsAccess()
        }
    }

    fun showSendSheet(preselect: SendContact? = null) {
        sendVm.clearSelection()
        sendVm.setAmountInput("")
        if (preselect != null) {
            sendVm.select(preselect)
        }
        ensureContactsPermission()
        val existing = activity.supportFragmentManager.findFragmentByTag(SendMoneyBottomSheetFragment.TAG)
        if (existing == null) {
            SendMoneyBottomSheetFragment.newInstance()
                .show(activity.supportFragmentManager, SendMoneyBottomSheetFragment.TAG)
        }
    }

    fun showPaySheet() {
        payVm.clearMethod()
        payVm.selectMethod(PayMethod.PAYBILL)
        ensureContactsPermission()
        val existing = activity.supportFragmentManager.findFragmentByTag(PayMoneyBottomSheetFragment.TAG)
        if (existing == null) {
            PayMoneyBottomSheetFragment.newInstance()
                .show(activity.supportFragmentManager, PayMoneyBottomSheetFragment.TAG)
        }
    }

    val startDestination = when {
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

    fun dismissMoneySheets() {
        listOf(SendMoneyBottomSheetFragment.TAG, PayMoneyBottomSheetFragment.TAG).forEach { tag ->
            (activity.supportFragmentManager.findFragmentByTag(tag) as? BottomSheetDialogFragment)
                ?.dismissAllowingStateLoss()
        }
    }

    fun runStepUpAndDial(
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
        app.authManager.authenticateSensitive(
            activity = activity,
            onSuccess = {
                scope.launch {
                    val prepared = prepare()
                    if (prepared == null) {
                        Toast.makeText(
                            context,
                            "Couldn’t start payment — check amount and recipient",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    startDial(prepared)
                    PendingPaymentReceipt.capture(prepared)
                    dismissMoneySheets()
                    navController.navigate(Route.Processing(prepared.auditId).path) {
                        popUpTo(Route.List.path) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            },
            onCancelOrFail = { msg ->
                scope.launch {
                    Toast.makeText(context, "Payment cancelled: $msg", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    fun runStepUpAndDial(repeatVm: RepeatTransactionViewModel) {
        runStepUpAndDial(
            prepare = { repeatVm.prepare() },
            startDial = { repeatVm.startDial(it) },
            pendingId = repeatVm.uiState.value.transaction?.id
        )
    }

    fun runStepUpAndDial(sendVm: SendMoneyViewModel) {
        runStepUpAndDial(
            prepare = { sendVm.prepare() },
            startDial = { sendVm.startDial(it) },
            pendingId = null
        )
    }

    fun runStepUpAndDial(payVm: PayMoneyViewModel) {
        runStepUpAndDial(
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
        composable(Route.Permission.path) {
            SmsPermissionScreen(
                permanentlyDenied = permanentlyDenied,
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
