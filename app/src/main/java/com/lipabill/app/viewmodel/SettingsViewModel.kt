package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
import com.lipabill.app.ui.permissions.AccessibilityPreferred
import com.lipabill.app.ussd.AccessibilityHelper
import com.lipabill.app.ui.permissions.SideloadRestrictedSettings
import com.lipabill.app.ussd.SimLine
import com.lipabill.app.ussd.SimLineHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

data class SettingsUiState(
    val timeoutMinutes: Int = 2,
    val fontSizeSp: Int = 12,
    val alwaysShowBalance: Boolean = false,
    val favouritesSectionEnabled: Boolean = true,
    val repeatEnabled: Boolean = true,
    val lipaBillA11yEnabled: Boolean = false,
    val simLines: List<SimLine> = emptyList(),
    val preferredSimSubscriptionId: Int? = null,
    val needsPhoneStatePermission: Boolean = false
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

    fun setTimeoutMinutes(minutes: Int) {
        auth.setTimeoutMinutes(minutes)
        refresh()
    }

    fun setFontSizeSp(size: Int) {
        app.setFontSizeSp(size)
        refresh()
    }

    fun setAlwaysShowBalance(enabled: Boolean) {
        app.setAlwaysShowBalance(enabled)
        refresh()
    }

    fun setFavouritesSectionEnabled(enabled: Boolean) {
        app.setFavouritesSectionEnabled(enabled)
        refresh()
    }

    fun setRepeatEnabled(enabled: Boolean) {
        app.repeatCoordinator.setFeatureEnabled(enabled)
        refresh()
    }

    fun setPreferredSim(subscriptionId: Int) {
        val lines = SimLineHelper.listActiveLines(getApplication())
        val line = lines.firstOrNull { it.subscriptionId == subscriptionId } ?: return
        if (!line.isSafaricom) return
        app.securePreferences.preferredSimSubscriptionId = subscriptionId
        refresh()
    }

    fun lockNow() {
        auth.lockNow()
    }

    fun openAccessibilitySettings() {
        AccessibilityHelper.openAppAccessibilityDetails(getApplication())
    }

    /** Profile Turn on/off — remember intent so updates can prompt re-enable. */
    fun onAccessibilityToggleConfirmed(currentlyEnabled: Boolean) {
        if (currentlyEnabled) {
            AccessibilityPreferred.markUserTurningOff(app.securePreferences)
        } else {
            AccessibilityPreferred.markUserTurningOn(app.securePreferences)
        }
        openAccessibilitySettings()
    }

    fun openAppInfoForRestrictedSettings() {
        SideloadRestrictedSettings.openAppInfo(getApplication())
    }

    private fun buildState(): SettingsUiState {
        val ctx = getApplication<Application>()
        val needsPerm = !SimLineHelper.hasPhoneStatePermission(ctx)
        val lines = if (needsPerm) emptyList() else SimLineHelper.listActiveLines(ctx)
        if (!needsPerm) {
            SimLineHelper.ensureSafaricomPreferred(app.securePreferences, lines)
        }
        val preferred = app.securePreferences.preferredSimSubscriptionId
        val selected = lines.firstOrNull { it.subscriptionId == preferred && it.isSafaricom }
            ?.subscriptionId
            ?: SimLineHelper.findSafaricom(lines)?.subscriptionId
        return SettingsUiState(
            timeoutMinutes = auth.timeoutMinutes(),
            fontSizeSp = app.securePreferences.uiFontSizeSp,
            alwaysShowBalance = app.securePreferences.alwaysShowBalance,
            favouritesSectionEnabled = app.securePreferences.favouritesSectionEnabled,
            repeatEnabled = app.securePreferences.repeatFeatureEnabled,
            lipaBillA11yEnabled = AccessibilityHelper.isLipaBillServiceEnabled(ctx),
            simLines = lines,
            preferredSimSubscriptionId = selected,
            needsPhoneStatePermission = needsPerm
        )
    }
}