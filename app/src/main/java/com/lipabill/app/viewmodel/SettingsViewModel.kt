package com.lipabill.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lipabill.app.LipaBillApp
import com.lipabill.app.data.local.entity.RepeatAttemptEntity
import com.lipabill.app.ussd.AccessibilityHelper
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
        _ui.value = buildState()
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

    fun setRepeatEnabled(enabled: Boolean) {
        app.repeatCoordinator.setFeatureEnabled(enabled)
        refresh()
    }

    fun setPreferredSim(subscriptionId: Int) {
        app.securePreferences.preferredSimSubscriptionId = subscriptionId
        refresh()
    }

    fun lockNow() {
        auth.lockNow()
    }

    fun openAccessibilitySettings() {
        AccessibilityHelper.openAppAccessibilityDetails(getApplication())
    }

    private fun buildState(): SettingsUiState {
        val ctx = getApplication<Application>()
        val needsPerm = !SimLineHelper.hasPhoneStatePermission(ctx)
        val lines = if (needsPerm) emptyList() else SimLineHelper.listActiveLines(ctx)
        val preferredRaw = app.securePreferences.preferredSimSubscriptionId
        // Auto-lock single SIM as permanent default
        if (preferredRaw < 0 && lines.size == 1) {
            app.securePreferences.preferredSimSubscriptionId = lines.first().subscriptionId
        }
        val preferred = app.securePreferences.preferredSimSubscriptionId
        val selected = lines.firstOrNull { it.subscriptionId == preferred }?.subscriptionId
            ?: preferred.takeIf { it >= 0 }
        return SettingsUiState(
            timeoutMinutes = auth.timeoutMinutes(),
            fontSizeSp = app.securePreferences.uiFontSizeSp,
            alwaysShowBalance = app.securePreferences.alwaysShowBalance,
            repeatEnabled = app.securePreferences.repeatFeatureEnabled,
            lipaBillA11yEnabled = AccessibilityHelper.isLipaBillServiceEnabled(ctx),
            simLines = lines,
            preferredSimSubscriptionId = selected,
            needsPhoneStatePermission = needsPerm
        )
    }
}