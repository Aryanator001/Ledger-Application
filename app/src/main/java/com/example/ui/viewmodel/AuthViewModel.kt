package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.AuthResult
import com.example.data.UserSession
import com.example.data.SecurityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val securityStore: SecurityStore
) : ViewModel() {

    val activeSession: StateFlow<UserSession?> = authRepository.activeSession

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    init {
        val cachedSession = securityStore.getCachedSession()
        if (cachedSession != null) {
            authRepository.restoreSessionFromCache(cachedSession)
        }
    }

    fun signInWithGoogle(context: Context, serverClientId: String, onResult: (Boolean) -> Unit) {
        _authError.value = null
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(context, serverClientId)) {
                is AuthResult.Success -> onResult(true)
                is AuthResult.Error -> {
                    _authError.value = result.message
                    onResult(false)
                }
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun signInWithMockDevUser(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val session = authRepository.signInWithMockDevUser()
            onResult(true)
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authRepository.signOut(context)
        }
    }
}

class AuthViewModelFactory(
    private val authRepository: AuthRepository,
    private val securityStore: SecurityStore
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(authRepository, securityStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
