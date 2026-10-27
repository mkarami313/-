package com.hamraheteyf.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hamraheteyf.app.data.DataResult
import com.hamraheteyf.app.data.model.AccountType
import com.hamraheteyf.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    fun login(email: String, password: String) {
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            when (val r = auth.login(email, password)) {
                is DataResult.Success -> _state.value = AuthUiState() // root switches automatically
                is DataResult.Error -> _state.value = AuthUiState(error = r.message)
            }
        }
    }

    fun register(
        displayName: String,
        email: String,
        password: String,
        accountType: AccountType,
        acceptedTerms: Boolean,
    ) {
        _state.value = AuthUiState(loading = true)
        viewModelScope.launch {
            when (val r = auth.register(displayName, email, password, accountType, acceptedTerms)) {
                is DataResult.Success -> _state.value = AuthUiState()
                is DataResult.Error -> _state.value = AuthUiState(error = r.message)
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            auth.sendPasswordReset(email)
            _state.value = _state.value.copy(info = "اگر این ایمیل ثبت شده باشد، راهنمای بازیابی ارسال می‌شود.")
        }
    }
}
