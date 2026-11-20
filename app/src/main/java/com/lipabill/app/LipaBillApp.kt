package com.lipabill.app

import android.app.Application
import android.net.Uri
import android.telephony.SubscriptionManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.lipabill.app.auth.AuthManager
import com.lipabill.app.data.local.AppDatabase
import com.lipabill.app.data.model.ActiveRegion
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.resolvedLedgerSources
import com.lipabill.app.data.model.importableLedgerSources
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
import com.lipabill.app.engage.SpendCategory
import kotlinx.coroutines.flow.first
import com.lipabill.app.viewmodel.InsightMarks
import com.lipabill.app.viewmodel.balanceWalletChoices
import com.lipabill.app.viewmodel.buildMoneyPlan
import com.lipabill.app.viewmodel.MoneyPlanUi
import com.lipabill.app.widget.BalanceWidget
import com.lipabill.app.data.model.MpesaTransaction
import com.lipabill.app.viewmodel.goalProgress
import com.lipabill.app.notify.MoneyMomentNotifier
import com.lipabill.app.engage.MoneyMoments
import com.lipabill.app.viewmodel.parseGoals
import com.lipabill.app.viewmodel.encodeGoals
import com.lipabill.app.viewmodel.parseBudgets
import com.lipabill.app.viewmodel.encodeBudgets
import com.lipabill.app.viewmodel.SavingsGoal
import com.lipabill.app.viewmodel.CategoryBudget
import com.lipabill.app.engage.encodeSpendCategoryOverride
import com.lipabill.app.engage.parseSpendCategoryOverrides
import com.lipabill.app.metrics.AppCrashReporting
import com.lipabill.app.metrics.AppMetrics
import com.lipabill.app.metrics.TicketImportResult
import com.lipabill.app.ui.permissions.AccessibilityPreferred
import com.lipabill.app.ui.privacy.RecordingCapture
import com.lipabill.app.tickets.PassReminderScheduler
import com.lipabill.app.ussd.LineGatedSources
import com.lipabill.app.ussd.PaymentDialResolver
import com.lipabill.app.ussd.PaymentWallet
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.SimLineHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    private val _recordingPrivacy = MutableStateFlow(false)
    val recordingPrivacy: StateFlow<Boolean> = _recordingPrivacy.asStateFlow()

    private val _darkMode = MutableStateFlow(false)
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    private val _essentialPayees = MutableStateFlow<Set<String>>(emptySet())
    val essentialPayees: StateFlow<Set<String>> = _essentialPayees.asStateFlow()

    private val _budgets = MutableStateFlow<List<CategoryBudget>>(emptyList())
    /** Monthly category budgets the user set. */
    val budgets: StateFlow<List<CategoryBudget>> = _budgets.asStateFlow()

    private val _goals = MutableStateFlow<List<SavingsGoal>>(emptyList())
    /** Savings goals, filled by the wallet's own savings transfers. */
    val goals: StateFlow<List<SavingsGoal>> = _goals.asStateFlow()

    /**
     * After a live payment: budget crossings, a food-delivery streak, payday. Each moment
     * posts once per period; sent keys are remembered (newest 200 kept).
     */
    private fun postMoneyMoments(tx: MpesaTransaction) {
        appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                val window = 95L * 24 * 60 * 60 * 1000
                val oldestGoal = _goals.value.minOfOrNull { it.createdAtMillis } ?: now
                val recent = repository.ledgerSince(minOf(now - window, oldestGoal))
                val sent = securePreferences.moneyMomentsSent
                val moments = MoneyMoments.evaluate(
                    tx = tx,
                    recent = recent,
                    budgets = _budgets.value,
                    goals = goalProgress(_goals.value, recent, java.time.LocalDate.now()),
                    overrides = _spendCategoryOverrides.value,
                    alreadySent = sent
                )
                if (moments.isEmpty()) return@runCatching
                moments.forEach { MoneyMomentNotifier.show(this@LipaBillApp, it) }
                securePreferences.moneyMomentsSent = (sent + moments.map { it.key }).toList().takeLast(200).toSet()
            }
        }
    }

    /** Runs [block] on the app's background scope (widgets, receivers). */
    fun launchInBackground(block: suspend () -> Unit) {
        appScope.launch { block() }
    }

    /** Safe to spend and balance for the home-screen widget, from a capped ledger read. */
    suspend fun widgetSnapshot(): MoneyPlanUi {
        val since = System.currentTimeMillis() - 200L * 24 * 60 * 60 * 1000
        val ledger = repository.ledgerSince(since)
        val balances = repository.observeLatestBalances().first()
        val region = _appRegion.value
        val enabled = _enabledSources.value.ifEmpty { region.sources }
        return buildMoneyPlan(
            ledger = ledger,
            balances = balances,
            walletSources = balanceWalletChoices(region, enabled, _simLines.value),
            primary = region.primaryWallet,
            budgets = _budgets.value,
            goals = _goals.value,
            overrides = _spendCategoryOverrides.value,
            marks = InsightMarks(_essentialPayees.value, _watchedPayees.value)
        )
    }

    /** Sets, changes or (with null / 0) removes the monthly budget for [category]. */
    fun setBudget(category: SpendCategory, monthlyLimit: Double?) {
        val next = _budgets.value.filterNot { it.category == category } +
            listOfNotNull(monthlyLimit?.takeIf { it > 0.0 }?.let { CategoryBudget(category, it) })
        securePreferences.categoryBudgets = encodeBudgets(next)
        _budgets.value = parseBudgets(securePreferences.categoryBudgets)
        BalanceWidget.refresh(this)
    }

    /** Adds or replaces (same id) a savings goal. */
    fun saveGoal(goal: SavingsGoal) {
        val next = _goals.value.filterNot { it.id == goal.id } + goal
        securePreferences.savingsGoals = encodeGoals(next)
        _goals.value = next
        BalanceWidget.refresh(this)
    }

    fun deleteGoal(id: String) {
        val next = _goals.value.filterNot { it.id == id }
        securePreferences.savingsGoals = encodeGoals(next)
        _goals.value = next
        BalanceWidget.refresh(this)
    }

    private val _spendCategoryOverrides = MutableStateFlow<Map<String, String>>(emptyMap())
    val spendCategoryOverrides: StateFlow<Map<String, String>> = _spendCategoryOverrides.asStateFlow()

    private val _watchedPayees = MutableStateFlow<Set<String>>(emptySet())
    val watchedPayees: StateFlow<Set<String>> = _watchedPayees.asStateFlow()

    private val _earnedBadges = MutableStateFlow<Set<String>>(emptySet())
    val earnedBadges: StateFlow<Set<String>> = _earnedBadges.asStateFlow()

    private val _appRegion = MutableStateFlow(AppRegion.DEFAULT)
    val appRegion: StateFlow<AppRegion> = _appRegion.asStateFlow()
    private val _enabledSources = MutableStateFlow(AppRegion.DEFAULT.sources)
    val enabledSources: StateFlow<Set<MoneySource>> = _enabledSources.asStateFlow()

    /** Active SIM lines; SIM-bound wallets (M-Pesa, Airtel Money, MoMo) only count with their line. */
    private val _simLines = MutableStateFlow<List<SimLine>>(emptyList())
    val simLines: StateFlow<List<SimLine>> = _simLines.asStateFlow()

    /**
     * Sources the ledger may import: the user's picks for [region] that exist in that
     * country's catalog. SIM presence gates Send/Pay dial only — SMS still imports for
     * every enabled rail (bank-first then MNO must still pull older wallet SMS).
     */
    fun usableLedgerSources(
        region: AppRegion = _appRegion.value,
        chosen: Set<MoneySource> = _enabledSources.value
    ): Set<MoneySource> = importableLedgerSources(region, chosen)

    /**
     * Re-reads the active SIMs. When a chosen wallet's line shows up that the last check
     * didn't have, rescans the full inbox so SMS skipped while it was missing are imported.
     */
    fun refreshSimLines(lines: List<SimLine> = SimLineHelper.listActiveLines(this, _appRegion.value)) {
        val region = _appRegion.value
        _simLines.value = lines
        val present = LineGatedSources.onPhone(region, lines)
        val previous = securePreferences.lineWalletsSeen
        securePreferences.lineWalletsSeen = present
        // First check on this install: the initial backfill already uses these lines.
        if (previous == null) return
        val gained = (present - previous).filter { it in _enabledSources.value }
        if (gained.isNotEmpty() && securePreferences.firstRunSetupDone) refreshLedger()
    }

    /**
     * The wallet Send/Pay/Repeat dial with: the saved line when it can still dial, else the
     * first eligible one. Home caption, balance pill, sheets and receipts all read this.
     */
    val activeDialWallet: StateFlow<PaymentWallet?> by lazy {
        combine(_appRegion, _simLines, _enabledSources, securePreferences.preferredSim) {
                region, lines, enabled, preferred ->
            PaymentDialResolver.resolve(region, lines, preferred, enabled = enabled)?.wallet
        }.stateIn(appScope, SharingStarted.Eagerly, null)
    }

    /** Make [source]'s line the active dial wallet. False when no active line carries it. */
    fun setActiveWallet(source: MoneySource): Boolean {
        val region = _appRegion.value
        if (source !in _enabledSources.value) return false
        val line = _simLines.value.firstOrNull { it.payWalletFor(region)?.ledgerSource == source }
            ?: return false
        securePreferences.preferredSimSubscriptionId = line.subscriptionId
        return true
    }

    private val subscriptionsListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() {
            refreshSimLines()
        }
    }

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
        securePreferences = SecurePreferences(this)
        securePreferences.seedDemoSocialHandles()
        _fontSizeSp.value = securePreferences.uiFontSizeSp
        _recordingPrivacy.value = recordingCaptureEnabled()
        _darkMode.value = securePreferences.darkMode
        _essentialPayees.value = securePreferences.essentialPayees
        _spendCategoryOverrides.value =
            parseSpendCategoryOverrides(securePreferences.spendCategoryOverrides)
        _budgets.value = parseBudgets(securePreferences.categoryBudgets)
        _goals.value = parseGoals(securePreferences.savingsGoals)
        _watchedPayees.value = securePreferences.watchedPayees
        _earnedBadges.value = securePreferences.earnedBadges
        _appRegion.value = securePreferences.appRegion
        // Primary-only installs (old first-run) → multi-rail default so other MNO/bank SMS import.
        val widenedRails = securePreferences.widenPrimaryOnlyLedgerSourcesIfNeeded(_appRegion.value)
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
            onNewTransaction = { tx ->
                ledgerNotifier.show(tx)
                postMoneyMoments(tx)
            },
            onPaymentFailure = { title, body, notifyId ->
                ledgerNotifier.showFailure(title, body, notifyId)
            },
            usableSources = ::usableLedgerSources
        )
        refreshSimLines()
        try {
            @Suppress("DEPRECATION")
            getSystemService(SubscriptionManager::class.java)
                ?.addOnSubscriptionsChangedListener(subscriptionsListener)
        } catch (_: SecurityException) {
            // Foreground checks below still refresh the lines.
        }
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
            BalanceWidget.refresh(this)
        }
        smsInboxSyncWatcher = SmsInboxSyncWatcher(this, repository)
        smsInboxSyncWatcher.start()
        // Widen and/or one-time full re-read: prior installs caught up on M-Pesa alone.
        if ((widenedRails || securePreferences.consumePendingFullInboxRescan()) &&
            securePreferences.firstRunSetupDone
        ) {
            refreshLedger()
        }
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
        // Firebase after first frame work — do not block prefs/DB/auth on SDK init.
        appScope.launch {
            AppMetrics.init(this@LipaBillApp)
            AppCrashReporting.init(this@LipaBillApp)
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                authManager.onAppForegrounded()
                // Picks up a SIM swap or a phone permission granted while away.
                refreshSimLines()
                BalanceWidget.refresh(this@LipaBillApp)
                smsInboxSyncWatcher.ensureStarted()
                // Quiet catch-up when returning to the app (repairs once, then inbox import).
                // First launch after multi-rail SMS fix still owes a full inbox pass.
                appScope.launch {
                    try {
                        merchantDirectory.restoreFromSnapshotIfEmpty()
                        if (securePreferences.consumePendingFullInboxRescan()) {
                            refreshLedger()
                        } else {
                            AppMetrics.ledgerRefresh(repository.catchUpOnForeground())
                        }
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

    fun setDarkMode(enabled: Boolean) {
        securePreferences.darkMode = enabled
        _darkMode.value = enabled
    }

    fun setPayeeEssential(key: String, essential: Boolean) {
        val next = if (essential) _essentialPayees.value + key else _essentialPayees.value - key
        securePreferences.essentialPayees = next
        _essentialPayees.value = next
    }

    /**
     * Persist a spend-category override for [payeeKey]. Pass null to clear.
     * Choosing Essentials also marks the payee essential for Insights/Habits.
     */
    fun setSpendCategoryOverride(payeeKey: String, category: SpendCategory?) {
        val key = payeeKey.trim()
        if (key.isEmpty()) return
        val without = securePreferences.spendCategoryOverrides
            .filterNot { it.startsWith("$key=") }
            .toSet()
        val next = if (category == null) {
            without
        } else {
            without + encodeSpendCategoryOverride(key, category)
        }
        securePreferences.spendCategoryOverrides = next
        _spendCategoryOverrides.value = parseSpendCategoryOverrides(next)
        if (category == SpendCategory.ESSENTIALS) {
            setPayeeEssential(key, true)
        }
    }

    /** Move a spend override when a name-keyed payee is renamed. */
    fun moveSpendCategoryOverride(fromKey: String, toKey: String) {
        if (fromKey == toKey || fromKey.isBlank() || toKey.isBlank()) return
        val id = _spendCategoryOverrides.value[fromKey] ?: return
        val category = SpendCategory.fromId(id) ?: return
        setSpendCategoryOverride(fromKey, null)
        setSpendCategoryOverride(toKey, category)
        if (fromKey in _essentialPayees.value) {
            setPayeeEssential(fromKey, false)
            setPayeeEssential(toKey, true)
        }
    }

    fun setPayeeWatched(key: String, watched: Boolean) {
        val next = if (watched) _watchedPayees.value + key else _watchedPayees.value - key
        securePreferences.watchedPayees = next
        _watchedPayees.value = next
    }

    fun recordBadges(ids: Set<String>) {
        val next = _earnedBadges.value + ids
        if (next == _earnedBadges.value) return
        securePreferences.earnedBadges = next
        _earnedBadges.value = next
    }

    fun setAppRegion(region: AppRegion, rescan: Boolean = true) {
        if (region == _appRegion.value) return
        securePreferences.appRegion = region
        _appRegion.value = region
        ActiveRegion.current = region
        // New country, new wallet set: re-baseline lines so this switch's rescan covers them.
        val lines = SimLineHelper.listActiveLines(this, region)
        _simLines.value = lines
        securePreferences.lineWalletsSeen = LineGatedSources.onPhone(region, lines)
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
