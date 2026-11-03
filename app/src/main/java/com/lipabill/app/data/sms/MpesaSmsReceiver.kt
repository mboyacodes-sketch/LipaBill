package com.lipabill.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.lipabill.app.LipaBillApp
import com.lipabill.app.metrics.AppCrashReporting
import com.lipabill.app.metrics.AppMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Live SMS ingestion for money confirmation messages arriving after install.
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
                    when (
                        val outcome = ConfirmationIngestion.acceptOutcome(
                            address = sms.displayOriginatingAddress.orEmpty(),
                            body = sms.messageBody.orEmpty(),
                            timestampMillis = sms.timestampMillis,
                            region = app.securePreferences.appRegion,
                            enabled = app.enabledSources.value
                        )
                    ) {
                        is ConfirmationIngestion.Outcome.Matched -> {
                            matched = true
                            AppMetrics.smsIngest("matched")
                            app.repository.upsert(outcome.tx)
                        }
                        ConfirmationIngestion.Outcome.Skipped -> Unit
                        is ConfirmationIngestion.Outcome.ParseFailed -> {
                            // Format quirks are common — analytics only, no Crashlytics flood.
                            AppMetrics.smsIngest("parse_error")
                        }
                    }
                }
                if (matched) {
                    app.repository.linkPhonesByName()
                    app.smsInboxSyncWatcher.syncQuietNow()
                }
            } catch (error: Exception) {
                AppMetrics.smsIngest("error")
                AppCrashReporting.recordCaught(error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
