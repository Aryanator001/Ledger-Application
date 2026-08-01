package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SecurityStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = createEncryptedPrefs(context, masterKey)

    companion object {
        private fun createEncryptedPrefs(
            context: Context,
            masterKey: MasterKey
        ): android.content.SharedPreferences {
            // On reinstall, stale ciphertext on disk can't be decrypted by the new Keystore key.
            // Wipe the file and retry so the app launches instead of crashing.
            return try {
                EncryptedSharedPreferences.create(
                    context,
                    "security_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                context.deleteSharedPreferences("security_prefs")
                EncryptedSharedPreferences.create(
                    context,
                    "security_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        }
    }

    private val _isAppLockEnabled = MutableStateFlow(sharedPreferences.getBoolean("app_lock_enabled", false))
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled

    private val _lockMethod = MutableStateFlow(sharedPreferences.getString("lock_method", "Biometrics") ?: "Biometrics")
    val lockMethod: StateFlow<String> = _lockMethod // "Biometrics", "PIN", "Both"

    private val _isBiometricsEnabled = MutableStateFlow(sharedPreferences.getBoolean("biometrics_enabled", true))
    val isBiometricsEnabled: StateFlow<Boolean> = _isBiometricsEnabled

    private val _isPinEnabled = MutableStateFlow(sharedPreferences.getBoolean("pin_enabled", false))
    val isPinEnabled: StateFlow<Boolean> = _isPinEnabled

    private val _lockAfter = MutableStateFlow(sharedPreferences.getLong("lock_after", 0L))
    val lockAfter: StateFlow<Long> = _lockAfter // 0 = Immediately, 60000 = 1 min, etc.

    private val _requireUnlockOnLaunch = MutableStateFlow(sharedPreferences.getBoolean("require_unlock_launch", true))
    val requireUnlockOnLaunch: StateFlow<Boolean> = _requireUnlockOnLaunch

    private val _hideBalanceWhenLocked = MutableStateFlow(sharedPreferences.getBoolean("hide_balance_locked", true))
    val hideBalanceWhenLocked: StateFlow<Boolean> = _hideBalanceWhenLocked

    private val _hideFromAppSwitcher = MutableStateFlow(sharedPreferences.getBoolean("hide_app_switcher", false))
    val hideFromAppSwitcher: StateFlow<Boolean> = _hideFromAppSwitcher

    private val _screenshotProtection = MutableStateFlow(sharedPreferences.getBoolean("screenshot_protection", false))
    val screenshotProtection: StateFlow<Boolean> = _screenshotProtection

    fun setAppLockEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("app_lock_enabled", enabled).apply()
        _isAppLockEnabled.value = enabled
    }

    fun setLockMethod(method: String) {
        sharedPreferences.edit().putString("lock_method", method).apply()
        _lockMethod.value = method
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("biometrics_enabled", enabled).apply()
        _isBiometricsEnabled.value = enabled
    }

    fun setPinEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("pin_enabled", enabled).apply()
        _isPinEnabled.value = enabled
    }

    fun savePin(pin: String) {
        // EncryptedSharedPreferences encrypts the value, safe to store directly
        sharedPreferences.edit().putString("app_pin", pin).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val saved = sharedPreferences.getString("app_pin", null)
        return saved == pin
    }

    fun hasPin(): Boolean {
        return sharedPreferences.getString("app_pin", null) != null
    }

    fun setLockAfter(duration: Long) {
        sharedPreferences.edit().putLong("lock_after", duration).apply()
        _lockAfter.value = duration
    }

    fun setRequireUnlockOnLaunch(require: Boolean) {
        sharedPreferences.edit().putBoolean("require_unlock_launch", require).apply()
        _requireUnlockOnLaunch.value = require
    }

    fun setHideBalanceWhenLocked(hide: Boolean) {
        sharedPreferences.edit().putBoolean("hide_balance_locked", hide).apply()
        _hideBalanceWhenLocked.value = hide
    }

    fun setHideFromAppSwitcher(hide: Boolean) {
        sharedPreferences.edit().putBoolean("hide_app_switcher", hide).apply()
        _hideFromAppSwitcher.value = hide
    }

    fun setScreenshotProtection(protect: Boolean) {
        sharedPreferences.edit().putBoolean("screenshot_protection", protect).apply()
        _screenshotProtection.value = protect
    }

    fun cacheSession(uid: String, displayName: String, email: String, photoUrl: String?, idToken: String?) {
        sharedPreferences.edit()
            .putString("cached_uid", uid)
            .putString("cached_display_name", displayName)
            .putString("cached_email", email)
            .putString("cached_photo_url", photoUrl)
            .putString("cached_id_token", idToken)
            .apply()
    }

    fun getCachedSession(): com.example.data.UserSession? {
        val uid = sharedPreferences.getString("cached_uid", null) ?: return null
        val displayName = sharedPreferences.getString("cached_display_name", "User") ?: "User"
        val email = sharedPreferences.getString("cached_email", "") ?: ""
        val photoUrl = sharedPreferences.getString("cached_photo_url", null)
        val idToken = sharedPreferences.getString("cached_id_token", null)
        return com.example.data.UserSession(uid, displayName, email, photoUrl, idToken)
    }

    fun clearCachedSession() {
        sharedPreferences.edit()
            .remove("cached_uid")
            .remove("cached_display_name")
            .remove("cached_email")
            .remove("cached_photo_url")
            .remove("cached_id_token")
            .apply()
    }
}
