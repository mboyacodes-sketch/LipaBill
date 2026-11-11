package com.lipabill.app.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lipabill.app.BuildConfig
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.data.sms.ReceiptOpenRequest
import com.lipabill.app.ui.SourceFilterRow
import com.lipabill.app.ui.adapt.LocalWindowForm
import com.lipabill.app.ui.adapt.WindowWidth
import com.lipabill.app.ui.components.BalanceAmountRow
import com.lipabill.app.ui.components.ConsumeSnackbarMessage
import com.lipabill.app.ui.components.WalletAccountSwitch
import com.lipabill.app.ui.detail.TransactionReceiptPopup
import com.lipabill.app.ui.engage.EngageHomeSection
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.SheetTextEditor
import com.lipabill.app.ui.sheet.rememberSheetEditor
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.ui.theme.ActionPay
import com.lipabill.app.ui.theme.ActionTickets
import com.lipabill.app.ui.theme.BrandNavy
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Canvas as CreamCanvas
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.LipaPalette
import com.lipabill.app.ui.theme.LocalLipaColors
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnAccent
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import com.lipabill.app.ui.util.opensOnDigitKeys
import com.lipabill.app.ui.util.rememberHideKeyboard
import com.lipabill.app.ui.util.safeDrawingBottomPadding
import com.lipabill.app.ui.util.safeDrawingTopPadding
import com.lipabill.app.ussd.PendingPaymentReceipt
import com.lipabill.app.viewmodel.DayGroup
import com.lipabill.app.viewmodel.EngagementViewModel
import com.lipabill.app.viewmodel.TransactionListViewModel
import com.lipabill.app.viewmodel.WalletAccount
import com.lipabill.app.viewmodel.fulizaDueCopy
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Off-white + route-blue home: balance pill, actions, transactions.
 * Layout tokens measured from the home mock (≈390dp frame).
 */
internal object HomeMock {
    val Margin = 20.dp
    val SectionGap = 18.dp
    val HeaderIcon = 40.dp
    val HeaderIconGap = 10.dp
    /** Breathing room under status bar (inset applied on the list container). */
    val HeaderTop = 8.dp
    /** Gap under search/gear before the balance pill. */
    val HeaderBottom = 48.dp

    val PillRadius = 28.dp
    val PillMinHeight = 104.dp
    val PillPadH = 22.dp
    val PillPadV = 16.dp
    val PillLabelGap = 8.dp

    val ActionSize = 60.dp
    val ActionIcon = 24.dp
    val ActionLabelGap = 8.dp

    val SearchRadius = 24.dp
    val SearchMinHeight = 44.dp

    val TxAvatar = 40.dp
    val TxRowV = 8.dp
}

/**
 * Off-white + route-blue home: balance pill, actions, transactions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    modifier: Modifier = Modifier,
    openReceipt: ReceiptOpenRequest? = null,
    onReceiptOpened: () -> Unit = {},
    onRepeatTransaction: (Long) -> Unit = {},
    onSend: () -> Unit = {},
    onReceive: () -> Unit = {},
    onExchange: () -> Unit = {},
    onTickets: () -> Unit = {},
    onRequestSms: () -> Unit = {},
    onRescan: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenHabits: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val app = context.applicationContext as LipaBillApp
    val lifecycleOwner = LocalLifecycleOwner.current
    var homeResumeTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeResumeTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val engageVm: EngagementViewModel = viewModel()
    val engage by engageVm.uiState.collectAsStateWithLifecycle()
    var receiptTxId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(openReceipt?.id, openReceipt?.code) {
        val request = openReceipt ?: return@LaunchedEffect
        val resolved = viewModel.resolveReceiptId(request.id, request.code)
        if (resolved == null) {
            onReceiptOpened()
            return@LaunchedEffect
        }
        receiptTxId = resolved
        // Wait until the row is on screen before forgetting the notification request.
        // Clearing earlier lets a home navigation recreate this screen with nothing to open.
        viewModel.observeTransaction(resolved).filterNotNull().first()
        onReceiptOpened()
    }
    ConsumeSnackbarMessage(state.scanMessage, snackbar) {
        viewModel.clearScanMessage()
    }

    val shownBalance = state.shownBalance()
    val canSendPay = state.canFundPayment()
    // Full row (incl. rawBody) so share can send truncated original SMS.
    val receiptTx by remember(receiptTxId) {
        receiptTxId?.let { viewModel.observeTransaction(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    val showingReceipt = receiptTxId != null
    val hideKeyboard = rememberHideKeyboard()
    val focusManager = LocalFocusManager.current
    var searchOpen by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var searchFieldFocused by remember { mutableStateOf(false) }
    val searchEditor = rememberSheetEditor(state.searchQuery)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val suppressSearchDismiss = remember { intArrayOf(0) }
    var homeEpoch by remember { mutableIntStateOf(0) }
    // Header search icon reveals the transactions search field (index after hero+actions).
    val txHeaderIndex = 2

    fun closeSearch() {
        searchOpen = false
        searchFocused = false
        searchFieldFocused = false
        focusManager.clearFocus(force = true)
        if (searchEditor.text.isNotEmpty() || state.searchQuery.isNotEmpty()) {
            searchEditor.overwrite("")
            viewModel.setSearchQuery("")
        }
    }

    /** Same landing as See all: the transaction list fills the screen, header at the top. */
    fun showAllTransactions() {
        scope.launch { listState.animateScrollToItem(txHeaderIndex) }
    }

    val density = LocalDensity.current
    // Debug/Maestro: scroll history into frame, then drop in the completed demo row.
    // Peek until insert succeeds — taking first lost the queue when this effect was
    // cancelled mid-scroll (lifecycle pause, search dismiss, recomposition).
    suspend fun revealDemoHomeRowIfQueued() {
        if (!BuildConfig.DEBUG) return
        val snap = PendingPaymentReceipt.peekDemoHomeReveal() ?: return
        suppressSearchDismiss[0]++
        try {
            listState.animateScrollToItem(txHeaderIndex)
            delay(180)
            listState.animateScrollBy(with(density) { 64.dp.toPx() })
            delay(140)
            app.repository.insertDemoPaymentResult(snap)
            PendingPaymentReceipt.takeDemoHomeReveal()
        } finally {
            suppressSearchDismiss[0]--
        }
    }
    LaunchedEffect(Unit) { revealDemoHomeRowIfQueued() }
    LaunchedEffect(homeResumeTick) {
        if (homeResumeTick == 0) return@LaunchedEffect
        revealDemoHomeRowIfQueued()
    }

    fun openSearch(index: Int) {
        val epoch = homeEpoch
        scope.launch {
            suppressSearchDismiss[0]++
            try {
                listState.animateScrollToItem(index)
                if (epoch != homeEpoch) return@launch
                searchOpen = true
                searchFocused = true
                withFrameNanos { }
                if (epoch != homeEpoch) return@launch
                listState.scrollToItem(index)
            } finally {
                suppressSearchDismiss[0]--
            }
        }
    }

    fun resetHome() {
        homeEpoch++
        closeSearch()
        scope.launch { listState.scrollToItem(0) }
    }

    fun onSearchFieldFocus(focused: Boolean) {
        val wasFocused = searchFieldFocused
        searchFieldFocused = focused
        if (focused) {
            searchOpen = true
            searchFocused = true
        } else if (wasFocused) {
            // Leaving the field drops the query so the list is not left filtered.
            closeSearch()
        }
    }
    // Index-only: scrollOffset thrashing was toggling blur every frame.
    val listCoversSwoosh by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val window = LocalWindowForm.current.width
    val pageMargin = if (window == WindowWidth.Compact) HomeMock.Margin else 28.dp
    val homeMoved by remember(window, txHeaderIndex) {
        derivedStateOf {
            val listFull = if (window == WindowWidth.Expanded) {
                listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > 0
            } else {
                listState.firstVisibleItemIndex >= txHeaderIndex
            }
            listFull || searchOpen || searchFocused || state.searchQuery.isNotBlank()
        }
    }
    BackHandler(enabled = homeMoved && !showingReceipt, onBack = ::resetHome)

    LaunchedEffect(showingReceipt) {
        if (showingReceipt && (searchOpen || searchFocused)) closeSearch()
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (suppressSearchDismiss[0] > 0) return@LaunchedEffect
        if (listState.isScrollInProgress && searchFocused) {
            closeSearch()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CreamCanvas)
            .hideKeyboardOnOutsideTap(onOutsideTap = { if (searchOpen) closeSearch() })
    ) {
        // Left page swoosh — draws under the status bar; header content is inset separately.
        val swoosh = Accent
        Canvas(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(if (window == WindowWidth.Expanded) 0.38f else 0.62f)
                .height(260.dp)
                .graphicsLayer { alpha = if (listCoversSwoosh) 0.35f else 1f }
        ) {
            val w = size.width
            val h = size.height
            val leaf = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.55f, 0f)
                cubicTo(w * 0.42f, h * 0.18f, w * 0.38f, h * 0.45f, w * 0.22f, h * 0.68f)
                cubicTo(w * 0.12f, h * 0.82f, w * 0.05f, h * 0.72f, 0f, h * 0.55f)
                close()
            }
            drawPath(leaf, color = swoosh.copy(alpha = 0.88f))
            val soft = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.38f, 0f)
                cubicTo(w * 0.30f, h * 0.22f, w * 0.22f, h * 0.38f, 0f, h * 0.32f)
                close()
            }
            drawPath(soft, color = swoosh.copy(alpha = 0.50f))
        }

        if (window == WindowWidth.Expanded) {
            HomeExpandedPane(
                pageMargin = pageMargin,
                isScanning = state.isScanning,
                onRescan = onRescan,
                showingReceipt = showingReceipt,
                listState = listState,
                balance = shownBalance,
                fulizaDue = state.fulizaDue,
                account = state.account,
                showFuliza = state.showFuliza,
                showSendPay = state.showSendPay,
                grouped = state.grouped,
                hasSmsPermission = state.hasSmsPermission,
                searchQuery = state.searchQuery,
                typeFilter = state.typeFilter,
                availableTypes = state.availableTypes,
                onTypeFilter = viewModel::setTypeFilter,
                sourceFilter = state.sourceFilter,
                availableSources = state.availableSources,
                onSourceFilter = viewModel::setSourceFilter,
                onAccountSelected = viewModel::setWalletAccount,
                searchEditor = searchEditor,
                onQueryChange = viewModel::setSearchQuery,
                searchOpen = searchOpen,
                searchFocused = searchFocused,
                onSearchFocus = ::onSearchFieldFocus,
                onSearch = { openSearch(0) },
                onOpenSettings = onOpenSettings,
                onOpenProfile = onOpenProfile,
                onSend = onSend,
                onPay = onReceive,
                onMetrics = onExchange,
                onTickets = onTickets,
                onRequestSms = onRequestSms,
                sendPayEnabled = canSendPay,
                onOpenTransaction = { id ->
                    hideKeyboard()
                    closeSearch()
                    receiptTxId = id
                },
                onKey = { ch -> viewModel.setSearchQuery(searchEditor.insert(ch)) },
                onBackspace = { viewModel.setSearchQuery(searchEditor.backspace()) },
                onKeyboardDone = { closeSearch() },
                engageSection = {
                    EngageHomeSection(
                        state = engage,
                        onOpen = onOpenHabits,
                        modifier = Modifier
                            .padding(horizontal = pageMargin)
                            .padding(top = HomeMock.SectionGap)
                    )
                }
            )
        } else Column(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.isScanning,
                onRefresh = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .safeDrawingTopPadding()
            ) {
                val homeSheet = CreamCanvas
                val listBottom = if (searchFocused) {
                    Space.gap
                } else {
                    Space.section + safeDrawingBottomPadding()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = listBottom)
                ) {
                item(key = "hero") {
                    HomeBrandHeader(
                        horizontalPadding = pageMargin,
                        onSearch = { openSearch(txHeaderIndex) },
                        onOpenSettings = onOpenSettings
                    )
                }
                item(key = "balance-actions") {
                    if (window == WindowWidth.Medium) {
                        Row(
                            modifier = Modifier.padding(horizontal = pageMargin),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            BalanceSection(
                                balance = shownBalance,
                                fulizaDue = state.fulizaDue,
                                account = state.account,
                                onAccountSelected = viewModel::setWalletAccount,
                                onOpenProfile = onOpenProfile,
                                showFuliza = state.showFuliza,
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionsRow(
                                onSend = onSend,
                                onDeposit = onReceive,
                                onDetails = onExchange,
                                onTickets = onTickets,
                                sendPayEnabled = canSendPay,
                                showSendPay = state.showSendPay,
                                spread = false
                            )
                        }
                    } else {
                        BalanceSection(
                            balance = shownBalance,
                            fulizaDue = state.fulizaDue,
                            account = state.account,
                            onAccountSelected = viewModel::setWalletAccount,
                            onOpenProfile = onOpenProfile,
                            showFuliza = state.showFuliza,
                            modifier = Modifier.padding(horizontal = pageMargin)
                        )
                        HomeSourceFilter(
                            account = state.account,
                            sources = state.availableSources,
                            selected = state.sourceFilter,
                            onSelect = viewModel::setSourceFilter,
                            pageMargin = pageMargin
                        )
                        Spacer(modifier = Modifier.height(HomeMock.SectionGap))
                        QuickActionsRow(
                            onSend = onSend,
                            onDeposit = onReceive,
                            onDetails = onExchange,
                            onTickets = onTickets,
                            sendPayEnabled = canSendPay,
                            showSendPay = state.showSendPay,
                            modifier = Modifier.padding(horizontal = pageMargin)
                        )
                    }
                    if (window == WindowWidth.Medium) {
                        HomeSourceFilter(
                            account = state.account,
                            sources = state.availableSources,
                            selected = state.sourceFilter,
                            onSelect = viewModel::setSourceFilter,
                            pageMargin = pageMargin
                        )
                    }
                    Spacer(modifier = Modifier.height(HomeMock.SectionGap))
                }
                item(key = "engage") {
                    Column(modifier = Modifier.padding(horizontal = pageMargin)) {
                        EngageHomeSection(
                            state = engage,
                            onOpen = onOpenHabits
                        )
                        Spacer(modifier = Modifier.height(HomeMock.SectionGap))
                    }
                }

                transactionFeed(
                    account = state.account,
                    grouped = state.grouped,
                    hasSmsPermission = state.hasSmsPermission,
                    searchQuery = state.searchQuery,
                    typeFilter = state.typeFilter,
                    availableTypes = state.availableTypes,
                    onTypeFilter = viewModel::setTypeFilter,
                    sourceFilter = state.sourceFilter,
                    searchEditor = searchEditor,
                    onQueryChange = viewModel::setSearchQuery,
                    searchOpen = searchOpen,
                    onSearchFocus = ::onSearchFieldFocus,
                    onRequestSms = onRequestSms,
                    onOpenTransaction = { id ->
                        hideKeyboard()
                        closeSearch()
                        receiptTxId = id
                    },
                    pageMargin = pageMargin,
                    sheetColor = homeSheet,
                    headerShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    onSeeAll = { showAllTransactions() }
                )
                item(key = "tx-sheet-tail") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(CreamCanvas)
                    )
                }
                }
            }

            if (searchFocused && !showingReceipt) {
                InAppKeyboard(
                    startOnDigits = state.searchQuery.opensOnDigitKeys(),
                    onChar = { ch ->
                        viewModel.setSearchQuery(searchEditor.insert(ch))
                    },
                    onBackspace = {
                        viewModel.setSearchQuery(searchEditor.backspace())
                    },
                    onDone = { closeSearch() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CreamCanvas)
                        .navigationBarsPadding()
                        .padding(horizontal = Space.page, vertical = Space.gap)
                )
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )
        receiptTx?.let { tx ->
            TransactionReceiptPopup(
                tx = tx,
                onDismiss = { receiptTxId = null },
                onRepeat = { id ->
                    receiptTxId = null
                    onRepeatTransaction(id)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeExpandedPane(
    pageMargin: Dp,
    isScanning: Boolean,
    onRescan: () -> Unit,
    showingReceipt: Boolean,
    listState: LazyListState,
    balance: Double?,
    fulizaDue: LocalDate?,
    account: WalletAccount,
    showFuliza: Boolean,
    showSendPay: Boolean,
    grouped: List<DayGroup>,
    hasSmsPermission: Boolean,
    searchQuery: String,
    typeFilter: TransactionType?,
    availableTypes: List<TransactionType>,
    onTypeFilter: (TransactionType?) -> Unit,
    sourceFilter: MoneySource?,
    availableSources: List<MoneySource>,
    onSourceFilter: (MoneySource?) -> Unit,
    onAccountSelected: (WalletAccount) -> Unit,
    searchEditor: SheetTextEditor,
    onQueryChange: (String) -> Unit,
    searchOpen: Boolean,
    searchFocused: Boolean,
    onSearchFocus: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onSend: () -> Unit,
    onPay: () -> Unit,
    onMetrics: () -> Unit,
    onTickets: () -> Unit,
    onRequestSms: () -> Unit,
    sendPayEnabled: Boolean,
    onOpenTransaction: (Long) -> Unit,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onKeyboardDone: () -> Unit,
    engageSection: @Composable () -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .safeDrawingTopPadding()
                .navigationBarsPadding()
                .padding(bottom = Space.section)
        ) {
            HomeBrandHeader(
                onSearch = onSearch,
                onOpenSettings = onOpenSettings,
                horizontalPadding = pageMargin,
                bottomPadding = 16.dp
            )
            BalanceSection(
                balance = balance,
                fulizaDue = fulizaDue,
                account = account,
                onAccountSelected = onAccountSelected,
                onOpenProfile = onOpenProfile,
                showFuliza = showFuliza,
                modifier = Modifier.padding(horizontal = pageMargin)
            )
            HomeSourceFilter(
                account = account,
                sources = availableSources,
                selected = sourceFilter,
                onSelect = onSourceFilter,
                pageMargin = pageMargin
            )
            Spacer(modifier = Modifier.height(HomeMock.SectionGap))
            QuickActionsRow(
                onSend = onSend,
                onDeposit = onPay,
                onDetails = onMetrics,
                onTickets = onTickets,
                sendPayEnabled = sendPayEnabled,
                showSendPay = showSendPay,
                modifier = Modifier.padding(horizontal = pageMargin)
            )
            engageSection()
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .safeDrawingTopPadding()
            ) {
                val listBottom = if (searchFocused) {
                    Space.gap
                } else {
                    Space.section + safeDrawingBottomPadding()
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = listBottom)
                ) {
                    transactionFeed(
                        account = account,
                        grouped = grouped,
                        hasSmsPermission = hasSmsPermission,
                        searchQuery = searchQuery,
                        typeFilter = typeFilter,
                        availableTypes = availableTypes,
                        onTypeFilter = onTypeFilter,
                        sourceFilter = sourceFilter,
                        searchEditor = searchEditor,
                        onQueryChange = onQueryChange,
                        searchOpen = searchOpen,
                        onSearchFocus = onSearchFocus,
                        onRequestSms = onRequestSms,
                        onOpenTransaction = onOpenTransaction,
                        pageMargin = pageMargin,
                        sheetColor = null
                    )
                }
            }
            if (searchFocused && !showingReceipt) {
                InAppKeyboard(
                    startOnDigits = searchQuery.opensOnDigitKeys(),
                    onChar = onKey,
                    onBackspace = onBackspace,
                    onDone = onKeyboardDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CreamCanvas)
                        .navigationBarsPadding()
                        .padding(horizontal = pageMargin, vertical = Space.gap)
                )
            }
        }
    }
}

@Composable
private fun HomeBrandHeader(
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    horizontalPadding: Dp = HomeMock.Margin,
    bottomPadding: Dp = HomeMock.HeaderBottom
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = HomeMock.HeaderTop,
                bottom = bottomPadding
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        HeaderIconButton(
            icon = Icons.Outlined.Search,
            contentDescription = "Search transactions",
            onClick = onSearch
        )
        Spacer(modifier = Modifier.width(HomeMock.HeaderIconGap))
        HeaderIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = "Settings",
            onClick = onOpenSettings
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(HomeMock.HeaderIcon)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Ink,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun HomeSourceFilter(
    account: WalletAccount,
    sources: List<MoneySource>,
    selected: MoneySource?,
    onSelect: (MoneySource?) -> Unit,
    pageMargin: Dp
) {
    if (account == WalletAccount.FULIZA || sources.size <= 1) return
    Spacer(modifier = Modifier.height(10.dp))
    SourceFilterRow(
        sources = sources,
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.padding(horizontal = pageMargin)
    )
}

@Composable
private fun BalanceSection(
    balance: Double?,
    account: WalletAccount,
    onAccountSelected: (WalletAccount) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    showFuliza: Boolean = true,
    fulizaDue: LocalDate? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HomeMock.PillMinHeight)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(HomeMock.PillRadius),
                spotColor = Color.Black.copy(alpha = 0.10f),
                ambientColor = Color.Black.copy(alpha = 0.05f)
            )
            .clip(RoundedCornerShape(HomeMock.PillRadius))
            .background(CardWhite)
    ) {
        // Mirrored leaf — anchored to the right edge of the pill.
        val leafNavy = Accent
        val leafWash = SoftBlue
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            // Build a left-side leaf, then mirror onto the right.
            fun mirrorX(x: Float) = w - x
            val leaf = Path().apply {
                moveTo(mirrorX(w * 0.42f), 0f)
                cubicTo(
                    mirrorX(w * 0.32f), h * 0.02f,
                    mirrorX(w * 0.28f), h * 0.28f,
                    mirrorX(w * 0.22f), h * 0.52f
                )
                cubicTo(
                    mirrorX(w * 0.16f), h * 0.78f,
                    mirrorX(w * 0.10f), h * 0.92f,
                    mirrorX(0f), h
                )
                lineTo(w, h)
                lineTo(w, 0f)
                close()
            }
            drawPath(leaf, color = leafNavy.copy(alpha = 0.90f))
            val highlight = Path().apply {
                moveTo(mirrorX(w * 0.28f), 0f)
                cubicTo(
                    mirrorX(w * 0.18f), h * 0.12f,
                    mirrorX(w * 0.12f), h * 0.35f,
                    mirrorX(0f), h * 0.42f
                )
                lineTo(w, h * 0.42f)
                lineTo(w, 0f)
                close()
            }
            drawPath(highlight, color = leafNavy.copy(alpha = 0.45f))
            drawCircle(
                color = leafWash.copy(alpha = 0.35f),
                radius = h * 0.38f,
                center = Offset(mirrorX(w * 0.06f), h * 0.12f)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = HomeMock.PillPadH,
                    // Clear of the request disc, which is centered on the pill.
                    end = 84.dp,
                    top = HomeMock.PillPadV,
                    bottom = HomeMock.PillPadV
                ),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(HomeMock.PillLabelGap)
        ) {
            WalletAccountSwitch(
                account = account,
                onSelect = onAccountSelected,
                accounts = if (showFuliza) {
                    WalletAccount.entries
                } else {
                    listOf(WalletAccount.MPESA)
                }
            )
            Text(
                text = account.balanceCaption,
                style = HomeType.caption,
                color = Mute
            )
            BalanceAmountRow(
                balance = balance,
                amountStyle = HomeType.balance,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            )
            if (account == WalletAccount.FULIZA && fulizaDue != null && (balance ?: 0.0) > 0.0) {
                Text(
                    text = fulizaDueCopy(fulizaDue, LocalDate.now()).replaceFirstChar { it.uppercase() } +
                        " · daily charges since are not included",
                    style = HomeType.caption,
                    color = Mute
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(SoftBlue)
                .clickable(onClick = onOpenProfile),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCode2,
                contentDescription = "Request",
                tint = if (LocalLipaColors.current.canvas == LipaPalette.Dark.canvas) {
                    Color.White
                } else {
                    Accent
                },
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun QuickActionsRow(
    onSend: () -> Unit,
    onDeposit: () -> Unit,
    onDetails: () -> Unit,
    onTickets: () -> Unit,
    sendPayEnabled: Boolean,
    spread: Boolean = true,
    modifier: Modifier = Modifier,
    showSendPay: Boolean = true
) {
    if (!showSendPay) {
        if (spread) {
            LedgerMovesRow(
                onInsights = onDetails,
                onPasses = onTickets,
                modifier = modifier
            )
        } else {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickAction(
                    icon = Icons.Outlined.BarChart,
                    label = "Insights",
                    onClick = onDetails,
                    face = ActionMetrics,
                    iconTint = BrandNavy
                )
                QuickAction(
                    icon = Icons.Outlined.ConfirmationNumber,
                    label = "Passes",
                    onClick = onTickets,
                    face = ActionTickets,
                    iconTint = BrandNavy
                )
            }
        }
        return
    }
    Row(
        modifier = if (spread) modifier.fillMaxWidth() else modifier,
        horizontalArrangement = if (spread) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.spacedBy(12.dp)
        }
    ) {
        QuickAction(
            icon = Icons.Outlined.NorthEast,
            label = "Send",
            onClick = onSend,
            enabled = sendPayEnabled,
            face = Accent,
            iconTint = OnAccent
        )
        QuickAction(
            icon = Icons.Outlined.Payments,
            label = "Pay",
            onClick = onDeposit,
            enabled = sendPayEnabled,
            face = ActionPay,
            iconTint = BrandNavy
        )
        QuickAction(
            icon = Icons.Outlined.BarChart,
            label = "Insights",
            onClick = onDetails,
            face = ActionMetrics,
            iconTint = BrandNavy
        )
        QuickAction(
            icon = Icons.Outlined.ConfirmationNumber,
            label = "Passes",
            onClick = onTickets,
            face = ActionTickets,
            iconTint = BrandNavy
        )
    }
}

@Composable
private fun LedgerMovesRow(
    onInsights: () -> Unit,
    onPasses: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LedgerMoveCard(
            icon = Icons.Outlined.BarChart,
            label = "Insights",
            caption = "Where it went",
            face = ActionMetrics,
            onClick = onInsights,
            modifier = Modifier.weight(1f)
        )
        LedgerMoveCard(
            icon = Icons.Outlined.ConfirmationNumber,
            label = "Passes",
            caption = "What it became",
            face = ActionTickets,
            onClick = onPasses,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LedgerMoveCard(
    icon: ImageVector,
    label: String,
    caption: String,
    face: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(face),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandNavy,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = label,
                style = HomeType.rowTitle,
                color = Ink,
                maxLines = 1
            )
            Text(
                text = caption,
                style = HomeType.caption,
                color = Mute,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    face: Color,
    iconTint: Color,
    enabled: Boolean = true
) {
    val labelColor = if (enabled) Ink else Mute
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HomeMock.ActionLabelGap),
        modifier = Modifier
            .width(HomeMock.ActionSize + 8.dp)
            .alpha(if (enabled) 1f else 0.55f)
    ) {
        Box(
            modifier = Modifier
                .size(HomeMock.ActionSize)
                .shadow(
                    if (enabled) 4.dp else 0.dp,
                    CircleShape,
                    clip = false,
                    spotColor = Color.Black.copy(alpha = 0.12f)
                )
                .clip(CircleShape)
                .background(if (enabled) face else face.copy(alpha = 0.5f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) iconTint else Mute,
                modifier = Modifier.size(HomeMock.ActionIcon)
            )
        }
        Text(
            text = label,
            style = HomeType.label,
            color = labelColor,
            maxLines = 1
        )
    }
}
