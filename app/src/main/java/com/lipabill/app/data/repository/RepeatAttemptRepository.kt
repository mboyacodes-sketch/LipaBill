package com.lipabill.app.data.repository

import com.lipabill.app.data.local.dao.RepeatAttemptDao
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.ussd.RepeatOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RepeatAttemptRepository(
    private val dao: RepeatAttemptDao
) {

    fun observeRecent(limit: Int = 50): Flow<List<RepeatAttemptEntity>> =
        dao.observeRecent(limit)

    suspend fun beginAttempt(tx: MpesaTransaction, amountOverride: Double? = null): Long =
        withContext(Dispatchers.IO) {
            dao.insert(
                RepeatAttemptEntity(
                    createdAtMillis = System.currentTimeMillis(),
                    sourceTransactionId = tx.id,
                    sourceTransactionCode = tx.code,
                    counterpartyName = tx.counterpartyName,
                    counterpartyPhone = tx.counterpartyPhone,
                    amount = amountOverride ?: tx.amount,
                    outcome = RepeatOutcome.USER_CANCELLED,
                    detail = "pending"
                )
            )
        }

    suspend fun getById(id: Long): RepeatAttemptEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    suspend fun finish(id: Long, outcome: RepeatOutcome, detail: String? = null) =
        withContext(Dispatchers.IO) {
            dao.updateOutcome(id, outcome, detail)
        }
}
