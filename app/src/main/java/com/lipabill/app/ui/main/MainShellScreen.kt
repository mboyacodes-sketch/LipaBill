package com.lipabill.app.ui.main

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lipabill.app.data.repository.SendContact
import com.lipabill.app.ui.list.TransactionListScreen
import com.lipabill.app.ui.util.imeAndNavBarsPadding
import com.lipabill.app.viewmodel.TransactionListViewModel

@Composable
fun MainShellScreen(
    listVm: TransactionListViewModel,
    onRepeatTransaction: (Long) -> Unit = {},
    onOpenSend: () -> Unit = {},
    onOpenSendTo: (SendContact) -> Unit = {},
    onOpenPay: () -> Unit = {},
    onOpenMetrics: () -> Unit = {},
    onOpenTickets: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        TransactionListScreen(
            viewModel = listVm,
            onRepeatTransaction = onRepeatTransaction,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imeAndNavBarsPadding(),
            onSend = onOpenSend,
            onSendTo = onOpenSendTo,
            onReceive = onOpenPay,
            onExchange = onOpenMetrics,
            onTickets = onOpenTickets,
            onRescan = { listVm.rescanInbox() },
            onOpenSettings = onOpenSettings
        )
    }
}
