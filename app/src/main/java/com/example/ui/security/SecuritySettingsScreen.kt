package com.example.ui.security

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.security.BiometricAuthenticator
import com.example.data.security.BiometricStatus
import com.example.ui.theme.appColors
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    viewModel: SecurityViewModel,
    activity: FragmentActivity,
    onBack: () -> Unit,
    onNavigateToSetPin: () -> Unit,
    onNavigateToChangePin: () -> Unit
) {
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val lockMethod by viewModel.lockMethod.collectAsState()
    val biometricsEnabled by viewModel.biometricsEnabled.collectAsState()
    val lockAfterMs by viewModel.lockAfterMs.collectAsState()
    val requireUnlock by viewModel.requireUnlockOnLaunch.collectAsState()
    val hideBalance by viewModel.hideBalanceWhenLocked.collectAsState()
    val hidePreview by viewModel.hidePreviewInSwitcher.collectAsState()
    val screenshotProtection by viewModel.screenshotProtection.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()

    var showLockAfterDialog by remember { mutableStateOf(false) }
    var showLockMethodDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val auth = remember { BiometricAuthenticator(activity) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Security Settings", style = AppTypography.headline3) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.appColors.bgBase,
                    titleContentColor = MaterialTheme.appColors.textPrimary,
                    navigationIconContentColor = MaterialTheme.appColors.textPrimary
                )
            )
        },
        containerColor = MaterialTheme.appColors.bgBase
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // APP LOCK SECTION
            SettingsSection("App Lock") {
                ToggleRow(
                    label = "App lock",
                    checked = appLockEnabled,
                    onToggle = { enabled ->
                        if (enabled) {
                            if (auth.canAuthenticate() != BiometricStatus.AVAILABLE && lockMethod == "BIOMETRICS") {
                                Toast.makeText(context, "Set up biometrics in device settings first", Toast.LENGTH_SHORT).show()
                                return@ToggleRow
                            }
                            if (lockMethod == "BIOMETRICS") {
                                auth.authenticate(
                                    title = "Enable App Lock",
                                    onSuccess = { viewModel.toggleAppLock(true) },
                                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                )
                            } else {
                                if (!hasPin) {
                                    onNavigateToSetPin()
                                } else {
                                    viewModel.toggleAppLock(true)
                                }
                            }
                        } else {
                            if (lockMethod == "BIOMETRICS") {
                                auth.authenticate(
                                    title = "Disable App Lock",
                                    onSuccess = { viewModel.toggleAppLock(false) },
                                    onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                                )
                            } else {
                                viewModel.toggleAppLock(false)
                            }
                        }
                    }
                )
                
                HorizontalDivider(
                    color = MaterialTheme.appColors.borderLight,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 16.dp)
                )
                
                ClickRow(
                    label = "Lock method",
                    value = lockMethod,
                    enabled = appLockEnabled,
                    onClick = { showLockMethodDialog = true }
                )
                
                if (appLockEnabled) {
                    HorizontalDivider(
                        color = MaterialTheme.appColors.borderLight,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                    if (lockMethod == "BIOMETRICS") {
                        ToggleRow(
                            label = "Biometrics",
                            checked = biometricsEnabled,
                            onToggle = { viewModel.toggleBiometrics(it) }
                        )
                    } else {
                        if (hasPin) {
                            ClickRow(
                                label = "Change PIN",
                                value = "",
                                onClick = onNavigateToChangePin
                            )
                        } else {
                            ClickRow(
                                label = "Set PIN",
                                value = "",
                                onClick = onNavigateToSetPin
                            )
                        }
                    }
                }
            }

            // LOCK BEHAVIOUR SECTION
            SettingsSection("Lock Behaviour") {
                ClickRow(
                    label = "Lock after",
                    value = lockAfterMsToLabel(lockAfterMs),
                    enabled = appLockEnabled,
                    onClick = { showLockAfterDialog = true }
                )
                HorizontalDivider(
                    color = MaterialTheme.appColors.borderLight,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 16.dp)
                )
                ToggleRow(
                    label = "Require unlock on launch",
                    checked = requireUnlock,
                    enabled = appLockEnabled,
                    onToggle = { viewModel.toggleRequireUnlockOnLaunch(it) }
                )
                HorizontalDivider(
                    color = MaterialTheme.appColors.borderLight,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 16.dp)
                )
                ToggleRow(
                    label = "Hide balance when locked",
                    checked = hideBalance,
                    enabled = appLockEnabled,
                    onToggle = { viewModel.toggleHideBalanceWhenLocked(it) }
                )
            }

            // PRIVACY SECTION
            SettingsSection("Privacy") {
                ToggleRow(
                    label = "Hide preview in app switcher",
                    checked = hidePreview,
                    onToggle = { viewModel.toggleHidePreviewInSwitcher(it) }
                )
                HorizontalDivider(
                    color = MaterialTheme.appColors.borderLight,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 16.dp)
                )
                ToggleRow(
                    label = "Screenshot protection",
                    checked = screenshotProtection,
                    onToggle = { viewModel.toggleScreenshotProtection(it) }
                )
            }
        }
    }

    if (showLockAfterDialog) {
        LockAfterDialog(
            current = lockAfterMs,
            onSelect = { 
                viewModel.setLockAfter(it)
                showLockAfterDialog = false
            },
            onDismiss = { showLockAfterDialog = false }
        )
    }
    if (showLockMethodDialog) {
        LockMethodDialog(
            current = lockMethod,
            onSelect = { 
                viewModel.setLockMethod(it)
                showLockMethodDialog = false
            },
            onDismiss = { showLockMethodDialog = false }
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = title.uppercase(),
            style = AppTypography.caption,
            color = MaterialTheme.appColors.textTertiary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Card(
            shape = AppShapes.xl,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = AppTypography.bodyRegular,
            color = if (enabled) MaterialTheme.appColors.textPrimary 
                    else MaterialTheme.appColors.textDisabled,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.appColors.primary
            )
        )
    }
}

@Composable
fun ClickRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = AppTypography.bodyRegular,
            color = if (enabled) MaterialTheme.appColors.textPrimary
                    else MaterialTheme.appColors.textDisabled,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.appColors.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.appColors.textTertiary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun LockAfterDialog(current: Long, onSelect: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock after", style = AppTypography.headline3) },
        text = {
            Column {
                listOf(0L to "Immediately", 60_000L to "1 minute", 300_000L to "5 minutes", 900_000L to "15 minutes", -1L to "Never").forEach { (ms, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(ms) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = ms == current,
                            onClick = { onSelect(ms) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.appColors.primary,
                                unselectedColor = MaterialTheme.appColors.textTertiary
                            )
                        )
                        Text(
                            text = label,
                            style = AppTypography.bodyRegular,
                            color = MaterialTheme.appColors.textPrimary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppTypography.bodyRegular, color = MaterialTheme.appColors.primary)
            }
        },
        containerColor = MaterialTheme.appColors.bgSurface,
        titleContentColor = MaterialTheme.appColors.textPrimary,
        textContentColor = MaterialTheme.appColors.textPrimary
    )
}

@Composable
fun LockMethodDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock method", style = AppTypography.headline3) },
        text = {
            Column {
                listOf("BIOMETRICS", "PIN").forEach { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(method) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = method == current,
                            onClick = { onSelect(method) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.appColors.primary,
                                unselectedColor = MaterialTheme.appColors.textTertiary
                            )
                        )
                        Text(
                            text = method,
                            style = AppTypography.bodyRegular,
                            color = MaterialTheme.appColors.textPrimary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppTypography.bodyRegular, color = MaterialTheme.appColors.primary)
            }
        },
        containerColor = MaterialTheme.appColors.bgSurface,
        titleContentColor = MaterialTheme.appColors.textPrimary,
        textContentColor = MaterialTheme.appColors.textPrimary
    )
}

private fun lockAfterMsToLabel(ms: Long): String = when (ms) {
    0L -> "Immediately"
    60_000L -> "1 minute"
    300_000L -> "5 minutes"
    900_000L -> "15 minutes"
    -1L -> "Never"
    else -> "Immediately"
}
