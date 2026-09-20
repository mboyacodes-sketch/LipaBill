package com.lipabill.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.parser.MpesaSmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Live SMS ingestion for M-Pesa messages arriving after install.
 * Upserts the broadcast payload immediately, then kicks a quiet inbox
 * rescan so multipart / delayed writes still land in Room.
 */
class MpesaSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val app = context.applicationContext as? LipaBillApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var matched = false
                messages.forEach { sms ->
                    val address = sms.displayOriginatingAddress.orEmpty()
                    if (!MpesaSmsFilter.isMpesaSender(address)) return@forEach
                    val body = sms.messageBody.orEmpty()
                    if (body.isBlank()) return@forEach
                    if (!MpesaSmsFilter.isTransactionConfirmation(body)) return@forEach
                    matched = true
                    val parsed = MpesaSmsParser.parse(body, sms.timestampMillis)
                    app.repository.upsert(parsed)
                }
                if (matched) {
                    app.repository.linkPhonesByName()
                    app.smsInboxSyncWatcher.syncQuietNow()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
