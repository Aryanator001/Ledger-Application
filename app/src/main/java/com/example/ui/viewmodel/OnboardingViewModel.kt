package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _currentSlide = MutableStateFlow(0)
    val currentSlide: StateFlow<Int> = _currentSlide.asStateFlow()

    fun goToSlide(index: Int) {
        _currentSlide.value = index.coerceIn(0, SLIDE_COUNT - 1)
    }

    fun nextSlide() {
        goToSlide(_currentSlide.value + 1)
    }

    fun skipToLast() {
        goToSlide(SLIDE_COUNT - 1)
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferences.setHasSeenOnboarding(true)
        }
    }

    companion object {
        const val SLIDE_COUNT = 4
    }
}

class OnboardingViewModelFactory(
    private val userPreferences: UserPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnboardingViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OnboardingViewModel(userPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
