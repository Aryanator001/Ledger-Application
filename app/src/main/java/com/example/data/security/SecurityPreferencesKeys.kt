package com.example.data.security

import androidx.datastore.preferences.core.*

object SecurityPreferencesKeys {
    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val LOCK_METHOD = stringPreferencesKey("lock_method")              // "PIN" | "PASSWORD" | "BIOMETRICS"
    val BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
    val LOCK_AFTER_MS = longPreferencesKey("lock_after_ms")            // 0=Immediately, 60000=1m, 300000=5m, -1=Never
    val REQUIRE_UNLOCK_ON_LAUNCH = booleanPreferencesKey("require_unlock_on_launch")
    val HIDE_BALANCE_WHEN_LOCKED = booleanPreferencesKey("hide_balance_when_locked")
    val HIDE_PREVIEW_IN_SWITCHER = booleanPreferencesKey("hide_preview_in_switcher")
    val SCREENSHOT_PROTECTION = booleanPreferencesKey("screenshot_protection")
    val PIN_HASH = stringPreferencesKey("pin_hash")                    // SHA-256
    val PIN_SALT = stringPreferencesKey("pin_salt")
}
