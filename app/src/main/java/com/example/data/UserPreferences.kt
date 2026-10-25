package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class UserPreferences(private val context: Context) {
    companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME = stringPreferencesKey("theme") // "System", "Light", "Dark"
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week") // "Sunday", "Monday"
        val DATE_FORMAT = stringPreferencesKey("date_format") // "DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD"
        val TIME_FORMAT = stringPreferencesKey("time_format") // "12h", "24h"
        val NUMBER_FORMAT = stringPreferencesKey("number_format") // "Indian", "International"
        val DECIMAL_PLACES = intPreferencesKey("decimal_places") // 0, 1, 2
        val SHOW_BALANCE = booleanPreferencesKey("show_balance")
        val DEFAULT_ACCOUNT_ID = stringPreferencesKey("default_account_id")
        val AUTO_CATEGORISE = booleanPreferencesKey("auto_categorise")
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val CATEGORIES_SEEDED = booleanPreferencesKey("categories_seeded")
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
        val ACTIVE_LEDGER_SCOPE_ID = stringPreferencesKey("active_ledger_scope_id")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }

    val activeUserId: Flow<String?> = context.dataStore.data.map { it[ACTIVE_USER_ID] }
    val activeLedgerScopeId: Flow<String?> = context.dataStore.data.map { it[ACTIVE_LEDGER_SCOPE_ID] }
    val appLanguage: Flow<String> = context.dataStore.data.map { it[APP_LANGUAGE] ?: "en" }

    val theme: Flow<String> = context.dataStore.data.map { it[THEME] ?: "System" }
    val firstDayOfWeek: Flow<String> = context.dataStore.data.map { it[FIRST_DAY_OF_WEEK] ?: "Monday" }
    val dateFormat: Flow<String> = context.dataStore.data.map { it[DATE_FORMAT] ?: "DD/MM/YYYY" }
    val timeFormat: Flow<String> = context.dataStore.data.map { it[TIME_FORMAT] ?: "12h" }
    val numberFormat: Flow<String> = context.dataStore.data.map { it[NUMBER_FORMAT] ?: "International" }
    val decimalPlaces: Flow<Int> = context.dataStore.data.map { it[DECIMAL_PLACES] ?: 2 }
    val showBalance: Flow<Boolean> = context.dataStore.data.map { it[SHOW_BALANCE] ?: true }
    val defaultAccountId: Flow<String?> = context.dataStore.data.map { it[DEFAULT_ACCOUNT_ID] }
    val autoCategorise: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CATEGORISE] ?: true }
    val currencyCode: Flow<String> = context.dataStore.data.map { it[CURRENCY_CODE] ?: "INR" }
    val currencySymbol: Flow<String> = context.dataStore.data.map { it[CURRENCY_SYMBOL] ?: "₹" }
    val categoriesSeeded: Flow<Boolean> = context.dataStore.data.map { it[CATEGORIES_SEEDED] ?: false }

    suspend fun setTheme(value: String) { context.dataStore.edit { it[THEME] = value } }
    suspend fun setAppLanguage(code: String) { context.dataStore.edit { it[APP_LANGUAGE] = code } }
    suspend fun setFirstDayOfWeek(value: String) { context.dataStore.edit { it[FIRST_DAY_OF_WEEK] = value } }
    suspend fun setDateFormat(value: String) { context.dataStore.edit { it[DATE_FORMAT] = value } }
    suspend fun setTimeFormat(value: String) { context.dataStore.edit { it[TIME_FORMAT] = value } }
    suspend fun setNumberFormat(value: String) { context.dataStore.edit { it[NUMBER_FORMAT] = value } }
    suspend fun setDecimalPlaces(value: Int) { context.dataStore.edit { it[DECIMAL_PLACES] = value } }
    suspend fun setShowBalance(value: Boolean) { context.dataStore.edit { it[SHOW_BALANCE] = value } }
    suspend fun setDefaultAccountId(value: String) { context.dataStore.edit { it[DEFAULT_ACCOUNT_ID] = value } }
    suspend fun setAutoCategorise(value: Boolean) { context.dataStore.edit { it[AUTO_CATEGORISE] = value } }
    suspend fun setCategoriesSeeded(value: Boolean) { context.dataStore.edit { it[CATEGORIES_SEEDED] = value } }
    
    suspend fun setActiveUserId(value: String?) {
        context.dataStore.edit { 
            if (value == null) it.remove(ACTIVE_USER_ID)
            else it[ACTIVE_USER_ID] = value
        }
    }

    suspend fun setActiveLedgerScopeId(value: String?) {
        context.dataStore.edit {
            if (value == null) it.remove(ACTIVE_LEDGER_SCOPE_ID)
            else it[ACTIVE_LEDGER_SCOPE_ID] = value
        }
    }

    suspend fun setCurrency(code: String, symbol: String) {
        context.dataStore.edit {
            it[CURRENCY_CODE] = code
            it[CURRENCY_SYMBOL] = symbol
        }
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ONBOARDING_COMPLETED] ?: false
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    val hasSeenOnboarding: Flow<Boolean> = context.dataStore.data
        .map { it[HAS_SEEN_ONBOARDING] ?: false }

    suspend fun setHasSeenOnboarding(seen: Boolean) {
        context.dataStore.edit { it[HAS_SEEN_ONBOARDING] = seen }
    }

    val lastBackupAt: Flow<Long?> = context.dataStore.data.map { it[LAST_BACKUP_AT] }

    suspend fun setLastBackupAt(value: Long) {
        context.dataStore.edit { it[LAST_BACKUP_AT] = value }
    }
}
