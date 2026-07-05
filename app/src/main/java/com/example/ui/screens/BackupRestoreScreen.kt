package com.example.ui.screens

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showClearDataDialog by remember { mutableStateOf(false) }

    // Pre-compute snackbar strings for use inside lambdas
    val jsonExportMsg = stringResource(R.string.backup_restore_exporting_json_msg)
    val csvExportMsg = stringResource(R.string.backup_restore_exporting_csv_msg)
    val requiresProMsg = stringResource(R.string.backup_restore_requires_pro_msg)
    val selectingFileMsg = stringResource(R.string.backup_restore_selecting_file_msg)
    val selectingCsvMsg = stringResource(R.string.backup_restore_selecting_csv_msg)
    val dataDeletedMsg = stringResource(R.string.backup_restore_deleted_msg)

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
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(jsonExportMsg)
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)

                    BackupRestoreItem(
                        icon = Icons.Default.Description,
                        title = stringResource(R.string.backup_restore_export_csv_title),
                        subtitle = stringResource(R.string.backup_restore_export_csv_sub),
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(csvExportMsg)
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)

                    BackupRestoreItem(
                        icon = Icons.Default.Update,
                        title = stringResource(R.string.backup_restore_last_backup_title),
                        subtitle = stringResource(R.string.backup_restore_never),
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
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(selectingFileMsg)
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)

                    BackupRestoreItem(
                        icon = Icons.Default.FileUpload,
                        title = stringResource(R.string.backup_restore_import_csv_title),
                        subtitle = stringResource(R.string.backup_restore_import_csv_sub),
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(selectingCsvMsg)
                            }
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
    iconColor: Color = MaterialTheme.appColors.primary,
    titleColor: Color = MaterialTheme.appColors.textPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
