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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.R
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

    // Pre-compute localized strings for use inside lambdas
    val enableLockTitle = stringResource(R.string.security_settings_enable_app_lock_title)
    val disableLockTitle = stringResource(R.string.security_settings_disable_app_lock_title)
    val biometricsToast = stringResource(R.string.security_settings_biometrics_toast)
    val lockMethodBiometrics = stringResource(R.string.security_settings_lock_method_biometrics)
    val lockMethodPin = stringResource(R.string.security_settings_lock_method_pin)
    val lockAfterImmediately = stringResource(R.string.security_settings_lock_after_immediately)
    val lockAfter1Min = stringResource(R.string.security_settings_lock_after_1_min)
    val lockAfter5Min = stringResource(R.string.security_settings_lock_after_5_min)
    val lockAfter15Min = stringResource(R.string.security_settings_lock_after_15_min)
    val lockAfterNever = stringResource(R.string.security_settings_lock_after_never)
    val lockAfterLabel = when (lockAfterMs) {
        0L -> lockAfterImmediately
        60_000L -> lockAfter1Min
        300_000L -> lockAfter5Min
        900_000L -> lockAfter15Min
        -1L -> lockAfterNever
        else -> lockAfterImmediately
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.security_settings_header), style = AppTypography.headline3) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.security_settings_back_desc))
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
            SettingsSection(stringResource(R.string.security_settings_section_app_lock)) {
                ToggleRow(
                    label = stringResource(R.string.security_settings_app_lock_label),
                    checked = appLockEnabled,
                    onToggle = { enabled ->
                        if (enabled) {
                            if (auth.canAuthenticate() != BiometricStatus.AVAILABLE && lockMethod == "BIOMETRICS") {
                                Toast.makeText(context, biometricsToast, Toast.LENGTH_SHORT).show()
                                return@ToggleRow
                            }
                            if (lockMethod == "BIOMETRICS") {
                                auth.authenticate(
                                    title = enableLockTitle,
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
                                    title = disableLockTitle,
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
                    label = stringResource(R.string.security_settings_lock_method_label),
                    value = when (lockMethod) {
                        "BIOMETRICS" -> lockMethodBiometrics
                        else -> lockMethodPin
                    },
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
                            label = stringResource(R.string.security_settings_biometrics_label),
                            checked = biometricsEnabled,
                            onToggle = { viewModel.toggleBiometrics(it) }
                        )
                    } else {
                        if (hasPin) {
                            ClickRow(
                                label = stringResource(R.string.security_settings_change_pin_label),
                                value = "",
                                onClick = onNavigateToChangePin
                            )
                        } else {
                            ClickRow(
                                label = stringResource(R.string.security_settings_set_pin_label),
                                value = "",
                                onClick = onNavigateToSetPin
                            )
                        }
                    }
                }
            }

            // LOCK BEHAVIOUR SECTION
            SettingsSection(stringResource(R.string.security_settings_section_lock_behaviour)) {
                ClickRow(
                    label = stringResource(R.string.security_settings_lock_after_label),
                    value = lockAfterLabel,
                    enabled = appLockEnabled,
                    onClick = { showLockAfterDialog = true }
                )
                HorizontalDivider(
                    color = MaterialTheme.appColors.borderLight,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 16.dp)
                )
                ToggleRow(
                    label = stringResource(R.string.security_settings_require_unlock_label),
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
                    label = stringResource(R.string.security_settings_hide_balance_label),
                    checked = hideBalance,
                    enabled = appLockEnabled,
                    onToggle = { viewModel.toggleHideBalanceWhenLocked(it) }
                )
            }

            // PRIVACY SECTION
            SettingsSection(stringResource(R.string.security_settings_section_privacy)) {
                ToggleRow(
                    label = stringResource(R.string.security_settings_hide_preview_label),
                    checked = hidePreview,
                    onToggle = { viewModel.toggleHidePreviewInSwitcher(it) }
                )
                HorizontalDivider(
                    color = MaterialTheme.appColors.borderLight,
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(start = 16.dp)
                )
                ToggleRow(
                    label = stringResource(R.string.security_settings_screenshot_protection_label),
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
        title = { Text(stringResource(R.string.security_settings_lock_after_label), style = AppTypography.headline3) },
        text = {
            Column {
                listOf(
                    0L to stringResource(R.string.security_settings_lock_after_immediately),
                    60_000L to stringResource(R.string.security_settings_lock_after_1_min),
                    300_000L to stringResource(R.string.security_settings_lock_after_5_min),
                    900_000L to stringResource(R.string.security_settings_lock_after_15_min),
                    -1L to stringResource(R.string.security_settings_lock_after_never)
                ).forEach { (ms, label) ->
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
                Text(stringResource(R.string.action_cancel), style = AppTypography.bodyRegular, color = MaterialTheme.appColors.primary)
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
        title = { Text(stringResource(R.string.security_settings_lock_method_label), style = AppTypography.headline3) },
        text = {
            Column {
                listOf(
                    "BIOMETRICS" to stringResource(R.string.security_settings_lock_method_biometrics),
                    "PIN" to stringResource(R.string.security_settings_lock_method_pin)
                ).forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(code) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = code == current,
                            onClick = { onSelect(code) },
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
                Text(stringResource(R.string.action_cancel), style = AppTypography.bodyRegular, color = MaterialTheme.appColors.primary)
            }
        },
        containerColor = MaterialTheme.appColors.bgSurface,
        titleContentColor = MaterialTheme.appColors.textPrimary,
        textContentColor = MaterialTheme.appColors.textPrimary
    )
}
