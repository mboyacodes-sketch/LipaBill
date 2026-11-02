package com.lipabill.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.model.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["source", "code"], unique = true),
        Index(value = ["timestampMillis"]),
        Index(value = ["source"])
    ]
)
/** One money confirmation from any enabled wallet or bank. */
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val type: TransactionType,
    val amount: Double?,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val timestampMillis: Long,
    val balance: Double?,
    val cost: Double?,
    val rawBody: String,
    val source: MoneySource = MoneySource.MPESA
) {
    fun toDomain(): MpesaTransaction = MpesaTransaction(
        id = id,
        code = code,
        type = type,
        amount = amount,
        counterpartyName = counterpartyName,
        counterpartyPhone = counterpartyPhone,
        timestampMillis = timestampMillis,
        balance = balance,
        cost = cost,
        rawBody = rawBody,
        source = source
    )

    /** List/home rows — skip large SMS bodies to cut memory. */
    fun toListItem(): MpesaTransaction = MpesaTransaction(
        id = id,
        code = code,
        type = type,
        amount = amount,
        counterpartyName = counterpartyName,
        counterpartyPhone = counterpartyPhone,
        timestampMillis = timestampMillis,
        balance = balance,
        cost = cost,
        rawBody = "",
        source = source
    )

    companion object {
        fun fromDomain(tx: MpesaTransaction): TransactionEntity = TransactionEntity(
            // Omit auto id on insert so Room generates it; dedupe is by unique [source, code]
            code = tx.code,
            type = tx.type,
            amount = tx.amount,
            counterpartyName = tx.counterpartyName,
            counterpartyPhone = tx.counterpartyPhone,
            timestampMillis = tx.timestampMillis,
            balance = tx.balance,
            cost = tx.cost,
            rawBody = tx.rawBody,
            source = tx.source
        )
    }
}
