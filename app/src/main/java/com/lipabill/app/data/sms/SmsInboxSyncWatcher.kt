package com.lipabill.app.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.lipabill.app.data.repository.TransactionRepository
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
        if (!hasSmsPermission()) return
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

    fun stop() {
        if (!registered) return
        try {
            appContext.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
            // ignore
        }
        registered = false
        debounceJob?.cancel()
        debounceJob = null
    }

    /** Call after SMS permission is granted at runtime. */
    fun ensureStarted() {
        if (!registered) start()
    }

    fun syncQuietNow() {
        scheduleSync(immediate = true)
    }

    private fun scheduleSync(immediate: Boolean = false) {
        if (!hasSmsPermission()) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            try {
                repository.rescanInbox()
            } catch (_: SecurityException) {
                // permission revoked mid-flight
            } catch (_: Exception) {
                // keep quiet — Room list will refresh on next successful sync
            }
        }
    }

    private fun hasSmsPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_SMS)
        val receive = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECEIVE_SMS)
        return read == PackageManager.PERMISSION_GRANTED &&
            receive == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val DEBOUNCE_MS = 900L
    }
}
