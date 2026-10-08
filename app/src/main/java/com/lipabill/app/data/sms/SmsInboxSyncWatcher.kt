package com.lipabill.app.data.sms

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.lipabill.app.data.repository.TransactionRepository
import com.lipabill.app.metrics.AppMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches the device SMS inbox and quietly re-imports matching M-Pesa messages
 * whenever the inbox changes (new SMS, multipart assemble, etc.).
 */
class SmsInboxSyncWatcher(
    private val appContext: Context,
    private val repository: TransactionRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var registered = false
    private var debounceJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }

        override fun onChange(selfChange: Boolean, uri: Uri?) {
            scheduleSync()
        }
    }

    fun start() {
        if (registered) return
        if (!appContext.hasMpesaSmsPermission()) return
        try {
            appContext.contentResolver.registerContentObserver(
                Telephony.Sms.Inbox.CONTENT_URI,
                true,
                observer
            )
            registered = true
        } catch (_: SecurityException) {
            registered = false
        }
    }

    /** Call after SMS permission is granted at runtime. */
    fun ensureStarted() {
        if (!registered) start()
    }

    fun syncQuietNow() {
        scheduleSync(immediate = true)
    }

    private fun scheduleSync(immediate: Boolean = false) {
        if (!appContext.hasMpesaSmsPermission()) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            try {
                AppMetrics.ledgerRefresh(repository.rescanInbox())
            } catch (error: Exception) {
                AppMetrics.ledgerRefreshFailed(error)
            }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 900L
    }
}
