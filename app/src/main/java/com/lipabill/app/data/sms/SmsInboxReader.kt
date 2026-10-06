package com.lipabill.app.data.sms

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.lipabill.app.data.model.MpesaTransaction

/**
 * Reads historical M-Pesa SMS from the device inbox.
 * Only messages whose sender/header is MPESA (or M-PESA) are included.
 * Wallet receipts and Fuliza draws are queried separately, with no shared cap,
 * so every matching confirmation on the phone can be imported.
 * Does not upload inbox contents.
 */
class SmsInboxReader(private val context: Context) {

    data class RawSms(
        val address: String,
        val body: String,
        val dateMillis: Long
    )

    fun hasSmsPermission(): Boolean = context.hasMpesaSmsPermission()

    /** Every M-PESA wallet confirmation in the inbox. Fuliza draws are not included. */
    fun readMpesaMessages(): List<RawSms> = readInbox(
        extraSelection = "${Telephony.Sms.BODY} LIKE ? AND ${Telephony.Sms.BODY} LIKE ?",
        extraArgs = arrayOf("%Confirmed%", "%balance%"),
        acceptBody = MpesaSmsFilter::isMpesaWalletConfirmation
    )

    /** Every Fuliza draw confirmation in the inbox. */
    fun readFulizaMessages(): List<RawSms> = readInbox(
        extraSelection = "${Telephony.Sms.BODY} LIKE ? AND ${Telephony.Sms.BODY} LIKE ? AND ${Telephony.Sms.BODY} LIKE ?",
        extraArgs = arrayOf("%Confirmed%", "%Fuliza%", "%outstanding%"),
        acceptBody = MpesaSmsFilter::isFulizaConfirmation
    )

    fun parseAll(): List<MpesaTransaction> =
        (readMpesaMessages() + readFulizaMessages()).mapNotNull { raw ->
            MpesaSmsIngestion.acceptAndParse(raw.address, raw.body, raw.dateMillis)
        }

    private fun readInbox(
        extraSelection: String,
        extraArgs: Array<String>,
        acceptBody: (String) -> Boolean
    ): List<RawSms> {
        if (!hasSmsPermission()) return emptyList()
        val selection = "(${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?) AND $extraSelection"
        val selectionArgs = arrayOf("%MPESA%", "%M-PESA%", *extraArgs)
        val results = mutableListOf<RawSms>()
        val cursor: Cursor? = try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                INBOX_PROJECTION,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC"
            )
        } catch (_: SecurityException) {
            null
        }

        cursor?.use {
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                val address = it.getString(addressIdx).orEmpty()
                if (!MpesaSmsFilter.isMpesaSender(address)) continue
                val body = it.getString(bodyIdx).orEmpty()
                if (!acceptBody(body)) continue
                if (body.isBlank()) continue
                results.add(RawSms(address, body, it.getLong(dateIdx)))
            }
        }
        return results
    }

    companion object {
        val INBOX_PROJECTION = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
    }
}
