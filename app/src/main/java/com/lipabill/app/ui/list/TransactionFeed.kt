package com.lipabill.app.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType
import com.lipabill.app.ui.components.CounterpartyAvatar
import com.lipabill.app.ui.components.txAmountLook
import com.lipabill.app.ui.privacy.recordingPrivacyCover
import com.lipabill.app.ui.sheet.EditableSheetField
import com.lipabill.app.ui.sheet.SheetTextEditor
import com.lipabill.app.ui.theme.CardWhite
import com.lipabill.app.ui.theme.Debit
import com.lipabill.app.ui.theme.HomeType
import com.lipabill.app.ui.theme.Income
import com.lipabill.app.ui.theme.Ink
import com.lipabill.app.ui.theme.Mute
import com.lipabill.app.ui.theme.OnPastel
import com.lipabill.app.ui.theme.Space
import com.lipabill.app.ui.theme.asPillLabel
import com.lipabill.app.ui.util.InterceptSystemIme
import com.lipabill.app.ui.util.categoryDisc
import com.lipabill.app.ui.util.categoryInk
import com.lipabill.app.ui.util.categoryWash
import com.lipabill.app.ui.util.chipLabel
import com.lipabill.app.ui.util.displayLabel
import com.lipabill.app.ui.util.formatActivityTime
import com.lipabill.app.ui.util.formatKes
import com.lipabill.app.viewmodel.DayGroup
import com.lipabill.app.viewmodel.WalletAccount

@Composable
internal fun SectionHeader(
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
internal fun TransactionSearchField(
    editor: SheetTextEditor,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    InterceptSystemIme {
        EditableSheetField(
            editor = editor,
            onText = onQueryChange,
            placeholder = "Name or number",
            modifier = modifier.heightIn(min = HomeMock.SearchMinHeight),
            onFocused = onFocusChange,
            shape = RoundedCornerShape(HomeMock.SearchRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite,
                cursorColor = Ink,
                focusedTextColor = Ink,
                unfocusedTextColor = Ink
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Mute,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = if (editor.text.isEmpty()) {
                null
            } else {
                {
                    IconButton(onClick = {
                        editor.overwrite("")
                        onQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = Mute,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        )
    }
}

@Composable
internal fun TransactionRow(
    tx: MpesaTransaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amount = txAmountLook(tx)
    val fuliza = tx.type == TransactionType.FULIZA
    val amountColor = if (amount.outgoingStyle) Debit else Income
    val named = !tx.counterpartyName.isNullOrBlank()
    val title = tx.counterpartyName ?: tx.type.displayLabel()
    val categoryInk = tx.type.categoryInk()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = HomeMock.TxRowV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CounterpartyAvatar(
            name = title,
            fill = tx.type.categoryDisc(),
            contentColor = OnPastel,
            size = HomeMock.TxAvatar,
            privacyCover = named
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = HomeType.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.recordingPrivacyCover(named)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tx.source.label,
                    style = HomeType.caption,
                    color = Mute,
                    maxLines = 1
                )
                Text(
                    text = " · ${tx.type.displayLabel()}",
                    style = HomeType.caption,
                    color = categoryInk,
                    maxLines = 1
                )
                if (fuliza && tx.cost != null) {
                    Text(
                        text = " · fee ${formatKes(tx.cost)}",
                        style = HomeType.caption,
                        color = Ink.copy(alpha = 0.62f),
                        maxLines = 1
                    )
                }
                val phone = tx.counterpartyPhone?.trim().orEmpty()
                if (phone.isNotEmpty()) {
                    Text(
                        text = " · $phone",
                        style = HomeType.caption,
                        color = Ink.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .recordingPrivacyCover()
                    )
                }
                Text(
                    text = " · ${formatActivityTime(tx.timestampMillis)}",
                    style = HomeType.caption,
                    color = Ink.copy(alpha = 0.62f),
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${amount.prefix}${formatKes(tx.amount)}",
            style = HomeType.amount,
            color = amountColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

internal fun LazyListScope.transactionFeed(
    account: WalletAccount,
    grouped: List<DayGroup>,
    hasSmsPermission: Boolean,
    searchQuery: String,
    typeFilter: TransactionType?,
    availableTypes: List<TransactionType>,
    onTypeFilter: (TransactionType?) -> Unit,
    sourceFilter: MoneySource?,
    searchEditor: SheetTextEditor,
    onQueryChange: (String) -> Unit,
    searchOpen: Boolean,
    onSearchFocus: (Boolean) -> Unit,
    onRequestSms: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    pageMargin: Dp,
    sheetColor: Color?,
    headerShape: Shape? = null,
    onSeeAll: (() -> Unit)? = null
) {
    fun Modifier.sheet(shape: Shape? = null): Modifier {
        val painted = when {
            sheetColor == null -> this
            shape != null -> background(sheetColor, shape)
            else -> background(sheetColor)
        }
        return painted.padding(horizontal = pageMargin)
    }

    item(key = "tx-header") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sheet(headerShape)
                .padding(top = 10.dp)
        ) {
            SectionHeader(
                title = account.ledgerTitle,
                trailing = if (onSeeAll != null) "See all" else null,
                onOpen = onSeeAll
            )
            if (availableTypes.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                CategoryFilterRow(
                    types = availableTypes,
                    selected = typeFilter,
                    onSelect = onTypeFilter
                )
            }
            if (searchOpen) {
                Spacer(modifier = Modifier.height(8.dp))
                TransactionSearchField(
                    editor = searchEditor,
                    onQueryChange = onQueryChange,
                    onFocusChange = onSearchFocus
                )
                Spacer(modifier = Modifier.height(6.dp))
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
    if (!hasSmsPermission && searchQuery.isBlank() && grouped.isNotEmpty()) {
        item(key = "sms-prompt") {
            SmsAccessPrompt(
                compact = true,
                onAllow = onRequestSms,
                modifier = Modifier.fillMaxWidth().sheet()
            )
        }
    }
    if (grouped.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                hasPermission = hasSmsPermission,
                searching = searchQuery.isNotBlank() ||
                    typeFilter != null ||
                    sourceFilter != null,
                filterLabel = when {
                    typeFilter != null && sourceFilter != null ->
                        "${sourceFilter.label} · ${typeFilter.chipLabel()}"
                    typeFilter != null -> typeFilter.chipLabel()
                    sourceFilter != null -> sourceFilter.label
                    else -> null
                },
                filterHint = when {
                    typeFilter != null ->
                        "Tap ${typeFilter.chipLabel()} again to show everything."
                    sourceFilter != null ->
                        "Tap ${sourceFilter.label} again to show everything."
                    else -> null
                },
                account = account,
                onAllowSms = onRequestSms,
                modifier = Modifier.fillMaxWidth().sheet()
            )
        }
    } else {
        grouped.forEach { day ->
            item(key = "day-${day.date}") {
                Text(
                    text = day.label,
                    style = HomeType.label,
                    color = Mute,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sheet()
                        .padding(top = 14.dp, bottom = 2.dp)
                )
            }
            items(items = day.items, key = { it.id }) { tx ->
                TransactionRow(
                    tx = tx,
                    onClick = { onOpenTransaction(tx.id) },
                    modifier = Modifier.fillMaxWidth().sheet()
                )
            }
        }
    }
}

@Composable
internal fun CategoryFilterRow(
    types: List<TransactionType>,
    selected: TransactionType?,
    onSelect: (TransactionType?) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        types.forEach { type ->
            val selectedType = type == selected
            val ink = type.categoryInk()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selectedType) type.categoryWash() else Color.Transparent)
                    .clickable { onSelect(if (selectedType) null else type) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = if (selectedType) 1f else 0.55f))
                )
                Text(
                    text = type.chipLabel(),
                    style = HomeType.caption.asPillLabel(),
                    color = if (selectedType) ink else Mute,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
internal fun EmptyState(
    hasPermission: Boolean,
    searching: Boolean,
    filterLabel: String?,
    filterHint: String?,
    account: WalletAccount,
    onAllowSms: () -> Unit,
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
                filterLabel != null && searching -> "Nothing in $filterLabel"
                searching -> "No matches"
                hasPermission -> account.emptyHistoryTitle
                else -> "Where it went shows up here"
            },
            style = HomeType.rowTitle
        )
        Spacer(modifier = Modifier.height(Space.gap))
        Text(
            text = when {
                filterHint != null -> filterHint
                searching -> "Try a different name or number."
                hasPermission -> account.emptyHistoryDetail
                else ->
                    "Allow SMS so LipaBill can read confirmation texts and show " +
                        "where your money went. Encrypted on this phone. " +
                        "It does not send SMS or read OTPs."
            },
            style = HomeType.body,
            color = Mute,
            textAlign = TextAlign.Center
        )
        if (!hasPermission && !searching) {
            Spacer(modifier = Modifier.height(Space.block))
            Button(onClick = onAllowSms) {
                Text("Allow SMS to see where it went")
            }
        }
    }
}

@Composable
internal fun SmsAccessPrompt(
    compact: Boolean,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = if (compact) Space.gap else Space.block),
        horizontalAlignment = Alignment.Start
    ) {
            Text(
                text = "Confirmation SMS stay out of the picture until SMS is allowed. " +
                    "They are read on this phone only, encrypted, and never sent. OTPs are not read.",
                style = HomeType.body,
                color = Mute
            )
        TextButton(onClick = onAllow) {
            Text("Allow SMS to see where it went")
        }
    }
}
