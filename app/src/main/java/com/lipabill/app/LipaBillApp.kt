package com.lipabill.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.lipabill.app.auth.AuthManager
import com.lipabill.app.data.local.AppDatabase
import com.lipabill.app.data.model.ActiveRegion
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.resolvedLedgerSources
import com.lipabill.app.data.prefs.SecurePreferences
import com.lipabill.app.data.repository.EngagementRepository
import com.lipabill.app.data.repository.MerchantDirectory
import com.lipabill.app.data.repository.MerchantSnapshotStore
import com.lipabill.app.data.repository.RepeatAttemptRepository
import com.lipabill.app.data.repository.TicketRepository
import com.lipabill.app.data.repository.TransactionRepository
import com.lipabill.app.data.sms.LedgerNotifier
import com.lipabill.app.data.sms.SmsInboxReader
import com.lipabill.app.data.tickets.TicketDocumentImporter
import com.lipabill.app.data.tickets.TicketDocumentKind
import com.lipabill.app.data.sms.SmsInboxSyncWatcher
import com.lipabill.app.engage.EngagementScheduler
import com.lipabill.app.metrics.AppCrashReporting
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.TicketImportResult
import com.lipabill.app.ui.permissions.AccessibilityPreferred
import com.lipabill.app.ui.privacy.RecordingCapture
import com.lipabill.app.tickets.PassReminderScheduler
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
    lateinit var engagement: EngagementRepository
        private set
    lateinit var engagementScheduler: EngagementScheduler
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _fontSizeSp = MutableStateFlow(12)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private val _favouritesSectionEnabled = MutableStateFlow(true)
    val favouritesSectionEnabled: StateFlow<Boolean> = _favouritesSectionEnabled.asStateFlow()

    private val _recordingPrivacy = MutableStateFlow(false)
    val recordingPrivacy: StateFlow<Boolean> = _recordingPrivacy.asStateFlow()

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _appRegion = MutableStateFlow(AppRegion.DEFAULT)
    val appRegion: StateFlow<AppRegion> = _appRegion.asStateFlow()
    private val _enabledSources = MutableStateFlow(AppRegion.DEFAULT.sources)
    val enabledSources: StateFlow<Set<MoneySource>> = _enabledSources.asStateFlow()

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
                    .onFailure { error ->
                        AppMetrics.ticketImportFailure("share", error)
                        failure = error.message?.ifBlank { null } ?: "Couldn’t read that pass."
                    }
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
            .copy(expectsBoardingPass = false, hasBoardingPass = true)
        val inserted = ticketRepository.addImport(draft)
        if (inserted != null) {
            AppMetrics.ticketImport("share", TicketImportResult.Saved)
            return inserted
        }
        val existing = ticketRepository.existingTicketId(draft.barcodeValue, draft.orderId)
        if (existing != null) {
            AppMetrics.ticketImport("share", TicketImportResult.Duplicate)
            return existing
        }
        throw IllegalArgumentException("That ticket is already saved.")
    }

    override fun onCreate() {
        super.onCreate()
        AppMetrics.init(this)
        AppCrashReporting.init(this)
        securePreferences = SecurePreferences(this)
        securePreferences.seedDemoSocialHandles()
        _fontSizeSp.value = securePreferences.uiFontSizeSp
        _favouritesSectionEnabled.value = securePreferences.favouritesSectionEnabled
        _recordingPrivacy.value = recordingCaptureEnabled()
        _darkMode.value = securePreferences.darkMode
        _appRegion.value = securePreferences.appRegion
        _enabledSources.value = securePreferences.enabledLedgerSources(_appRegion.value)
        ActiveRegion.current = _appRegion.value
        ActiveRegion.enabledSources = _enabledSources.value
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
        val ledgerNotifier = LedgerNotifier(this)
        repository = TransactionRepository(
            dao = db.transactionDao(),
            inboxReader = SmsInboxReader(this),
            securePreferences = securePreferences,
            merchantDirectory = merchantDirectory,
            onNewTransaction = ledgerNotifier::show
        )
        repeatRepository = RepeatAttemptRepository(db.repeatAttemptDao())
        ticketRepository = TicketRepository(db.ticketDao())
        repeatCoordinator = RepeatTransactionCoordinator(
            appContext = this,
            securePreferences = securePreferences,
            repeatRepository = repeatRepository,
            transactionRepository = repository
        )
        engagement = EngagementRepository(
            challenges = db.challengeDao(),
            transactions = db.transactionDao()
        )
        engagementScheduler = EngagementScheduler(this)
        repository.afterInsert = {
            val end = engagement.evaluate()
            if (end != null) engagementScheduler.scheduleChallengeEnd(end)
        }
        smsInboxSyncWatcher = SmsInboxSyncWatcher(this, repository)
        smsInboxSyncWatcher.start()
        engagementScheduler.ensureScheduled()
        appScope.launch {
            ticketRepository.observeAll().collect { tickets ->
                runCatching { PassReminderScheduler(this@LipaBillApp).sync(tickets) }
            }
        }

        appScope.launch {
            runCatching { merchantDirectory.restoreFromSnapshotIfEmpty() }
        }
        ensureDemoLedger()

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
                        AppMetrics.ledgerRefresh(repository.rescanInbox())
                        repository.linkPhonesByName()
                        val end = engagement.evaluate()
                        if (end != null) engagementScheduler.scheduleChallengeEnd(end)
                        engagementScheduler.ensureScheduled()
                    } catch (error: Exception) {
                        AppMetrics.ledgerRefreshFailed(error)
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

    fun setFavouritesSectionEnabled(enabled: Boolean) {
        securePreferences.favouritesSectionEnabled = enabled
        _favouritesSectionEnabled.value = enabled
    }

    fun setDarkMode(enabled: Boolean) {
        securePreferences.darkMode = enabled
        _darkMode.value = enabled
    }

    fun setAppRegion(region: AppRegion, rescan: Boolean = true) {
        if (region == _appRegion.value) return
        securePreferences.appRegion = region
        _appRegion.value = region
        ActiveRegion.current = region
        val nextSources = resolvedLedgerSources(region, _enabledSources.value) +
            region.primaryWallet
        publishEnabledSources(nextSources)
        if (!rescan || !securePreferences.firstRunSetupDone) return
        refreshLedger()
    }

    fun setEnabledSources(sources: Set<MoneySource>, rescan: Boolean = true) {
        val next = resolvedLedgerSources(_appRegion.value, sources)
        if (next == _enabledSources.value) return
        publishEnabledSources(next)
        if (!rescan || !securePreferences.firstRunSetupDone) return
        refreshLedger()
    }

    private fun publishEnabledSources(sources: Set<MoneySource>) {
        securePreferences.setEnabledLedgerSources(sources, _appRegion.value)
        _enabledSources.value = sources
        ActiveRegion.enabledSources = sources
    }

    private fun refreshLedger() {
        if (!::repository.isInitialized) return
        // Invalidate before the coroutine so an in-flight catch-up scan cannot
        // rewrite the high-water mark and skip SMS for newly enabled rails.
        repository.invalidateInboxScanProgress()
        appScope.launch { repository.rescanInbox(forceFullHistory = true) }
    }

    fun ensureDemoLedger() {
        if (!::repository.isInitialized) return
        appScope.launch {
            runCatching { repository.seedDemoLedgerIfEmpty() }
            if (::ticketRepository.isInitialized) {
                runCatching { ticketRepository.seedDemoPassIfEmpty() }
            }
        }
    }

    fun setRecordingPrivacy(enabled: Boolean) {
        if (!RecordingCapture.available) return
        securePreferences.recordingPrivacy = enabled
        _recordingPrivacy.value = enabled
    }

    private fun recordingCaptureEnabled(): Boolean =
        RecordingCapture.available && securePreferences.recordingPrivacy
}

/** A .pkpass opened from another app, saved off the Passes screen so navigation cannot cancel it. */
sealed interface OpenedPass {
    data object Idle : OpenedPass
    data object Reading : OpenedPass
    data class Saved(val id: Long) : OpenedPass
    data class Failed(val message: String) : OpenedPass
}
