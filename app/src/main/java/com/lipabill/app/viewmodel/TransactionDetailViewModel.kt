package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.MpesaTransaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TransactionDetailViewModel(
    application: Application,
    transactionId: Long
) : AndroidViewModel(application) {

    private val repo = (application as LipaBillApp).repository

    val transaction: StateFlow<MpesaTransaction?> = repo.observeById(transactionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
