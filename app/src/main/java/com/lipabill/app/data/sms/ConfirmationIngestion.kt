package com.lipabill.app.data.sms

import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.parser.KenyanConfirmationParser
import com.lipabill.app.data.parser.MpesaSmsParser

/**
 * SMS → ledger gate for every money confirmation we know how to read
 * in the selected [AppRegion]. Kenya M-Pesa keeps the Confirmed + balance
 * parser. Other rails use [KenyanConfirmationParser]. Does not dial or pay.
 */
object ConfirmationIngestion {

    /** Outcome of trying to turn one SMS into a ledger row (no address/body retained). */
    sealed class Outcome {
        data class Matched(val tx: MpesaTransaction) : Outcome()
        data object Skipped : Outcome()
        data class ParseFailed(val error: Throwable) : Outcome()
    }

    fun shouldAccept(
        address: String?,
        body: String?,
        region: AppRegion = AppRegion.DEFAULT,
        enabled: Set<MoneySource> = region.sources
    ): Boolean {
        val source = MoneySource.fromSender(address, region) ?: return false
        return shouldAcceptBody(source, body, region, enabled)
    }

    fun shouldAcceptBody(
        source: MoneySource,
        body: String?,
        region: AppRegion = AppRegion.DEFAULT,
        enabled: Set<MoneySource> = region.sources
    ): Boolean {
        if (source !in region.sources || source !in enabled) return false
        return isConfirmationBody(source, body, region)
    }

    private fun isConfirmationBody(
        source: MoneySource,
        body: String?,
        region: AppRegion
    ): Boolean = when {
        source == MoneySource.MPESA && region == AppRegion.KENYA ->
            MpesaSmsFilter.isTransactionConfirmation(body) ||
                KenyanConfirmationParser.isLipaNaKcbMerchantSms(body.orEmpty())
        else ->
            KenyanConfirmationFilter.isMoneyConfirmation(body, region)
    }

    /** True when [body] is a money confirmation in any supported country format. */
    fun looksLikeStoredConfirmation(source: MoneySource, body: String?): Boolean {
        if (source == MoneySource.MPESA && MpesaSmsFilter.isTransactionConfirmation(body)) {
            return true
        }
        return AppRegion.entries.any { KenyanConfirmationFilter.isMoneyConfirmation(body, it) }
    }

    fun acceptAndParse(
        address: String?,
        body: String?,
        timestampMillis: Long,
        region: AppRegion = AppRegion.DEFAULT,
        enabled: Set<MoneySource> = region.sources
    ): MpesaTransaction? = when (
        val outcome = acceptOutcome(address, body, timestampMillis, region, enabled)
    ) {
        is Outcome.Matched -> outcome.tx
        else -> null
    }

    fun acceptOutcome(
        address: String?,
        body: String?,
        timestampMillis: Long,
        region: AppRegion = AppRegion.DEFAULT,
        enabled: Set<MoneySource> = region.sources
    ): Outcome {
        val source = MoneySource.fromSender(address, region) ?: return Outcome.Skipped
        val text = body?.takeIf { it.isNotBlank() } ?: return Outcome.Skipped
        if (!shouldAcceptBody(source, text, region, enabled)) return Outcome.Skipped
        return runCatching {
            when {
                source == MoneySource.MPESA && region == AppRegion.KENYA -> {
                    if (MpesaSmsFilter.isTransactionConfirmation(text)) {
                        MpesaSmsParser.parse(text, timestampMillis).copy(source = MoneySource.MPESA)
                    } else {
                        // Lipa-na-KCB receipts omit Confirmed + balance; same Kenyan parser.
                        KenyanConfirmationParser.parse(text, timestampMillis, MoneySource.MPESA)
                            ?: error("confirmation_unparsed")
                    }
                }
                else ->
                    KenyanConfirmationParser.parse(text, timestampMillis, source)
                        ?: error("confirmation_unparsed")
            }
        }.fold(
            onSuccess = { Outcome.Matched(it) },
            onFailure = { Outcome.ParseFailed(it) }
        )
    }

    fun sourceKey(tx: MpesaTransaction): String = "${tx.source.name}:${tx.code}"

    /** True when any receipt id in [body] is already stored under any rail. */
    fun sharesKnownReceipt(body: String, knownSourceCodes: Set<String>): Boolean {
        if (knownSourceCodes.isEmpty()) return false
        val codes = KenyanConfirmationParser.allReceiptCodes(body)
        if (codes.isEmpty()) return false
        return knownSourceCodes.any { key ->
            val code = key.substringAfter(':', missingDelimiterValue = "")
            code.isNotEmpty() && code in codes
        }
    }

    /**
     * Lipa-na-KCB sends a merchant receipt and a bank settlement twin.
     * Keep the merchant row; drop suspense / pay-to-biller twins that share a ref.
     */
    fun dedupeLipaNaKcbTwins(rows: List<MpesaTransaction>): List<MpesaTransaction> {
        if (rows.size <= 1) return rows
        val codesByIndex = rows.map { KenyanConfirmationParser.allReceiptCodes(it.rawBody) + it.code }
        val twinIndexes = rows.indices.filter { KenyanConfirmationParser.isSettlementTwinSms(rows[it].rawBody) }
        val merchantIndexes =
            rows.indices.filter { KenyanConfirmationParser.isLipaNaKcbMerchantSms(rows[it].rawBody) }
        if (twinIndexes.isEmpty() || merchantIndexes.isEmpty()) return rows
        val drop = mutableSetOf<Int>()
        for (t in twinIndexes) {
            for (m in merchantIndexes) {
                if (codesByIndex[t].any { it in codesByIndex[m] }) {
                    drop += t
                    break
                }
            }
        }
        if (drop.isEmpty()) return rows
        return rows.filterIndexed { index, _ -> index !in drop }
    }
}
