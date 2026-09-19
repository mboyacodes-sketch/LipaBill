package com.lipabill.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lipabill.app.ussd.RepeatOutcome

@Entity(tableName = "repeat_attempts")
data class RepeatAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtMillis: Long,
    val sourceTransactionId: Long,
    val sourceTransactionCode: String,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val amount: Double?,
    val outcome: RepeatOutcome,
    val detail: String? = null
)
