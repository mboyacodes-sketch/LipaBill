package com.lipabill.app.data.sms

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource

/**
 * Reads money confirmation SMS from the device inbox for the selected region.
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
     * Confirmations from known money senders in [region].
     * [newerThanMillis] keeps already-imported history out of the provider query.
     */
    fun readMessages(
        newerThanMillis: Long? = null,
        region: AppRegion = AppRegion.DEFAULT,
        enabled: Set<MoneySource> = region.sources
    ): List<RawSms> {
        val (extraSelection, extraArgs) = bodyQuery(region)
        return readInbox(
            extraSelection = extraSelection,
            extraArgs = extraArgs,
            newerThanMillis = newerThanMillis,
            region = region,
            enabled = enabled.ifEmpty { region.sources }
        )
    }

    private fun readInbox(
        extraSelection: String,
        extraArgs: Array<String>,
        newerThanMillis: Long?,
        region: AppRegion,
        enabled: Set<MoneySource>
    ): List<RawSms> {
        if (!hasSmsPermission()) return emptyList()
        val addressLikes = enabled.flatMap { it.addressLikes }.distinct()
            .ifEmpty { region.inboxAddressLikes }
        val (selection, selectionArgs) = inboxQuery(
            extraSelection = extraSelection,
            extraArgs = extraArgs,
            newerThanMillis = newerThanMillis,
            addressLikes = addressLikes
        )
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
                if (MoneySource.fromSender(address, region) !in enabled) continue
                val body = it.getString(bodyIdx).orEmpty()
                if (body.isBlank()) continue
                if (!ConfirmationIngestion.shouldAccept(address, body, region, enabled)) continue
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

        internal fun bodyQuery(region: AppRegion): Pair<String, Array<String>> {
            val likes = region.bodyAmountLikes + listOf("%Confirmed%", "%credited%")
            val selection = likes.joinToString(
                separator = " OR ",
                prefix = "(",
                postfix = ")"
            ) { _ -> "${Telephony.Sms.BODY} LIKE ?" }
            return selection to likes.toTypedArray()
        }

        internal fun inboxQuery(
            extraSelection: String,
            extraArgs: Array<String>,
            newerThanMillis: Long?,
            addressLikes: List<String> = AppRegion.DEFAULT.inboxAddressLikes
        ): Pair<String, Array<String>> {
            val likes = addressLikes.ifEmpty { AppRegion.DEFAULT.inboxAddressLikes }
            val selection = buildString {
                append(likes.joinToString(separator = " OR ", prefix = "(", postfix = ")") { _ ->
                    "${Telephony.Sms.ADDRESS} LIKE ?"
                })
                append(" AND ")
                append(extraSelection)
                if (newerThanMillis != null) {
                    append(" AND ")
                    append(Telephony.Sms.DATE)
                    append(" > ?")
                }
            }
            val args = ArrayList<String>(likes.size + extraArgs.size + 1)
            args.addAll(likes)
            args.addAll(extraArgs)
            if (newerThanMillis != null) args += newerThanMillis.toString()
            return selection to args.toTypedArray()
        }
    }
}
