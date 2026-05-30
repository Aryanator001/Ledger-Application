package com.example.data.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.example.data.security.SecurityPreferencesKeys.APP_LOCK_ENABLED
import com.example.data.security.SecurityPreferencesKeys.BIOMETRICS_ENABLED
import com.example.data.security.SecurityPreferencesKeys.HIDE_BALANCE_WHEN_LOCKED
import com.example.data.security.SecurityPreferencesKeys.HIDE_PREVIEW_IN_SWITCHER
import com.example.data.security.SecurityPreferencesKeys.LOCK_AFTER_MS
import com.example.data.security.SecurityPreferencesKeys.LOCK_METHOD
import com.example.data.security.SecurityPreferencesKeys.PIN_HASH
import com.example.data.security.SecurityPreferencesKeys.PIN_SALT
import com.example.data.security.SecurityPreferencesKeys.REQUIRE_UNLOCK_ON_LAUNCH
import com.example.data.security.SecurityPreferencesKeys.SCREENSHOT_PROTECTION
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SecurityRepository(private val dataStore: DataStore<Preferences>) {
    val appLockEnabled: Flow<Boolean> = dataStore.data.map { it[APP_LOCK_ENABLED] ?: false }
    val lockMethod: Flow<String> = dataStore.data.map { it[LOCK_METHOD] ?: "BIOMETRICS" }
    val biometricsEnabled: Flow<Boolean> = dataStore.data.map { it[BIOMETRICS_ENABLED] ?: false }
    val lockAfterMs: Flow<Long> = dataStore.data.map { it[LOCK_AFTER_MS] ?: 0L }
    val requireUnlockOnLaunch: Flow<Boolean> = dataStore.data.map { it[REQUIRE_UNLOCK_ON_LAUNCH] ?: true }
    val hideBalanceWhenLocked: Flow<Boolean> = dataStore.data.map { it[HIDE_BALANCE_WHEN_LOCKED] ?: true }
    val hidePreviewInSwitcher: Flow<Boolean> = dataStore.data.map { it[HIDE_PREVIEW_IN_SWITCHER] ?: false }
    val screenshotProtection: Flow<Boolean> = dataStore.data.map { it[SCREENSHOT_PROTECTION] ?: false }
    val pinHash: Flow<String?> = dataStore.data.map { it[PIN_HASH] }
    val pinSalt: Flow<String?> = dataStore.data.map { it[PIN_SALT] }

    suspend fun setPin(hash: String, salt: String) {
        dataStore.edit { 
            it[PIN_HASH] = hash
            it[PIN_SALT] = salt
        }
    }

    suspend fun setAppLockEnabled(v: Boolean) = dataStore.edit { it[APP_LOCK_ENABLED] = v }
    suspend fun setLockMethod(v: String) = dataStore.edit { it[LOCK_METHOD] = v }
    suspend fun setBiometricsEnabled(v: Boolean) = dataStore.edit { it[BIOMETRICS_ENABLED] = v }
    suspend fun setLockAfterMs(v: Long) = dataStore.edit { it[LOCK_AFTER_MS] = v }
    suspend fun setRequireUnlockOnLaunch(v: Boolean) = dataStore.edit { it[REQUIRE_UNLOCK_ON_LAUNCH] = v }
    suspend fun setHideBalanceWhenLocked(v: Boolean) = dataStore.edit { it[HIDE_BALANCE_WHEN_LOCKED] = v }
    suspend fun setHidePreviewInSwitcher(v: Boolean) = dataStore.edit { it[HIDE_PREVIEW_IN_SWITCHER] = v }
    suspend fun setScreenshotProtection(v: Boolean) = dataStore.edit { it[SCREENSHOT_PROTECTION] = v }
}
