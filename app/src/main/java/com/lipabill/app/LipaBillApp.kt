package com.lipabill.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.lipabill.app.auth.AuthManager
import com.lipabill.app.data.local.AppDatabase
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.data.repository.MerchantDirectory
import com.lipabill.app.data.repository.MerchantSnapshotStore
import com.lipabill.app.data.repository.RepeatAttemptRepository
import com.lipabill.app.data.repository.TicketRepository
import com.lipabill.app.data.repository.TransactionRepository
import com.lipabill.app.data.sms.SmsInboxReader
import com.lipabill.app.data.tickets.TicketDocumentImporter
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.data.sms.SmsInboxSyncWatcher
import com.lipabill.app.metrics.AppCrashReporting
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.ui.permissions.AccessibilityPreferred
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LipaBillApp : Application() {

    lateinit var securePreferences: SecurePreferences
        private set
    lateinit var authManager: AuthManager
        private set
    lateinit var repository: TransactionRepository
        private set
    lateinit var merchantDirectory: MerchantDirectory
        private set
    lateinit var repeatRepository: RepeatAttemptRepository
        private set
    lateinit var repeatCoordinator: RepeatTransactionCoordinator
        private set
    lateinit var ticketRepository: TicketRepository
        private set
    lateinit var smsInboxSyncWatcher: SmsInboxSyncWatcher
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _fontSizeSp = MutableStateFlow(12)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private val _alwaysShowBalance = MutableStateFlow(false)
    val alwaysShowBalance: StateFlow<Boolean> = _alwaysShowBalance.asStateFlow()

    private val _favouritesSectionEnabled = MutableStateFlow(true)
    val favouritesSectionEnabled: StateFlow<Boolean> = _favouritesSectionEnabled.asStateFlow()

    private val _recordingPrivacy = MutableStateFlow(false)
    val recordingPrivacy: StateFlow<Boolean> = _recordingPrivacy.asStateFlow()

    private val _openedPass = MutableStateFlow<OpenedPass>(OpenedPass.Idle)
    val openedPass: StateFlow<OpenedPass> = _openedPass.asStateFlow()

    fun importOpenedPasses(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _openedPass.value = OpenedPass.Reading
        appScope.launch {
            var savedId: Long? = null
            var failure: String? = null
            for (uri in uris) {
                val outcome = runCatching { importOnePass(uri) }
                outcome.onSuccess { id -> if (savedId == null) savedId = id }
                    .onFailure { failure = it.message?.ifBlank { null } ?: "Couldn’t read that pass." }
            }
            _openedPass.value = when (val id = savedId) {
                null -> OpenedPass.Failed(failure ?: "Couldn’t read that pass.")
                else -> OpenedPass.Saved(id)
            }
        }
    }

    fun clearOpenedPass() {
        _openedPass.value = OpenedPass.Idle
    }

    private suspend fun importOnePass(uri: Uri): Long {
        val draft = TicketDocumentImporter.import(this, uri, TicketDocumentKind.PKPASS)
        return ticketRepository.addImport(
            draft.copy(expectsBoardingPass = false, hasBoardingPass = true)
        ) ?: ticketRepository.existingTicketId(draft.barcodeValue, draft.orderId)
            ?: throw IllegalArgumentException("That ticket is already saved.")
    }

    override fun onCreate() {
        super.onCreate()
        AppMetrics.init(this)
        AppCrashReporting.init(this)
        securePreferences = SecurePreferences(this)
        _fontSizeSp.value = securePreferences.uiFontSizeSp
        _alwaysShowBalance.value = securePreferences.alwaysShowBalance
        _favouritesSectionEnabled.value = securePreferences.favouritesSectionEnabled
        _recordingPrivacy.value = debugRecordingPrivacy()
        // Existing installs already past first launch — don't force the new setup wizard.
        if (!securePreferences.firstRunSetupDone &&
            (securePreferences.smsBackfillDone ||
                securePreferences.preferredSimSubscriptionId >= 0 ||
                securePreferences.accessibilityOnboardingSeen)
        ) {
            securePreferences.firstRunSetupDone = true
        }
        authManager = AuthManager(this, securePreferences)
        // Remember if Accessibility is already on so an update can prompt re-enable.
        AccessibilityPreferred.syncFromSystem(this, securePreferences)

        val db = AppDatabase.getInstance(this)
        merchantDirectory = MerchantDirectory(
            dao = db.merchantDao(),
            snapshotStore = MerchantSnapshotStore(this)
        )
        repository = TransactionRepository(
            dao = db.transactionDao(),
            inboxReader = SmsInboxReader(this),
            securePreferences = securePreferences,
            merchantDirectory = merchantDirectory
        )
        repeatRepository = RepeatAttemptRepository(db.repeatAttemptDao())
        ticketRepository = TicketRepository(db.ticketDao())
        repeatCoordinator = RepeatTransactionCoordinator(
            appContext = this,
            securePreferences = securePreferences,
            repeatRepository = repeatRepository,
            transactionRepository = repository
        )
        smsInboxSyncWatcher = SmsInboxSyncWatcher(this, repository)
        smsInboxSyncWatcher.start()

        appScope.launch {
            runCatching { merchantDirectory.restoreFromSnapshotIfEmpty() }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                authManager.onAppForegrounded()
                smsInboxSyncWatcher.ensureStarted()
                // Quiet catch-up when returning to the app (also runs confirmation purge once)
                appScope.launch {
                    try {
                        merchantDirectory.restoreFromSnapshotIfEmpty()
                        repository.purgeNonConfirmationsIfNeeded()
                        repository.repairParsedAmountsIfNeeded()
                        repository.rescanInbox()
                        repository.linkPhonesByName()
                    } catch (_: Exception) {
                        // ignore — pull-to-refresh remains available
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                authManager.onAppBackgrounded()
            }
        })

        authManager.onColdStart()
    }

    fun setFontSizeSp(size: Int) {
        securePreferences.uiFontSizeSp = size
        _fontSizeSp.value = securePreferences.uiFontSizeSp
    }

    fun setAlwaysShowBalance(enabled: Boolean) {
        securePreferences.alwaysShowBalance = enabled
        _alwaysShowBalance.value = enabled
    }

    fun setFavouritesSectionEnabled(enabled: Boolean) {
        securePreferences.favouritesSectionEnabled = enabled
        _favouritesSectionEnabled.value = enabled
    }

    fun setRecordingPrivacy(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        securePreferences.recordingPrivacy = enabled
        _recordingPrivacy.value = enabled
    }

    private fun debugRecordingPrivacy(): Boolean =
        BuildConfig.DEBUG && securePreferences.recordingPrivacy
}

/** A .pkpass opened from another app, saved off the Passes screen so navigation cannot cancel it. */
sealed interface OpenedPass {
    data object Idle : OpenedPass
    data object Reading : OpenedPass
    data class Saved(val id: Long) : OpenedPass
    data class Failed(val message: String) : OpenedPass
}
