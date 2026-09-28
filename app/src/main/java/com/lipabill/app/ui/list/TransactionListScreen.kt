package com.lipabill.app.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.ui.adapt.LocalWindowForm
import com.lipabill.app.ui.adapt.WindowWidth
import com.lipabill.app.ui.components.BalanceAmountRow
import com.lipabill.app.ui.detail.TransactionReceiptPopup
import com.lipabill.app.ui.sheet.InAppKeyboard
import com.lipabill.app.ui.sheet.SheetInputStyle
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.ActionMetrics
import com.lipabill.app.ui.theme.ActionPay
import com.lipabill.app.ui.theme.ActionTickets
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Canvas as CreamCanvas
import com.lipabill.app.ui.theme.Debit
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.theme.avatarPastel
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.bringIntoViewOnFocus
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatActivityTime
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.ui.util.hideKeyboardOnOutsideTap
import com.lipabill.app.ui.util.isOutgoing
import com.lipabill.app.ui.util.rememberHideKeyboard
import com.lipabill.app.ussd.PaymentAccessGates
import com.lipabill.app.viewmodel.TransactionListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Off-white + route-blue home: balance pill, actions, frequent, transactions.
 * Layout tokens measured from the home mock (≈390dp frame).
 */
private object HomeMock {
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

    val FrequentAvatar = 52.dp
    val FrequentGap = 12.dp
    val FrequentLabelGap = 6.dp

    val SearchRadius = 24.dp
    val SearchMinHeight = 44.dp

    val TxAvatar = 40.dp
    val TxRowV = 8.dp
}

/**
 * Off-white + route-blue home: balance pill, actions, frequent, transactions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    modifier: Modifier = Modifier,
    onRepeatTransaction: (Long) -> Unit = {},
    onSend: () -> Unit = {},
    onSendTo: (SendContact) -> Unit = {},
    onReceive: () -> Unit = {},
    onExchange: () -> Unit = {},
    onTickets: () -> Unit = {},
    onRescan: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val app = context.applicationContext as LipaBillApp
    val alwaysShowBalance by app.alwaysShowBalance.collectAsStateWithLifecycle()
    val favouritesSectionEnabled by app.favouritesSectionEnabled.collectAsStateWithLifecycle()
    var receiptTxId by remember { mutableStateOf<Long?>(null) }
    var paymentAccess by remember { mutableStateOf(PaymentAccessGates.evaluate(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                paymentAccess = PaymentAccessGates.evaluate(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        paymentAccess = PaymentAccessGates.evaluate(context)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun runIfPaymentsReady(action: () -> Unit) {
        val status = PaymentAccessGates.evaluate(context).also { paymentAccess = it }
        if (status.ready) {
            action()
        } else {
            Toast.makeText(
                context,
                status.blockReason ?: "Finish setup in Profile to send and pay",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(state.scanMessage) {
        val msg = state.scanMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearScanMessage()
    }

    val flat = remember(state.grouped) { state.grouped.flatMap { it.items } }
    val frequent = state.frequentContacts
    // Full row (incl. rawBody) so share can send truncated original SMS.
    val receiptTx by remember(receiptTxId) {
        receiptTxId?.let { viewModel.observeTransaction(it) } ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    val showingReceipt = receiptTxId != null
    val hideKeyboard = rememberHideKeyboard()
    val focusManager = LocalFocusManager.current
    var searchFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Header search icon scrolls to the transactions search field (index after hero+actions[+frequent]).
    val txHeaderIndex = if (favouritesSectionEnabled && frequent.isNotEmpty()) 3 else 2
    val listCoversSwoosh by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 64
        }
    }
    val window = LocalWindowForm.current.width
    val pageMargin = if (window == WindowWidth.Compact) HomeMock.Margin else 28.dp

    LaunchedEffect(showingReceipt) {
        if (showingReceipt && searchFocused) {
            searchFocused = false
            focusManager.clearFocus(force = true)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && searchFocused) {
            searchFocused = false
            focusManager.clearFocus(force = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CreamCanvas)
            .hideKeyboardOnOutsideTap()
    ) {
        // Left page swoosh — mirror of the design’s top-right leaf (stays clear of status icons).
        Canvas(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(if (window == WindowWidth.Expanded) 0.38f else 0.62f)
                .height(260.dp)
                .statusBarsPadding()
                .then(if (listCoversSwoosh) Modifier.blur(20.dp) else Modifier)
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
            drawPath(leaf, color = Accent.copy(alpha = 0.88f))
            val soft = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.38f, 0f)
                cubicTo(w * 0.30f, h * 0.22f, w * 0.22f, h * 0.38f, 0f, h * 0.32f)
                close()
            }
            drawPath(soft, color = Accent.copy(alpha = 0.50f))
        }

        if (window == WindowWidth.Expanded) {
            HomeExpandedPane(
                pageMargin = pageMargin,
                isScanning = state.isScanning,
                onRescan = onRescan,
                showingReceipt = showingReceipt,
                listState = listState,
                balance = state.latestBalance,
                alwaysShowBalance = alwaysShowBalance,
                frequent = frequent,
                showFrequent = favouritesSectionEnabled && frequent.isNotEmpty(),
                flat = flat,
                hasSmsPermission = state.hasSmsPermission,
                searchQuery = state.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                searchFocused = searchFocused,
                onSearchFocus = { searchFocused = it },
                onSearch = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                        searchFocused = true
                    }
                },
                onOpenSettings = onOpenSettings,
                onSend = { runIfPaymentsReady(onSend) },
                onPay = { runIfPaymentsReady(onReceive) },
                onMetrics = onExchange,
                onTickets = onTickets,
                sendPayEnabled = paymentAccess.ready,
                onAddFrequent = { runIfPaymentsReady(onSend) },
                onSelectFrequent = { contact -> runIfPaymentsReady { onSendTo(contact) } },
                onOpenTransaction = { id ->
                    hideKeyboard()
                    searchFocused = false
                    receiptTxId = id
                },
                onKey = { ch -> viewModel.setSearchQuery(state.searchQuery + ch) },
                onBackspace = { viewModel.setSearchQuery(state.searchQuery.dropLast(1)) },
                onKeyboardDone = {
                    searchFocused = false
                    focusManager.clearFocus(force = true)
                }
            )
        } else Column(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.isScanning,
                onRefresh = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .then(if (showingReceipt) Modifier.blur(14.dp) else Modifier)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = if (searchFocused) Space.gap else Space.section
                    )
                ) {
                item(key = "hero") {
                    HomeBrandHeader(
                        horizontalPadding = pageMargin,
                        onSearch = {
                            scope.launch {
                                listState.animateScrollToItem(txHeaderIndex)
                                searchFocused = true
                            }
                        },
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
                                balance = state.latestBalance,
                                alwaysShowBalance = alwaysShowBalance,
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionsRow(
                                onSend = { runIfPaymentsReady(onSend) },
                                onDeposit = { runIfPaymentsReady(onReceive) },
                                onDetails = onExchange,
                                onTickets = onTickets,
                                sendPayEnabled = paymentAccess.ready,
                                spread = false
                            )
                        }
                    } else {
                        BalanceSection(
                            balance = state.latestBalance,
                            alwaysShowBalance = alwaysShowBalance,
                            modifier = Modifier.padding(horizontal = pageMargin)
                        )
                        Spacer(modifier = Modifier.height(HomeMock.SectionGap))
                        QuickActionsRow(
                            onSend = { runIfPaymentsReady(onSend) },
                            onDeposit = { runIfPaymentsReady(onReceive) },
                            onDetails = onExchange,
                            onTickets = onTickets,
                            sendPayEnabled = paymentAccess.ready,
                            modifier = Modifier.padding(horizontal = pageMargin)
                        )
                    }
                    Spacer(modifier = Modifier.height(HomeMock.SectionGap))
                }
                if (favouritesSectionEnabled && frequent.isNotEmpty()) {
                    item(key = "frequent") {
                        Column(modifier = Modifier.padding(horizontal = pageMargin)) {
                            SectionHeader(
                                title = "Frequent",
                                trailing = null,
                                onOpen = { runIfPaymentsReady(onSend) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            FrequentContactsRow(
                                contacts = frequent,
                                onAdd = { runIfPaymentsReady(onSend) },
                                onSelect = { contact -> runIfPaymentsReady { onSendTo(contact) } }
                            )
                        }
                        Spacer(modifier = Modifier.height(HomeMock.SectionGap))
                    }
                }

                item(key = "tx-header") {
                    val sheetTop = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CreamCanvas, sheetTop)
                            .padding(horizontal = pageMargin)
                            .padding(top = 10.dp)
                    ) {
                        SectionHeader(
                            title = "Transactions",
                            trailing = "See all",
                            onOpen = {
                                scope.launch { listState.animateScrollToItem(txHeaderIndex) }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TransactionSearchField(
                            query = state.searchQuery,
                            onQueryChange = viewModel::setSearchQuery,
                            onFocusChange = { searchFocused = it }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

            if (flat.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        hasPermission = state.hasSmsPermission,
                        searching = state.searchQuery.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CreamCanvas)
                            .padding(horizontal = pageMargin)
                    )
                }
            } else {
                items(items = flat, key = { it.id }) { tx ->
                    TransactionRow(
                        tx = tx,
                        onClick = {
                            hideKeyboard()
                            searchFocused = false
                            receiptTxId = tx.id
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CreamCanvas)
                            .padding(horizontal = pageMargin)
                    )
                }
            }
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
                    startOnDigits = state.searchQuery.any { it.isDigit() } &&
                        state.searchQuery.none { it.isLetter() },
                    onChar = { ch ->
                        viewModel.setSearchQuery(state.searchQuery + ch)
                    },
                    onBackspace = {
                        viewModel.setSearchQuery(state.searchQuery.dropLast(1))
                    },
                    onDone = {
                        searchFocused = false
                        focusManager.clearFocus(force = true)
                    },
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
            modifier = Modifier.align(Alignment.BottomCenter)
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

@Composable
private fun HomeExpandedPane(
    pageMargin: Dp,
    isScanning: Boolean,
    onRescan: () -> Unit,
    showingReceipt: Boolean,
    listState: LazyListState,
    balance: Double?,
    alwaysShowBalance: Boolean,
    frequent: List<SendContact>,
    showFrequent: Boolean,
    flat: List<MpesaTransaction>,
    hasSmsPermission: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchFocused: Boolean,
    onSearchFocus: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onSend: () -> Unit,
    onPay: () -> Unit,
    onMetrics: () -> Unit,
    onTickets: () -> Unit,
    sendPayEnabled: Boolean,
    onAddFrequent: () -> Unit,
    onSelectFrequent: (SendContact) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onKeyboardDone: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
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
                alwaysShowBalance = alwaysShowBalance,
                modifier = Modifier.padding(horizontal = pageMargin)
            )
            Spacer(modifier = Modifier.height(HomeMock.SectionGap))
            QuickActionsRow(
                onSend = onSend,
                onDeposit = onPay,
                onDetails = onMetrics,
                onTickets = onTickets,
                sendPayEnabled = sendPayEnabled,
                modifier = Modifier.padding(horizontal = pageMargin)
            )
            if (showFrequent) {
                Spacer(modifier = Modifier.height(HomeMock.SectionGap))
                Column(modifier = Modifier.padding(horizontal = pageMargin)) {
                    SectionHeader(title = "Frequent", trailing = null, onOpen = onSend)
                    Spacer(modifier = Modifier.height(10.dp))
                    FrequentContactsRow(
                        contacts = frequent,
                        onAdd = onAddFrequent,
                        onSelect = onSelectFrequent
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .then(if (showingReceipt) Modifier.blur(14.dp) else Modifier)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Space.section)
                ) {
                    item(key = "tx-header") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = pageMargin)
                                .padding(top = 10.dp)
                        ) {
                            SectionHeader(title = "Transactions")
                            Spacer(modifier = Modifier.height(8.dp))
                            TransactionSearchField(
                                query = searchQuery,
                                onQueryChange = onQueryChange,
                                onFocusChange = onSearchFocus
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                    if (flat.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                hasPermission = hasSmsPermission,
                                searching = searchQuery.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = pageMargin)
                            )
                        }
                    } else {
                        items(items = flat, key = { it.id }) { tx ->
                            TransactionRow(
                                tx = tx,
                                onClick = { onOpenTransaction(tx.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = pageMargin)
                            )
                        }
                    }
                }
            }
            if (searchFocused && !showingReceipt) {
                InAppKeyboard(
                    startOnDigits = searchQuery.any { it.isDigit() } &&
                        searchQuery.none { it.isLetter() },
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
private fun BalanceSection(
    balance: Double?,
    alwaysShowBalance: Boolean,
    modifier: Modifier = Modifier
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
            drawPath(leaf, color = Accent.copy(alpha = 0.90f))
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
            drawPath(highlight, color = Accent.copy(alpha = 0.45f))
            drawCircle(
                color = SoftBlue.copy(alpha = 0.35f),
                radius = h * 0.38f,
                center = Offset(mirrorX(w * 0.06f), h * 0.12f)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = HomeMock.PillPadH,
                    // Extra end padding so amount/eye sit clear of the right swoosh.
                    end = HomeMock.PillPadH + 8.dp,
                    top = HomeMock.PillPadV,
                    bottom = HomeMock.PillPadV
                ),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(HomeMock.PillLabelGap)
        ) {
            Text(
                text = "Available balance",
                style = HomeType.caption,
                color = Mute
            )
            BalanceAmountRow(
                balance = balance,
                alwaysShow = alwaysShowBalance,
                amountStyle = HomeType.balance,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                showEyeToggle = true,
                eyeInCircle = true
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
    modifier: Modifier = Modifier
) {
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
            iconTint = CardWhite
        )
        QuickAction(
            icon = Icons.Outlined.Payments,
            label = "Pay",
            onClick = onDeposit,
            enabled = sendPayEnabled,
            face = ActionPay,
            iconTint = Accent
        )
        QuickAction(
            icon = Icons.Outlined.BarChart,
            label = "Metrics",
            onClick = onDetails,
            face = ActionMetrics,
            iconTint = Accent
        )
        QuickAction(
            icon = Icons.Outlined.ConfirmationNumber,
            label = "Passes",
            onClick = onTickets,
            face = ActionTickets,
            iconTint = Accent
        )
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

@Composable
private fun SectionHeader(
    title: String,
    trailing: String? = null,
    onOpen: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onOpen != null) Modifier.clickable(onClick = onOpen)
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = HomeType.section,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = HomeType.caption,
                color = Mute
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
        if (onOpen != null || trailing != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Mute,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun TransactionSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    InterceptSystemIme {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = HomeMock.SearchMinHeight)
                .bringIntoViewOnFocus(delayMs = 80L)
                .onFocusChanged { onFocusChange(it.isFocused) },
            singleLine = true,
            readOnly = true,
            textStyle = SheetInputStyle,
            shape = RoundedCornerShape(HomeMock.SearchRadius),
            placeholder = {
                Text("Name or number", style = HomeType.body, color = Mute)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Mute,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = Mute,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite,
                cursorColor = Ink
            )
        )
    }
}

@Composable
private fun FrequentContactsRow(
    contacts: List<SendContact>,
    onAdd: () -> Unit,
    onSelect: (SendContact) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(HomeMock.FrequentGap)) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(HomeMock.FrequentAvatar + 8.dp)
                    .clickable(onClick = onAdd)
            ) {
                Box(
                    modifier = Modifier
                        .size(HomeMock.FrequentAvatar)
                        .border(1.5.dp, Mute.copy(alpha = 0.45f), CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Add",
                        tint = Mute,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(HomeMock.FrequentLabelGap))
                Text("Add", style = HomeType.caption, color = Mute)
            }
        }
        items(contacts, key = { it.normalizedPhone }) { contact ->
            val name = contact.name ?: contact.normalizedPhone
            val seed = name.hashCode()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(HomeMock.FrequentAvatar + 8.dp)
                    .clickable { onSelect(contact) }
            ) {
                Box(
                    modifier = Modifier
                        .size(HomeMock.FrequentAvatar)
                        .clip(CircleShape)
                        .background(avatarPastel(seed)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = HomeType.rowTitle,
                        color = Ink
                    )
                }
                Spacer(modifier = Modifier.height(HomeMock.FrequentLabelGap))
                Text(
                    text = name,
                    style = HomeType.caption,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TransactionRow(
    tx: MpesaTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outgoing = tx.type.isOutgoing()
    val sign = if (outgoing) "−" else "+"
    val amountColor = if (outgoing) Debit else Income
    val title = tx.counterpartyName ?: tx.type.displayLabel()
    val seed = title.hashCode()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = HomeMock.TxRowV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(HomeMock.TxAvatar)
                .clip(CircleShape)
                .background(avatarPastel(seed)),
            contentAlignment = Alignment.Center
        ) {
            val initial = title
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "?"
            Text(text = initial, style = HomeType.rowTitle, color = Ink)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HomeType.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(tx.type.displayLabel())
                    val phone = tx.counterpartyPhone?.trim().orEmpty()
                    if (phone.isNotEmpty()) {
                        append(" · ")
                        append(phone)
                    }
                    append(" · ")
                    append(formatActivityTime(tx.timestampMillis))
                },
                style = HomeType.caption,
                color = Ink.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$sign${formatKes(tx.amount)}",
            style = HomeType.amount,
            color = amountColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun EmptyState(
    hasPermission: Boolean,
    searching: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.section + Space.block),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                searching -> "No matches"
                hasPermission -> "No activity yet"
                else -> "SMS permission off"
            },
            style = HomeType.rowTitle
        )
        Spacer(modifier = Modifier.height(Space.gap))
        Text(
            text = when {
                searching -> "Try a different name or number."
                hasPermission -> "Pull down to refresh and import M-Pesa SMS."
                else -> "Grant SMS access to import your inbox."
            },
            style = HomeType.body,
            color = Mute
        )
    }
}
