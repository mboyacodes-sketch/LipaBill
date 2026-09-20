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
    /** Biometrics and/or device PIN / pattern / password available */
    AVAILABLE,
    /** No lock screen at all — warn but don't hard-block */
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

    /** Fingerprint / face / iris enrolled and usable. */
    fun hasBiometricEnrolled(): Boolean =
        biometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK) ==
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
     * Biometrics when available (with PIN fallback in the system sheet);
     * otherwise device credential only.
     */
    fun allowedAuthenticators(): Int =
        if (hasBiometricEnrolled()) {
            Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL
        } else {
            Authenticators.DEVICE_CREDENTIAL
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

    fun continueWithoutLockScreen() {
        _state.value = AuthUiState.Unlocked
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

    /**
     * Step-up auth for money-moving actions. Always prompts even if the session
     * is already unlocked. Does not change [state] on failure/cancel.
     */
    fun authenticateSensitive(
        activity: FragmentActivity,
        title: String = "Confirm to pay",
        subtitle: String = "LipaBill will open M-Pesa. You enter your PIN on a secure keypad.",
        onSuccess: () -> Unit,
        onCancelOrFail: (String) -> Unit
    ) {
        if (capability() == AuthCapability.NONE) {
            onCancelOrFail("Set a phone PIN or biometric to confirm payments")
            return
        }
        authenticateInternal(
            activity = activity,
            title = title,
            subtitle = subtitle,
            onSuccess = onSuccess,
            onError = onCancelOrFail,
            onCancel = { onCancelOrFail("Authentication cancelled") }
        )
    }

    private fun unlockSubtitle(): String =
        when {
            hasBiometricEnrolled() && hasDeviceCredential() ->
                "Use fingerprint, face, or your phone PIN"
            hasBiometricEnrolled() ->
                "Confirm it's you to view M-Pesa transactions"
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
