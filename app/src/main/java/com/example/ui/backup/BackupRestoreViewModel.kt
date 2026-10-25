package com.example.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.UserPreferences
import com.example.data.backup.BackupError
import com.example.data.backup.BackupManager
import com.example.data.backup.BackupOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Loading : BackupUiState
    data object ExportSuccess : BackupUiState
    data object RestoreSuccess : BackupUiState
    data object ExportCsvSuccess : BackupUiState
    data object ImportCsvSuccess : BackupUiState
    data class Error(val error: BackupError) : BackupUiState
}

class BackupRestoreViewModel(
    private val backupManager: BackupManager,
    userPreferences: UserPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    val lastBackupAt: StateFlow<Long?> =
        userPreferences.lastBackupAt.stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun exportBackup(destination: Uri) {
        _uiState.value = BackupUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val outcome = backupManager.exportBackup(destination)) {
                is BackupOutcome.Success -> BackupUiState.ExportSuccess
                is BackupOutcome.Failure -> BackupUiState.Error(outcome.error)
            }
        }
    }

    fun restoreBackup(source: Uri) {
        _uiState.value = BackupUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val outcome = backupManager.restoreBackup(source)) {
                is BackupOutcome.Success -> BackupUiState.RestoreSuccess
                is BackupOutcome.Failure -> BackupUiState.Error(outcome.error)
            }
        }
    }

    fun exportCsv(destination: Uri) {
        _uiState.value = BackupUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val outcome = backupManager.exportCsv(destination)) {
                is BackupOutcome.Success -> BackupUiState.ExportCsvSuccess
                is BackupOutcome.Failure -> BackupUiState.Error(outcome.error)
            }
        }
    }

    fun importCsv(source: Uri) {
        _uiState.value = BackupUiState.Loading
        viewModelScope.launch {
            _uiState.value = when (val outcome = backupManager.importCsv(source)) {
                is BackupOutcome.Success -> BackupUiState.ImportCsvSuccess
                is BackupOutcome.Failure -> BackupUiState.Error(outcome.error)
            }
        }
    }

    fun consumeUiState() {
        _uiState.value = BackupUiState.Idle
    }
}

class BackupRestoreViewModelFactory(
    private val backupManager: BackupManager,
    private val userPreferences: UserPreferences
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return BackupRestoreViewModel(backupManager, userPreferences) as T
    }
}
