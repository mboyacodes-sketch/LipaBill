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
            MpesaSmsFilter.isTransactionConfirmation(body)
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
    ): MpesaTransaction? {
        val source = MoneySource.fromSender(address, region) ?: return null
        val text = body?.takeIf { it.isNotBlank() } ?: return null
        if (!shouldAcceptBody(source, text, region, enabled)) return null
        return runCatching {
            when {
                source == MoneySource.MPESA && region == AppRegion.KENYA ->
                    MpesaSmsParser.parse(text, timestampMillis).copy(source = MoneySource.MPESA)
                else ->
                    KenyanConfirmationParser.parse(text, timestampMillis, source)
            }
        }.getOrNull()
    }

    fun sourceKey(tx: MpesaTransaction): String = "${tx.source.name}:${tx.code}"
}
