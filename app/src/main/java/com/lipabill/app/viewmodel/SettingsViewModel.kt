package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.model.AppRegion
import com.lipabill.app.data.model.MoneySource
import com.lipabill.app.data.model.providerCatalog
import com.lipabill.app.data.model.lockedLedgerSources
import com.lipabill.app.data.model.regionsPresentOnSims
import com.lipabill.app.data.model.selectableAppRegions
import com.lipabill.app.data.model.toggleLedgerSource
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
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
    val darkMode: Boolean = false,
    val repeatEnabled: Boolean = true,
    val lipaBillA11yEnabled: Boolean = false,
    val simLines: List<SimLine> = emptyList(),
    val preferredSimSubscriptionId: Int? = null,
    val needsPhoneStatePermission: Boolean = false,
    /** Wallets with no dial line; caption explains SMS still works without the SIM. */
    val lockedSources: Map<MoneySource, String> = emptyMap(),
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
        // SIM-locked wallets still belong on the ledger (SMS import). Send/Pay stay
        // dial-gated separately; locked copy under the chip explains the missing line.
        val region = app.appRegion.value
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

    fun setRepeatEnabled(enabled: Boolean) {
        app.repeatCoordinator.setFeatureEnabled(enabled)
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
        val enabled = app.enabledSources.value
        if (!needsPerm) {
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
            darkMode = app.securePreferences.darkMode,
            repeatEnabled = app.securePreferences.repeatFeatureEnabled,
            lipaBillA11yEnabled = AccessibilityHelper.isLipaBillServiceEnabled(ctx),
            simLines = lines,
            preferredSimSubscriptionId = selected,
            needsPhoneStatePermission = needsPerm,
            lockedSources = lockedLedgerSources(region, lines, phoneAccess = !needsPerm),
            simRegions = simRegions,
            selectableRegions = selectableAppRegions(simRegions, region)
        )
    }
}