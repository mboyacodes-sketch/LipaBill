package com.lipabill.app.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.lipabill.app.data.model.MpesaTransaction

/**
 * Reads historical M-Pesa SMS from the device inbox.
 * Only messages whose sender/header is MPESA (or M-PESA) are included.
 * Bound by [DEFAULT_MAX_MESSAGES]; does not upload inbox contents.
 */
class SmsInboxReader(private val context: Context) {

    data class RawSms(
        val address: String,
        val body: String,
        val dateMillis: Long
    )

    fun hasSmsPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS)
        val receive = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS)
        return read == PackageManager.PERMISSION_GRANTED &&
            receive == PackageManager.PERMISSION_GRANTED
    }

    fun readMpesaMessages(maxMessages: Int = DEFAULT_MAX_MESSAGES): List<RawSms> {
        if (!hasSmsPermission()) return emptyList()
        val limit = maxMessages.coerceAtMost(DEFAULT_MAX_MESSAGES).coerceAtLeast(0)
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
            while (it.moveToNext() && results.size < limit) {
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

    fun parseAll(maxMessages: Int = DEFAULT_MAX_MESSAGES): List<MpesaTransaction> =
        readMpesaMessages(maxMessages).mapNotNull { raw ->
            MpesaSmsIngestion.acceptAndParse(raw.address, raw.body, raw.dateMillis)
        }

    companion object {
        /** Hard cap for historical backfill — do not raise without a privacy review. */
        const val DEFAULT_MAX_MESSAGES = 300

        val INBOX_PROJECTION = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
    }
}
