package com.hamraheteyf.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.AccountType
import com.hamraheteyf.app.data.repository.AuthRepository
import com.hamraheteyf.app.security.CredentialVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class AuthViewModel(
    private val auth: AuthRepository,
    private val vault: CredentialVault? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    /**
     * Signs in. When [remember] is set and the sign-in succeeds, the credentials are saved
     * (encrypted) for later fingerprint sign-in. The save runs here, in the ViewModel scope,
     * because the login screen is torn down the instant the session becomes active.
     */
    fun login(email: String, password: String, remember: Boolean = false, fromBiometric: Boolean = false) {
        if (_state.value.loading) return
        _state.value = AuthUiState(loading = true)
        // NonCancellable (as in register): auth.login suspends inside onAuthenticated (first refresh) AFTER the
        // session went active; if the torn-down screen cancelled it there, polling, reminders, push registration
        // and the fingerprint save below would all be skipped.
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
            when (val r = auth.login(email.trim(), password)) {
                is DataResult.Success -> {
                    // Off the main thread: a v2 save generates a fresh Keystore RSA key (up to seconds in a TEE).
                    // A fingerprint sign-in just used the saved credentials, so there is nothing new to save.
                    if (!fromBiometric) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { if (remember) vault?.save(email.trim(), password) else vault?.clear() }
                    }
                    _state.value = AuthUiState() // root switches automatically
                }
                is DataResult.Error -> {
                    // Saved credentials the server rejects (password reset elsewhere, account gone) would otherwise
                    // auto-prompt and fail on every launch. A transport error says nothing about them: keep.
                    if (fromBiometric && !r.message.startsWith("خطای ارتباط")) runCatching { vault?.clear() }
                    _state.value = AuthUiState(error = r.message)
                }
            }
        }
    }

    /** «شروع بدونِ ثبت‌نام»: حسابِ همین دستگاه (اگر قبلاً ساخته شده، همان برمی‌گردد). */
    fun startWithDevice(device: String, displayName: String) {
        if (_state.value.loading) return
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
            _state.value = when (val r = auth.deviceLogin(device, displayName.trim())) {
                is DataResult.Success -> AuthUiState()
                is DataResult.Error -> AuthUiState(error = r.message)
            }
        }
    }

    fun register(
        displayName: String,
        email: String,
        password: String,
        accountType: AccountType,
        acceptedTerms: Boolean,
        onSuccess: () -> Unit = {},
    ) {
        _state.value = AuthUiState(loading = true)
        // NonCancellable: the register screen is torn down the moment the session becomes active,
        // which would otherwise cancel this coroutine before onSuccess runs.
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
            when (val r = auth.register(displayName, email, password, accountType, acceptedTerms)) {
                is DataResult.Success -> { onSuccess(); _state.value = AuthUiState() }
                is DataResult.Error -> _state.value = AuthUiState(error = r.message)
            }
        }
    }

    fun resetPassword(email: String) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null, info = null)
        viewModelScope.launch {
            _state.value = when (val r = auth.sendPasswordReset(email)) {
                is DataResult.Success -> _state.value.copy(loading = false, info = "اگر این ایمیل ثبت شده باشد، راهنمای بازیابی ارسال می‌شود.")
                // A transport failure used to be reported as "sent" — the user waited for an email that never left.
                is DataResult.Error -> _state.value.copy(loading = false, error = r.message)
            }
        }
    }
}
