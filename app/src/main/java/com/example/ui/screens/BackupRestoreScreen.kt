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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore", style = AppTypography.display.copy(fontSize = 20.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            SettingsSectionHeader("Backup")
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
                        title = "Export all data",
                        subtitle = "Download a full backup as a JSON file",
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Generating JSON export...")
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)
                    
                    BackupRestoreItem(
                        icon = Icons.Default.Description,
                        title = "Export transactions as CSV",
                        subtitle = "Compatible with Excel and Google Sheets",
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Generating CSV export...")
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)
                    
                    BackupRestoreItem(
                        icon = Icons.Default.Update,
                        title = "Last backup date",
                        subtitle = "Never",
                        showDisclosure = false,
                        onClick = {}
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: "Cloud backup" (Pro feature)
            SettingsSectionHeader("Cloud backup")
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
                        title = "Google Drive backup",
                        subtitle = "Auto backup to cloud",
                        badge = "Pro",
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Requires Pro Version")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: "Restore"
            SettingsSectionHeader("Restore")
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
                        title = "Restore from backup",
                        subtitle = "Import a .json backup file",
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Selecting file...")
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)
                    
                    BackupRestoreItem(
                        icon = Icons.Default.FileUpload,
                        title = "Import from CSV",
                        subtitle = "Import basic CSV from other apps",
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Selecting CSV...")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // SECTION: "Danger zone"
            SettingsSectionHeader("Danger zone", color = MaterialTheme.appColors.expense)
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
                        title = "Clear all data",
                        titleColor = MaterialTheme.appColors.expense,
                        subtitle = "Permanently delete everything",
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
            title = { Text("Delete everything?", style = AppTypography.display.copy(fontSize = 20.sp)) },
            text = {
                Column {
                    Text(
                        text = "This will permanently delete all your transactions, accounts, and settings. This cannot be undone.",
                        style = AppTypography.bodyRegular,
                        color = MaterialTheme.appColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Type 'DELETE' to confirm:",
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
                            snackbarHostState.showSnackbar("Data deleted (Not fully implemented)")
                        }
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.appColors.expense),
                    enabled = deleteText == "DELETE"
                ) {
                    Text("Clear Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = MaterialTheme.appColors.textPrimary)
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
