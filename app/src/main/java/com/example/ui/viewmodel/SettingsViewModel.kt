package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.widget.Toast
import com.example.R

class SettingsViewModel(
    application: Application,
    private val userPreferences: UserPreferences,
    private val repository: com.example.data.TransactionRepository
) : AndroidViewModel(application) {

    fun clearCache() {
        viewModelScope.launch {
            try {
                getApplication<Application>().cacheDir.deleteRecursively()
                getApplication<Application>().externalCacheDir?.deleteRecursively()
                Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.settings_cache_cleared), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(getApplication(), getApplication<Application>().getString(R.string.settings_cache_clear_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val allAccounts: StateFlow<List<com.example.data.Account>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultAccountId: StateFlow<String?> = userPreferences.defaultAccountId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val theme: StateFlow<String> = userPreferences.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "System")

    val firstDayOfWeek: StateFlow<String> = userPreferences.firstDayOfWeek
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Monday")

    val dateFormat: StateFlow<String> = userPreferences.dateFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DD/MM/YYYY")

    val timeFormat: StateFlow<String> = userPreferences.timeFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "12h")

    val numberFormat: StateFlow<String> = userPreferences.numberFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "International")

    val decimalPlaces: StateFlow<Int> = userPreferences.decimalPlaces
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val showBalance: StateFlow<Boolean> = userPreferences.showBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val autoCategorise: StateFlow<Boolean> = userPreferences.autoCategorise
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val currencyCode: StateFlow<String> = userPreferences.currencyCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "INR")

    val currencySymbol: StateFlow<String> = userPreferences.currencySymbol
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "₹")

    val appLanguage: StateFlow<String> = userPreferences.appLanguage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    fun setTheme(value: String) { viewModelScope.launch { userPreferences.setTheme(value) } }
    fun setFirstDayOfWeek(value: String) { viewModelScope.launch { userPreferences.setFirstDayOfWeek(value) } }
    fun setDateFormat(value: String) { viewModelScope.launch { userPreferences.setDateFormat(value) } }
    fun setTimeFormat(value: String) { viewModelScope.launch { userPreferences.setTimeFormat(value) } }
    fun setNumberFormat(value: String) { viewModelScope.launch { userPreferences.setNumberFormat(value) } }
    fun setDecimalPlaces(value: Int) { viewModelScope.launch { userPreferences.setDecimalPlaces(value) } }
    fun setShowBalance(value: Boolean) { viewModelScope.launch { userPreferences.setShowBalance(value) } }
    fun setDefaultAccountId(value: String) { viewModelScope.launch { userPreferences.setDefaultAccountId(value) } }
    fun setAutoCategorise(value: Boolean) { viewModelScope.launch { userPreferences.setAutoCategorise(value) } }
    fun setCurrency(code: String, symbol: String) { viewModelScope.launch { userPreferences.setCurrency(code, symbol) } }
    fun setAppLanguage(code: String) { viewModelScope.launch { userPreferences.setAppLanguage(code) } }
}

class SettingsViewModelFactory(
    private val application: Application,
    private val userPreferences: UserPreferences,
    private val repository: com.example.data.TransactionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(application, userPreferences, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
