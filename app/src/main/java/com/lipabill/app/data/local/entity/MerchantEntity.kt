package com.lipabill.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lipabill.app.data.model.TransactionType

/**
 * Maps Lipa na M-Pesa identifiers (paybill / till / Pochi phone) to SMS business names
 * so users can later search by name and recover the number + last account.
 */
@Entity(
    tableName = "merchants",
    indices = [
        Index(value = ["type", "identifier"], unique = true),
        Index(value = ["normalizedName"])
    ]
)
data class MerchantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    /** Business number (paybill), till number, or Pochi phone. Digits only. */
    val identifier: String,
    /** Last account / shop code used for this paybill (null for till / Pochi). */
    val accountHint: String?,
    val displayName: String,
    val normalizedName: String,
    val lastUsedMillis: Long,
    val useCount: Int = 1
)

/**
 * Short-lived bridge: what the user typed at dial time → incoming SMS with only a name.
 */
@Entity(tableName = "pending_merchant_payments")
data class PendingMerchantPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val identifier: String,
    val accountCode: String?,
    val amount: Double,
    val createdAtMillis: Long,
    val consumed: Boolean = false
)
