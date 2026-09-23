package com.lipabill.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.lipabill.app.LipaBillApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Live SMS ingestion for M-Pesa messages arriving after install.
 * Upserts the broadcast payload immediately, then kicks a quiet inbox
 * rescan so multipart / delayed writes still land in Room.
 *
 * Does not log SMS bodies. Does not trigger payments or USSD.
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
                    val body = sms.messageBody.orEmpty()
                    val parsed = MpesaSmsIngestion.acceptAndParse(
                        address = address,
                        body = body,
                        timestampMillis = sms.timestampMillis
                    ) ?: return@forEach
                    matched = true
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
