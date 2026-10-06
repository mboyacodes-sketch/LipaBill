package com.lipabill.app.data.sms

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.lipabill.app.data.model.MpesaTransaction

/**
 * Reads M-Pesa confirmations from the device inbox.
 * Wallet receipts and Fuliza draws come from one query, with no row cap.
 * A date bound limits a refresh to messages newer than the last scan.
 * Does not upload inbox contents.
 */
class SmsInboxReader(private val context: Context) {

    data class RawSms(
        val address: String,
        val body: String,
        val dateMillis: Long
    )

    fun hasSmsPermission(): Boolean = context.hasMpesaSmsPermission()

    /**
     * Confirmations whose sender is MPESA.
     * [newerThanMillis] keeps already-imported history out of the provider query.
     */
    fun readMessages(newerThanMillis: Long? = null): List<RawSms> = readInbox(
        extraSelection = CONFIRMED_SELECTION,
        extraArgs = CONFIRMED_ARGS,
        acceptBody = MpesaSmsFilter::isTransactionConfirmation,
        newerThanMillis = newerThanMillis
    )

    fun parseAll(newerThanMillis: Long? = null): List<MpesaTransaction> =
        readMessages(newerThanMillis).mapNotNull { raw ->
            MpesaSmsIngestion.acceptAndParse(raw.address, raw.body, raw.dateMillis)
        }

    private fun readInbox(
        extraSelection: String,
        extraArgs: Array<String>,
        acceptBody: (String) -> Boolean,
        newerThanMillis: Long?
    ): List<RawSms> {
        if (!hasSmsPermission()) return emptyList()
        val (selection, selectionArgs) = inboxQuery(extraSelection, extraArgs, newerThanMillis)
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
        /** Confirmed, and either a balance line or a Fuliza outstanding line. */
        private const val CONFIRMED_SELECTION =
            "${Telephony.Sms.BODY} LIKE ? AND (" +
                "${Telephony.Sms.BODY} LIKE ? OR (" +
                "${Telephony.Sms.BODY} LIKE ? AND ${Telephony.Sms.BODY} LIKE ?))"

        private val CONFIRMED_ARGS = arrayOf("%Confirmed%", "%balance%", "%Fuliza%", "%outstanding%")

        val INBOX_PROJECTION = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )

        internal fun inboxQuery(
            extraSelection: String,
            extraArgs: Array<String>,
            newerThanMillis: Long?
        ): Pair<String, Array<String>> {
            val selection = buildString {
                append("(${Telephony.Sms.ADDRESS} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?)")
                append(" AND ")
                append(extraSelection)
                if (newerThanMillis != null) {
                    append(" AND ")
                    append(Telephony.Sms.DATE)
                    append(" > ?")
                }
            }
            val args = ArrayList<String>(2 + extraArgs.size + 1)
            args += "%MPESA%"
            args += "%M-PESA%"
            args.addAll(extraArgs)
            if (newerThanMillis != null) args += newerThanMillis.toString()
            return selection to args.toTypedArray()
        }
    }
}
