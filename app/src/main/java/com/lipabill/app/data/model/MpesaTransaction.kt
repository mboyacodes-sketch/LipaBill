package com.lipabill.app.data.model

/**
 * Structured representation of a parsed M-Pesa confirmation SMS.
 * Pure data — no Android framework types.
 *
 * @param id Room row id when loaded from DB (0 if not yet persisted).
 */
data class MpesaTransaction(
    val id: Long = 0,
    val code: String,
    val type: TransactionType,
    val amount: Double?,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val timestampMillis: Long,
    val balance: Double?,
    val cost: Double?,
    val rawBody: String
)
