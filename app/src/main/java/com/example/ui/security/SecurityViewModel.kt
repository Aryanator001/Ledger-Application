package com.example.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.security.SecurityRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SecurityViewModel(private val repo: SecurityRepository) : ViewModel() {
    val appLockEnabled = repo.appLockEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val lockMethod = repo.lockMethod.stateIn(viewModelScope, SharingStarted.Lazily, "BIOMETRICS")
    val biometricsEnabled = repo.biometricsEnabled.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val lockAfterMs = repo.lockAfterMs.stateIn(viewModelScope, SharingStarted.Lazily, 0L)
    val requireUnlockOnLaunch = repo.requireUnlockOnLaunch.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val hideBalanceWhenLocked = repo.hideBalanceWhenLocked.stateIn(viewModelScope, SharingStarted.Lazily, true)
    val hidePreviewInSwitcher = repo.hidePreviewInSwitcher.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val screenshotProtection = repo.screenshotProtection.stateIn(viewModelScope, SharingStarted.Lazily, false)
    val hasPin = repo.pinHash.map { it != null }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun savePin(pin: String) {
        viewModelScope.launch {
            val salt = java.util.UUID.randomUUID().toString()
            val hash = hashPin(pin, salt)
            repo.setPin(hash, salt)
            repo.setLockMethod("PIN")
            repo.setAppLockEnabled(true)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val hash = repo.pinHash.first()
        val salt = repo.pinSalt.first()
        if (hash == null || salt == null) return false
        return hashPin(pin, salt) == hash
    }

    private fun hashPin(pin: String, salt: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hashedBytes = md.digest((pin + salt).toByteArray(Charsets.UTF_8))
        return hashedBytes.joinToString("") { "%02x".format(it) }
    }

    fun toggleAppLock(v: Boolean) = viewModelScope.launch { repo.setAppLockEnabled(v) }
    fun setLockMethod(v: String) = viewModelScope.launch { repo.setLockMethod(v) }
    fun toggleBiometrics(v: Boolean) = viewModelScope.launch { repo.setBiometricsEnabled(v) }
    fun setLockAfter(ms: Long) = viewModelScope.launch { repo.setLockAfterMs(ms) }
    fun toggleRequireUnlockOnLaunch(v: Boolean) = viewModelScope.launch { repo.setRequireUnlockOnLaunch(v) }
    fun toggleHideBalanceWhenLocked(v: Boolean) = viewModelScope.launch { repo.setHideBalanceWhenLocked(v) }
    fun toggleHidePreviewInSwitcher(v: Boolean) = viewModelScope.launch { repo.setHidePreviewInSwitcher(v) }
    fun toggleScreenshotProtection(v: Boolean) = viewModelScope.launch { repo.setScreenshotProtection(v) }
}
