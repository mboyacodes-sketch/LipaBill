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
    /**
     * Full confirmation SMS text.
     * Used for share, re-parse, and merchant account enrichment.
     * TODO(privacy): Evaluate whether rawBody can be minimized in a future production privacy pass
     * (keep structured fields only if share/reparse can work without it).
     * Must not be logged or uploaded by app code.
     */
    val rawBody: String
)
