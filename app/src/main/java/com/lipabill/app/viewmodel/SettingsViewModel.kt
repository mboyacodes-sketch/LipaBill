package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.defaultLedgerSources
import com.lipabill.app.data.model.providerCatalog
import com.lipabill.app.data.model.lockedLedgerSources
import com.lipabill.app.data.model.regionsPresentOnSims
import com.lipabill.app.data.model.selectableAppRegions
import com.lipabill.app.data.model.simWalletNumberHints
import com.lipabill.app.data.model.toggleLedgerSource
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
import com.lipabill.app.data.tickets.PassReminderLeadTimes
import com.lipabill.app.tickets.PassReminderScheduler
import com.lipabill.app.ui.permissions.AccessibilityPreferred
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ussd.LineGatedSources
import com.lipabill.app.ussd.PaymentDialResolver
import com.lipabill.app.ussd.RepeatTransactionCoordinator
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.SimLineHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val region: AppRegion = AppRegion.DEFAULT,
    val enabledSources: Set<MoneySource> = AppRegion.DEFAULT.sources,
    val providerChoices: List<MoneySource> = providerCatalog(AppRegion.DEFAULT),
    val timeoutMinutes: Int = 2,
    val fontSizeSp: Int = 12,
    val recordingPrivacy: Boolean = false,
    /** Debug / sideload: bypass first-run and open Home. */
    val skipSetupWizard: Boolean = false,
    val darkMode: Boolean = false,
    val repeatEnabled: Boolean = true,
    val passReminderLeadTimes: PassReminderLeadTimes = PassReminderLeadTimes.DEFAULT,
    val lipaBillA11yEnabled: Boolean = false,
    val simLines: List<SimLine> = emptyList(),
    val preferredSimSubscriptionId: Int? = null,
    val needsPhoneStatePermission: Boolean = false,
    /** Wallets with no matching SIM when phone access is on — chips stay off. */
    val lockedSources: Map<MoneySource, String> = emptyMap(),
    /** “M-Pesa · …6877” suffixes from SIM line numbers when readable. */
    val walletNumberHints: Map<MoneySource, String> = emptyMap(),
    /** Countries with a SIM on this phone; empty means the picker is not gated. */
    val simRegions: Set<AppRegion> = emptySet(),
    /** Regions the Country picker may select (SIM-backed + current). */
    val selectableRegions: Set<AppRegion> = AppRegion.entries.toSet()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LipaBillApp
    private val auth = app.authManager

    private val _ui = MutableStateFlow(buildState())
    val uiState: StateFlow<SettingsUiState> = _ui.asStateFlow()

    val recentAttempts: StateFlow<List<RepeatAttemptEntity>> =
        app.repeatRepository.observeRecent(20)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun refresh() {
        val state = buildState()
        if (state.lipaBillA11yEnabled) {
            AccessibilityPreferred.markUserTurningOn(app.securePreferences)
        }
        _ui.value = state
    }

    fun setRegion(region: AppRegion) {
        val allowed = _ui.value.selectableRegions
        if (region !in allowed) return
        app.setAppRegion(region, rescan = true)
        refresh()
    }

    fun toggleProvider(source: MoneySource) {
        val region = app.appRegion.value
        val ctx = getApplication<Application>()
        if (SimLineHelper.hasPhoneStatePermission(ctx)) {
            val lines = SimLineHelper.listActiveLines(ctx, region)
            if (source in LineGatedSources.locked(region, lines, phoneAccess = true)) return
        }
        val next = toggleLedgerSource(
            app.enabledSources.value,
            source,
            region.sources
        )
        app.setEnabledSources(next, rescan = true)
        refresh()
    }

    fun setTimeoutMinutes(minutes: Int) {
        auth.setTimeoutMinutes(minutes)
        refresh()
    }

    fun setFontSizeSp(size: Int) {
        app.setFontSizeSp(size)
        refresh()
    }


    fun setDarkMode(enabled: Boolean) {
        app.setDarkMode(enabled)
        refresh()
    }



    fun setRecordingPrivacy(enabled: Boolean) {
        app.setRecordingPrivacy(enabled)
        refresh()
    }

    /**
     * Debug / sideload. When enabled, marks first-run complete and clears any
     * pending replay so the next launch opens Home. Caller should navigate home.
     */
    fun setSkipSetupWizard(enabled: Boolean) {
        app.securePreferences.debugSkipSetupWizard = enabled
        if (enabled) {
            app.securePreferences.replayFirstRunPending = false
            app.securePreferences.firstRunSetupDone = true
            app.ensureDemoLedger()
        }
        refresh()
    }

    fun setRepeatEnabled(enabled: Boolean) {
        app.repeatCoordinator.setFeatureEnabled(enabled)
        refresh()
    }

    fun setPassReminderLeadTimes(times: PassReminderLeadTimes) {
        app.securePreferences.passReminderLeadTimes = times
        PassReminderScheduler(
            getApplication(),
            app.passContextSync,
            { app.securePreferences.passReminderLeadTimes }
        ).requestImmediateRecheck()
        refresh()
    }

    suspend fun prepareFulizaOptIn(): RepeatTransactionCoordinator.PreparedRepeat =
        app.repeatCoordinator.prepareFulizaOptIn()

    fun startFulizaOptIn(prepared: RepeatTransactionCoordinator.PreparedRepeat): Boolean {
        val subId = _ui.value.preferredSimSubscriptionId
        return app.repeatCoordinator.startDialing(prepared, subId)
    }

    fun setPreferredSim(subscriptionId: Int) {
        val region = app.securePreferences.appRegion
        val enabled = app.enabledSources.value
        val lines = SimLineHelper.listActiveLines(getApplication(), region)
        val line = lines.firstOrNull { it.subscriptionId == subscriptionId } ?: return
        val wallet = line.payWalletFor(region) ?: return
        if (wallet.ledgerSource !in enabled) return
        app.securePreferences.preferredSimSubscriptionId = subscriptionId
        refresh()
    }

    fun lockNow() {
        auth.lockNow()
    }

    /** Settings Turn on/off — remember intent so updates can prompt re-enable. */
    fun onAccessibilityToggleConfirmed(currentlyEnabled: Boolean) {
        if (currentlyEnabled) {
            AccessibilityPreferred.markUserTurningOff(app.securePreferences)
        } else {
            AccessibilityPreferred.markUserTurningOn(app.securePreferences)
        }
        AccessibilityHelper.openAppAccessibilityDetails(getApplication())
    }

    private fun buildState(): SettingsUiState {
        val ctx = getApplication<Application>()
        val region = app.securePreferences.appRegion
        val needsPerm = !SimLineHelper.hasPhoneStatePermission(ctx)
        val lines = if (needsPerm) emptyList() else SimLineHelper.listActiveLines(ctx, region)
        var enabled = app.enabledSources.value
        if (!needsPerm) {
            val locked = LineGatedSources.locked(region, lines, phoneAccess = true).keys
            val pruned = enabled.filterNot { it in locked }.toSet()
            if (pruned != enabled) {
                enabled = pruned.ifEmpty {
                    defaultLedgerSources(
                        region,
                        onPhoneWallets = LineGatedSources.onPhone(region, lines)
                    )
                }
                if (enabled != app.enabledSources.value) {
                    app.setEnabledSources(enabled, rescan = true)
                }
            }
            SimLineHelper.ensurePayPreferred(region, app.securePreferences, lines, enabled)
        }
        // Settings is where phone access gets granted; let the ledger see the lines at once.
        app.refreshSimLines(lines)
        val preferred = app.securePreferences.preferredSimSubscriptionId
        val selected = lines.firstOrNull {
            it.subscriptionId == preferred &&
                it.isPayEligible(region) &&
                it.payWalletFor(region)?.ledgerSource in enabled
        }?.subscriptionId
            ?: PaymentDialResolver.resolve(region, lines, preferred, enabled = enabled)
                ?.subscriptionId
        val simRegions = ctx.regionsPresentOnSims()
        return SettingsUiState(
            region = region,
            enabledSources = enabled,
            providerChoices = providerCatalog(region),
            timeoutMinutes = auth.timeoutMinutes(),
            fontSizeSp = app.securePreferences.uiFontSizeSp,
            recordingPrivacy = app.securePreferences.recordingPrivacy,
            skipSetupWizard = app.securePreferences.debugSkipSetupWizard,
            darkMode = app.securePreferences.darkMode,
            repeatEnabled = app.securePreferences.repeatFeatureEnabled,
            passReminderLeadTimes = app.securePreferences.passReminderLeadTimes,
            lipaBillA11yEnabled = AccessibilityHelper.isLipaBillServiceEnabled(ctx),
            simLines = lines,
            preferredSimSubscriptionId = selected,
            needsPhoneStatePermission = needsPerm,
            lockedSources = lockedLedgerSources(region, lines, phoneAccess = !needsPerm),
            walletNumberHints = if (needsPerm) emptyMap() else simWalletNumberHints(region, lines),
            simRegions = simRegions,
            selectableRegions = selectableAppRegions(simRegions, region)
        )
    }
}