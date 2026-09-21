package com.lipabill.app.ui.list

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.ui.components.BalanceAmountRow
import com.lipabill.app.ui.detail.TransactionReceiptPopup
import com.lipabill.app.ui.sheet.SheetInputStyle
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Hairline
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Accent
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.SoftBlue
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatActivityTime
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.ui.util.isOutgoing
import com.lipabill.app.viewmodel.TransactionListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf

/**
 * Clean off-white home: greeting, balance, actions, frequent contacts, transactions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel,
    onOpenTransaction: (Long) -> Unit,
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
    val app = LocalContext.current.applicationContext as LipaBillApp
    val alwaysShowBalance by app.alwaysShowBalance.collectAsStateWithLifecycle()
    var receiptTxId by remember { mutableStateOf<Long?>(null) }

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

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.isScanning,
            onRefresh = onRescan,
            modifier = Modifier
                .fillMaxSize()
                // Strong in-composition bokeh so list text behind the ticket is unreadable.
                .then(if (showingReceipt) Modifier.blur(14.dp) else Modifier)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardWhite),
                contentPadding = PaddingValues(bottom = Space.section)
            ) {
                item(key = "hero") {
                    TopWalletHero(
                        onRescan = onRescan,
                        onOpenSettings = onOpenSettings
                    )
                }
                item(key = "balance") {
                    Spacer(modifier = Modifier.height(Space.block))
                    BalanceSection(
                        balance = state.latestBalance,
                        alwaysShowBalance = alwaysShowBalance,
                        modifier = Modifier.padding(horizontal = Space.page)
                    )
                    Spacer(modifier = Modifier.height(Space.block))
                }
                item(key = "actions") {
                    QuickActionsRow(
                        onSend = onSend,
                        onDeposit = onReceive,
                        onDetails = onExchange,
                        onTickets = onTickets,
                        modifier = Modifier.padding(horizontal = Space.page)
                    )
                    Spacer(modifier = Modifier.height(Space.block))
                }
                if (frequent.isNotEmpty()) {
                    item(key = "frequent") {
                        Column(modifier = Modifier.padding(horizontal = Space.page)) {
                            SectionHeader(title = "Frequent", onOpen = onSend)
                            Spacer(modifier = Modifier.height(Space.block))
                            FrequentContactsRow(
                                contacts = frequent,
                                onAdd = onSend,
                                onSelect = onSendTo
                            )
                        }
                        Spacer(modifier = Modifier.height(Space.block))
                    }
                }

                item(key = "tx-header") {
                    Column(modifier = Modifier.padding(horizontal = Space.page)) {
                        SectionHeader(title = "Transactions")
                        Spacer(modifier = Modifier.height(Space.gap))
                        TransactionSearchField(
                            query = state.searchQuery,
                            onQueryChange = viewModel::setSearchQuery
                        )
                        Spacer(modifier = Modifier.height(Space.gap))
                    }
                }

            if (flat.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        hasPermission = state.hasSmsPermission,
                        searching = state.searchQuery.isNotBlank(),
                        modifier = Modifier.padding(horizontal = Space.page)
                    )
                }
            } else {
                items(items = flat, key = { it.id }) { tx ->
                    TransactionRow(
                        tx = tx,
                        onClick = { receiptTxId = tx.id },
                        modifier = Modifier.padding(horizontal = Space.page)
                    )
                }
            }
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
private fun TopWalletHero(
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val dock = Color(0xFFE2E6EC)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(dock)
            .statusBarsPadding()
    ) {
        HomeHeader(
            onRescan = onRescan,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.padding(horizontal = Space.page, vertical = Space.hero)
        )
        Spacer(modifier = Modifier.height(Space.block))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            WalletBanner(
                modifier = Modifier.fillMaxWidth(0.79f)
            )
        }
    }
}

@Composable
private fun HomeHeader(
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        HeaderIconButton(
            icon = Icons.Outlined.Refresh,
            contentDescription = "Rescan SMS",
            onClick = onRescan
        )
        Spacer(modifier = Modifier.width(Space.tight))
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
            .size(40.dp)
            .clip(CircleShape)
            .background(CardWhite.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Accent,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun WalletBanner(modifier: Modifier = Modifier) {
    val cardFace = Color(0xFFF7F8FA)
    val cardFaceDeep = Color(0xFFE4E7EC)
    val cardFaceLight = Color(0xFFFFFFFF)
    val inkMuted = Color(0xFF6B7280)
    val pocketShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fullCardHeight = maxWidth / 1.586f
        val visibleHeight = fullCardHeight * 0.547f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(visibleHeight)
                .shadow(
                    elevation = 12.dp,
                    shape = pocketShape,
                    spotColor = Color.Black.copy(alpha = 0.2f),
                    ambientColor = Color.Black.copy(alpha = 0.08f)
                )
                .clip(pocketShape)
        ) {
            // Full card — bottom half clipped away by the parent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fullCardHeight)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(cardFaceLight, cardFace, cardFaceDeep),
                            start = Offset(0f, 0f),
                            end = Offset(800f, 600f)
                        )
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.65f),
                        radius = size.minDimension * 0.55f,
                        center = Offset(size.width * 0.88f, size.height * 0.02f)
                    )
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.04f),
                        radius = size.minDimension * 0.55f,
                        center = Offset(size.width * 0.12f, size.height * 0.9f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.cardH, vertical = Space.card)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "M-PESA",
                            style = HomeType.label.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = Ink
                        )
                        Icon(
                            imageVector = Icons.Outlined.Wifi,
                            contentDescription = null,
                            tint = inkMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(90f)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "Safaricom",
                            style = HomeType.caption.copy(fontWeight = FontWeight.Medium),
                            color = inkMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceSection(
    balance: Double?,
    alwaysShowBalance: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CardWhite)
            .padding(horizontal = Space.card, vertical = Space.cardH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Available balance",
            style = HomeType.caption,
            color = Mute
        )
        Spacer(modifier = Modifier.height(Space.gap))
        BalanceAmountRow(
            balance = balance,
            alwaysShow = alwaysShowBalance,
            amountStyle = HomeType.balance,
            horizontalArrangement = Arrangement.Center,
            eyeTint = Mute
        )
    }
}

@Composable
private fun QuickActionsRow(
    onSend: () -> Unit,
    onDeposit: () -> Unit,
    onDetails: () -> Unit,
    onTickets: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        QuickAction(Icons.Outlined.NorthEast, "Send", onSend)
        QuickAction(Icons.Outlined.Payments, "Pay", onDeposit)
        QuickAction(Icons.Outlined.Wallet, "Metrics", onDetails)
        QuickAction(Icons.Outlined.ConfirmationNumber, "Tickets", onTickets)
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.gap),
        modifier = Modifier.width(56.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
                .clip(RoundedCornerShape(16.dp))
                .background(CardWhite)
                .border(1.dp, Hairline, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Accent,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = HomeType.label,
            color = Ink,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionHeader(title: String, onOpen: (() -> Unit)? = null) {
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
        if (onOpen != null) {
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
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = SheetInputStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        shape = RoundedCornerShape(14.dp),
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
            focusedBorderColor = Hairline,
            unfocusedBorderColor = Hairline,
            focusedContainerColor = Color(0xFFF7F8FA),
            unfocusedContainerColor = Color(0xFFF7F8FA),
            cursorColor = Ink
        )
    )
}

@Composable
private fun FrequentContactsRow(
    contacts: List<SendContact>,
    onAdd: () -> Unit,
    onSelect: (SendContact) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.chip)) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable(onClick = onAdd)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .border(1.dp, Mute, CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add", tint = Mute)
                }
                Spacer(modifier = Modifier.height(Space.gap))
                Text("Add", style = HomeType.caption, color = Mute)
            }
        }
        items(contacts, key = { it.normalizedPhone }) { contact ->
            val name = contact.name ?: contact.normalizedPhone
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable { onSelect(contact) }
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SoftBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        style = HomeType.rowTitle,
                        color = Ink
                    )
                }
                Spacer(modifier = Modifier.height(Space.gap))
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
    val amountColor = if (outgoing) Ink else Income

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.row),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(SoftBlue),
            contentAlignment = Alignment.Center
        ) {
            val initial = (tx.counterpartyName ?: tx.type.displayLabel())
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString()
                ?: "?"
            Text(text = initial, style = HomeType.rowTitle, color = Ink)
        }
        Spacer(modifier = Modifier.width(Space.block))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.counterpartyName ?: tx.type.displayLabel(),
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
                color = Mute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "$sign${formatKes(tx.amount)}",
            style = HomeType.amount,
            color = amountColor
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
                hasPermission -> "Tap refresh in the header to import M-Pesa SMS."
                else -> "Grant SMS access to import your inbox."
            },
            style = HomeType.body,
            color = Mute
        )
    }
}
