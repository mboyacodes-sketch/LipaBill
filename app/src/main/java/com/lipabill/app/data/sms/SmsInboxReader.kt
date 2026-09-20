package com.lipabill.app.data.sms

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.data.parser.MpesaSmsParser

/**
 * Reads historical M-Pesa SMS from the device inbox.
 * Only messages whose sender/header is MPESA (or M-PESA) are included.
 */
class SmsInboxReader(private val context: Context) {

    data class RawSms(
        val address: String,
        val body: String,
        val dateMillis: Long
    )

    fun readMpesaMessages(maxMessages: Int = 300): List<RawSms> {
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val selection = "${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?"
        val selectionArgs = arrayOf("%MPESA%", "%M-PESA%")
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        val results = mutableListOf<RawSms>()
        val cursor: Cursor? = try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )
        } catch (_: SecurityException) {
            null
        }

        cursor?.use {
            val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext() && results.size < maxMessages) {
                val address = it.getString(addressIdx).orEmpty()
                if (!MpesaSmsFilter.isMpesaSender(address)) continue
                val body = it.getString(bodyIdx).orEmpty()
                if (!MpesaSmsFilter.isTransactionConfirmation(body)) continue
                val date = it.getLong(dateIdx)
                if (body.isNotBlank()) {
                    results.add(RawSms(address, body, date))
                }
            }
        }
        return results
    }

    fun parseAll(maxMessages: Int = 300): List<MpesaTransaction> =
        readMpesaMessages(maxMessages).map { MpesaSmsParser.parse(it.body, it.dateMillis) }
}
