package com.lipabill.app.auth

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.lipabill.app.data.prefs.SecurePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AuthCapability {
    /** Class 3 biometrics and/or a device PIN, pattern, or password. */
    AVAILABLE,
    /** No strong biometric and no device credential. The ledger stays locked. */
    NONE
}

sealed class AuthUiState {
    data object Locked : AuthUiState()
    data object Unlocked : AuthUiState()
    data object LockScreenMissing : AuthUiState()
}

/**
 * Gates the app behind BiometricPrompt.
 *
 * Prefer biometrics when enrolled; otherwise fall back to the phone's PIN,
 * pattern, or password ([Authenticators.DEVICE_CREDENTIAL]).
 * Re-locks on cold start and after [SecurePreferences.authTimeoutMillis] in background.
 */
class AuthManager(
    context: Context,
    private val securePreferences: SecurePreferences
) {
    private val appContext = context.applicationContext
    private val biometricManager = BiometricManager.from(appContext)
    private val keyguardManager =
        appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Locked)
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun capability(): AuthCapability =
        if (hasBiometricEnrolled() || hasDeviceCredential()) {
            AuthCapability.AVAILABLE
        } else {
            AuthCapability.NONE
        }

    /** Class 3 fingerprint / face enrolled and usable. Class 2 biometrics do not unlock the ledger. */
    fun hasBiometricEnrolled(): Boolean =
        biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Phone PIN, pattern, or password is set. */
    fun hasDeviceCredential(): Boolean {
        if (keyguardManager.isDeviceSecure) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return biometricManager.canAuthenticate(Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_SUCCESS
        }
        return false
    }

    /**
     * Class 3 biometrics, with the device PIN as fallback on Android 11+.
     * Older releases cannot combine the two in one prompt.
     */
    fun allowedAuthenticators(): Int {
        val strong = hasBiometricEnrolled()
        val credential = hasDeviceCredential()
        val canCombine = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        return when {
            strong && credential && canCombine ->
                Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
            credential && !strong -> Authenticators.DEVICE_CREDENTIAL
            strong -> Authenticators.BIOMETRIC_STRONG
            else -> Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun onColdStart() {
        if (!securePreferences.authRequired) {
            _state.value = AuthUiState.Unlocked
            return
        }
        when (capability()) {
            AuthCapability.AVAILABLE -> _state.value = AuthUiState.Locked
            AuthCapability.NONE -> _state.value = AuthUiState.LockScreenMissing
        }
    }

    fun onAppBackgrounded() {
        securePreferences.lastBackgroundedAt = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (!securePreferences.authRequired) {
            _state.value = AuthUiState.Unlocked
            return
        }
        if (capability() == AuthCapability.NONE) {
            _state.value = AuthUiState.LockScreenMissing
            return
        }
        if (_state.value is AuthUiState.Unlocked) {
            val elapsed = System.currentTimeMillis() - securePreferences.lastBackgroundedAt
            if (securePreferences.lastBackgroundedAt > 0L &&
                elapsed >= securePreferences.authTimeoutMillis
            ) {
                _state.value = AuthUiState.Locked
            }
        } else if (_state.value !is AuthUiState.LockScreenMissing) {
            _state.value = AuthUiState.Locked
        }
    }

    fun lockNow() {
        if (capability() == AuthCapability.NONE) {
            _state.value = AuthUiState.LockScreenMissing
        } else {
            _state.value = AuthUiState.Locked
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        onError: (String) -> Unit = {}
    ) {
        authenticateInternal(
            activity = activity,
            title = "Unlock LipaBill",
            subtitle = unlockSubtitle(),
            onSuccess = { _state.value = AuthUiState.Unlocked },
            onError = onError,
            onCancel = {}
        )
    }

    private fun unlockSubtitle(): String =
        when {
            hasBiometricEnrolled() && hasDeviceCredential() ->
                "Use fingerprint, face, or your phone PIN"
            hasBiometricEnrolled() ->
                "Confirm it's you to see your money"
            else ->
                "Enter your phone PIN, pattern, or password"
        }

    private fun authenticateInternal(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        if (capability() == AuthCapability.NONE) {
            _state.value = AuthUiState.LockScreenMissing
            onCancel()
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // Stay waiting; user can retry within the system sheet.
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators())
            .build()

        prompt.authenticate(info)
    }

    fun setTimeoutMinutes(minutes: Int) {
        securePreferences.authTimeoutMillis = minutes.coerceIn(1, 60) * 60_000L
    }

    fun timeoutMinutes(): Int =
        (securePreferences.authTimeoutMillis / 60_000L).toInt().coerceAtLeast(1)
}
