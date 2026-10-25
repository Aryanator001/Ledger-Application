package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.backup.BackupRestoreViewModel
import com.example.ui.backup.BackupUiState
import com.example.data.backup.BackupError
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showClearDataDialog by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportCsvUri by remember { mutableStateOf<Uri?>(null) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val isLoading = uiState is BackupUiState.Loading

    // Pre-compute snackbar strings for use inside lambdas
    val requiresProMsg = stringResource(R.string.backup_restore_requires_pro_msg)
    val dataDeletedMsg = stringResource(R.string.backup_restore_deleted_msg)
    val exportSuccessMsg = stringResource(R.string.backup_restore_export_success_msg)
    val restoreSuccessMsg = stringResource(R.string.backup_restore_restore_success_msg)
    val exportCsvSuccessMsg = stringResource(R.string.backup_restore_export_csv_success_msg)
    val importCsvSuccessMsg = stringResource(R.string.backup_restore_import_csv_success_msg)
    val ioErrorMsg = stringResource(R.string.backup_restore_io_error_msg)
    val invalidFileMsg = stringResource(R.string.backup_restore_invalid_file_msg)
    val unsupportedVersionMsg = stringResource(R.string.backup_restore_unsupported_version_msg)
    val noActiveLedgerMsg = stringResource(R.string.backup_restore_no_active_ledger_msg)
    val malformedCsvMsg = stringResource(R.string.backup_restore_malformed_csv_msg)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        android.util.Log.d("BackupRestoreScreen", "CreateDocument result: $uri")
        uri?.let { viewModel.exportBackup(it) }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        android.util.Log.d("BackupRestoreScreen", "OpenDocument result: $uri")
        uri?.let { pendingRestoreUri = it }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        android.util.Log.d("BackupRestoreScreen", "CreateDocument(csv) result: $uri")
        uri?.let { viewModel.exportCsv(it) }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        android.util.Log.d("BackupRestoreScreen", "OpenDocument(csv) result: $uri")
        uri?.let { pendingImportCsvUri = it }
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is BackupUiState.ExportSuccess -> {
                snackbarHostState.showSnackbar(exportSuccessMsg)
                viewModel.consumeUiState()
            }
            is BackupUiState.RestoreSuccess -> {
                snackbarHostState.showSnackbar(restoreSuccessMsg)
                viewModel.consumeUiState()
            }
            is BackupUiState.ExportCsvSuccess -> {
                snackbarHostState.showSnackbar(exportCsvSuccessMsg)
                viewModel.consumeUiState()
            }
            is BackupUiState.ImportCsvSuccess -> {
                snackbarHostState.showSnackbar(importCsvSuccessMsg)
                viewModel.consumeUiState()
            }
            is BackupUiState.Error -> {
                val msg = when (state.error) {
                    BackupError.IO_ERROR -> ioErrorMsg
                    BackupError.MALFORMED_JSON -> invalidFileMsg
                    BackupError.UNSUPPORTED_SCHEMA_VERSION -> unsupportedVersionMsg
                    BackupError.NO_ACTIVE_LEDGER_SCOPE -> noActiveLedgerMsg
                    BackupError.MALFORMED_CSV -> malformedCsvMsg
                }
                snackbarHostState.showSnackbar(msg)
                viewModel.consumeUiState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_restore_title), style = AppTypography.display.copy(fontSize = 20.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.backup_restore_back_desc))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.appColors.bgBase,
                    titleContentColor = MaterialTheme.appColors.textPrimary,
                    navigationIconContentColor = MaterialTheme.appColors.textPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.appColors.bgBase
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            
            Spacer(modifier = Modifier.height(16.dp))

            // SECTION: "Backup"
            SettingsSectionHeader(stringResource(R.string.backup_restore_section_backup))
            Card(
                shape = AppShapes.xl,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    BackupRestoreItem(
                        icon = Icons.Default.Save,
                        title = stringResource(R.string.backup_restore_export_all_title),
                        subtitle = stringResource(R.string.backup_restore_export_all_sub),
                        enabled = !isLoading,
                        onClick = {
                            exportLauncher.launch("ledger_backup_${System.currentTimeMillis()}.json")
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)

                    BackupRestoreItem(
                        icon = Icons.Default.Description,
                        title = stringResource(R.string.backup_restore_export_csv_title),
                        subtitle = stringResource(R.string.backup_restore_export_csv_sub),
                        enabled = !isLoading,
                        onClick = {
                            exportCsvLauncher.launch("ledger_transactions_${System.currentTimeMillis()}.csv")
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)

                    BackupRestoreItem(
                        icon = Icons.Default.Update,
                        title = stringResource(R.string.backup_restore_last_backup_title),
                        subtitle = lastBackupAt?.let {
                            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(java.util.Date(it))
                        } ?: stringResource(R.string.backup_restore_never),
                        showDisclosure = false,
                        onClick = {}
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: "Cloud backup" (Pro feature)
            SettingsSectionHeader(stringResource(R.string.backup_restore_section_cloud))
            Card(
                shape = AppShapes.xl,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    BackupRestoreItem(
                        icon = Icons.Default.CloudUpload,
                        title = stringResource(R.string.backup_restore_gdrive_backup_title),
                        subtitle = stringResource(R.string.backup_restore_gdrive_backup_sub),
                        badge = stringResource(R.string.backup_restore_badge_pro),
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(requiresProMsg)
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: "Restore"
            SettingsSectionHeader(stringResource(R.string.backup_restore_section_restore))
            Card(
                shape = AppShapes.xl,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    BackupRestoreItem(
                        icon = Icons.Default.SettingsBackupRestore,
                        title = stringResource(R.string.backup_restore_restore_backup_title),
                        subtitle = stringResource(R.string.backup_restore_restore_backup_sub),
                        enabled = !isLoading,
                        onClick = {
                            restoreLauncher.launch(arrayOf("*/*"))
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)

                    BackupRestoreItem(
                        icon = Icons.Default.FileUpload,
                        title = stringResource(R.string.backup_restore_import_csv_title),
                        subtitle = stringResource(R.string.backup_restore_import_csv_sub),
                        enabled = !isLoading,
                        onClick = {
                            importCsvLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // SECTION: "Danger zone"
            SettingsSectionHeader(stringResource(R.string.backup_restore_section_danger), color = MaterialTheme.appColors.expense)
            Card(
                shape = AppShapes.xl,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            ) {
                Column {
                    BackupRestoreItem(
                        icon = Icons.Default.DeleteForever,
                        iconColor = MaterialTheme.appColors.expense,
                        title = stringResource(R.string.backup_restore_clear_all_title),
                        titleColor = MaterialTheme.appColors.expense,
                        subtitle = stringResource(R.string.backup_restore_clear_all_sub),
                        onClick = { showClearDataDialog = true }
                    )
                }
            }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.backup_restore_restore_confirm_title), style = AppTypography.display.copy(fontSize = 20.sp)) },
            text = {
                Text(
                    text = stringResource(R.string.backup_restore_restore_confirm_msg),
                    style = AppTypography.bodyRegular,
                    color = MaterialTheme.appColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreBackup(uri)
                        pendingRestoreUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary)
                ) {
                    Text(stringResource(R.string.backup_restore_restore_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.appColors.textPrimary)
                }
            },
            containerColor = MaterialTheme.appColors.bgSurface
        )
    }

    pendingImportCsvUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportCsvUri = null },
            title = { Text(stringResource(R.string.backup_restore_import_csv_confirm_title), style = AppTypography.display.copy(fontSize = 20.sp)) },
            text = {
                Text(
                    text = stringResource(R.string.backup_restore_import_csv_confirm_msg),
                    style = AppTypography.bodyRegular,
                    color = MaterialTheme.appColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importCsv(uri)
                        pendingImportCsvUri = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.primary)
                ) {
                    Text(stringResource(R.string.backup_restore_import_csv_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportCsvUri = null }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.appColors.textPrimary)
                }
            },
            containerColor = MaterialTheme.appColors.bgSurface
        )
    }

    if (showClearDataDialog) {
        var deleteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text(stringResource(R.string.backup_restore_delete_confirm_title), style = AppTypography.display.copy(fontSize = 20.sp)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.backup_restore_delete_confirm_msg),
                        style = AppTypography.bodyRegular,
                        color = MaterialTheme.appColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.backup_restore_delete_instruction),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deleteText,
                        onValueChange = { deleteText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // TODO: Implement deletion
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(dataDeletedMsg)
                        }
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.expense),
                    enabled = deleteText == "DELETE"
                ) {
                    Text(stringResource(R.string.backup_restore_delete_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = MaterialTheme.appColors.textPrimary)
                }
            },
            containerColor = MaterialTheme.appColors.bgSurface
        )
    }
}

@Composable
fun SettingsSectionHeader(title: String, color: Color = MaterialTheme.appColors.textSecondary) {
    Text(
        text = title.uppercase(),
        style = AppTypography.caption,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
    )
}

@Composable
fun BackupRestoreItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit,
    showDisclosure: Boolean = true,
    enabled: Boolean = true,
    iconColor: Color = MaterialTheme.appColors.primary,
    titleColor: Color = MaterialTheme.appColors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = titleColor,
                    style = AppTypography.bodyRegular
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.appColors.primary,
                        shape = AppShapes.full
                    ) {
                        Text(
                            text = badge,
                            color = Color.White,
                            style = AppTypography.caption,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                color = MaterialTheme.appColors.textSecondary,
                style = AppTypography.bodySmall
            )
        }

        if (showDisclosure) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.appColors.textTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
