package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.settings.OptionPickerSheet
import com.example.ui.theme.appColors
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import com.example.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToCurrency: () -> Unit
) {
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val firstDayOfWeek by viewModel.firstDayOfWeek.collectAsStateWithLifecycle()
    val dateFormat by viewModel.dateFormat.collectAsStateWithLifecycle()
    val timeFormat by viewModel.timeFormat.collectAsStateWithLifecycle()
    val numberFormat by viewModel.numberFormat.collectAsStateWithLifecycle()
    val decimalPlaces by viewModel.decimalPlaces.collectAsStateWithLifecycle()
    val showBalance by viewModel.showBalance.collectAsStateWithLifecycle()
    val autoCategorise by viewModel.autoCategorise.collectAsStateWithLifecycle()
    val accounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val defaultAccountId by viewModel.defaultAccountId.collectAsStateWithLifecycle()
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()

    val currentAccountName = remember(accounts, defaultAccountId) {
        accounts.find { it.id == defaultAccountId }?.name ?: "Cash"
    }

    var pickerType by remember { mutableStateOf<PickerType?>(null) }
    var showClearCacheAlert by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("General", style = AppTypography.headline3) },
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
            SettingsSection("Appearance") {
                GeneralSettingsRow(
                    label = "Theme",
                    value = theme,
                    onClick = { pickerType = PickerType.THEME }
                )
                GeneralSettingsRow(
                    label = "App Icon",
                    value = "Default",
                    onClick = { /* Pick icon logic */ }
                )
            }

            SettingsSection("Date & Time") {
                GeneralSettingsRow(
                    label = "First day of week",
                    value = firstDayOfWeek,
                    onClick = { pickerType = PickerType.FIRST_DAY_OF_WEEK }
                )
                GeneralSettingsRow(
                    label = "Date format",
                    value = dateFormat,
                    onClick = { pickerType = PickerType.DATE_FORMAT }
                )
                GeneralSettingsRow(
                    label = "Time format",
                    value = timeFormat,
                    onClick = { pickerType = PickerType.TIME_FORMAT }
                )
            }

            SettingsSection("Numbers & Display") {
                GeneralSettingsRow(
                    label = "Currency",
                    value = currencyCode,
                    onClick = onNavigateToCurrency
                )
                GeneralSettingsRow(
                    label = "Number format",
                    value = numberFormat,
                    onClick = { pickerType = PickerType.NUMBER_FORMAT }
                )
                GeneralSettingsRow(
                    label = "Decimal places",
                    value = decimalPlaces.toString(),
                    onClick = { pickerType = PickerType.DECIMAL_PLACES }
                )
                GeneralSettingsToggleRow(
                    label = "Show balance on home screen",
                    checked = showBalance,
                    onCheckedChange = { viewModel.setShowBalance(it) }
                )
            }

            SettingsSection("Data") {
                GeneralSettingsRow(
                    label = "Default account",
                    value = currentAccountName,
                    onClick = { pickerType = PickerType.DEFAULT_ACCOUNT }
                )
                GeneralSettingsToggleRow(
                    label = "Auto-categorise transactions",
                    checked = autoCategorise,
                    onCheckedChange = { viewModel.setAutoCategorise(it) }
                )
                GeneralSettingsRow(
                    label = "Clear cache",
                    labelColor = Color.Red,
                    showChevron = false,
                    onClick = { showClearCacheAlert = true }
                )
            }

            SettingsSection("About") {
                GeneralSettingsRow(
                    label = "App version",
                    value = "v1.0.0 (build 42)",
                    showChevron = false,
                    onClick = {}
                )
                GeneralSettingsRow(
                    label = "Changelog / What's new",
                    onClick = { /* Nav to changelog */ }
                )
            }
        }

        // Pickers
        when (pickerType) {
            PickerType.THEME -> OptionPickerSheet(
                title = "Theme",
                options = listOf("System", "Light", "Dark"),
                selectedOption = theme,
                onOptionSelected = { viewModel.setTheme(it) },
                onDismiss = { pickerType = null }
            )
            PickerType.FIRST_DAY_OF_WEEK -> OptionPickerSheet(
                title = "First day of week",
                options = listOf("Sunday", "Monday"),
                selectedOption = firstDayOfWeek,
                onOptionSelected = { viewModel.setFirstDayOfWeek(it) },
                onDismiss = { pickerType = null }
            )
            PickerType.DATE_FORMAT -> OptionPickerSheet(
                title = "Date format",
                options = listOf("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD"),
                selectedOption = dateFormat,
                onOptionSelected = { viewModel.setDateFormat(it) },
                onDismiss = { pickerType = null }
            )
            PickerType.TIME_FORMAT -> OptionPickerSheet(
                title = "Time format",
                options = listOf("12h", "24h"),
                selectedOption = timeFormat,
                onOptionSelected = { viewModel.setTimeFormat(it) },
                onDismiss = { pickerType = null }
            )
            PickerType.NUMBER_FORMAT -> OptionPickerSheet(
                title = "Number format",
                options = listOf("Indian", "International"),
                selectedOption = numberFormat,
                onOptionSelected = { viewModel.setNumberFormat(it) },
                onDismiss = { pickerType = null }
            )
            PickerType.DECIMAL_PLACES -> OptionPickerSheet(
                title = "Decimal places",
                options = listOf("0", "1", "2"),
                selectedOption = decimalPlaces.toString(),
                onOptionSelected = { viewModel.setDecimalPlaces(it.toInt()) },
                onDismiss = { pickerType = null }
            )
            PickerType.DEFAULT_ACCOUNT -> OptionPickerSheet(
                title = "Default account",
                options = accounts.map { it.name },
                selectedOption = currentAccountName,
                onOptionSelected = { name ->
                    accounts.find { it.name == name }?.let { viewModel.setDefaultAccountId(it.id) }
                },
                onDismiss = { pickerType = null }
            )
            else -> {}
        }

        if (showClearCacheAlert) {
            AlertDialog(
                onDismissRequest = { showClearCacheAlert = false },
                title = { Text("Clear Cache") },
                text = { Text("Clear all cached data? This won't delete your transactions.") },
                confirmButton = {
                    TextButton(onClick = { 
                        viewModel.clearCache()
                        showClearCacheAlert = false 
                    }) {
                        Text("Confirm", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheAlert = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.appColors.bgSurface,
                titleContentColor = MaterialTheme.appColors.textPrimary,
                textContentColor = MaterialTheme.appColors.textPrimary
            )
        }
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
fun GeneralSettingsRow(
    label: String,
    value: String? = null,
    onClick: () -> Unit,
    labelColor: Color? = null,
    showChevron: Boolean = true
) {
    val appliedLabelColor = labelColor ?: MaterialTheme.appColors.textPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = AppTypography.bodyRegular, color = appliedLabelColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    text = value,
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.appColors.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.appColors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun GeneralSettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.appColors.primary
            )
        )
    }
}

enum class PickerType {
    THEME, FIRST_DAY_OF_WEEK, DATE_FORMAT, TIME_FORMAT, NUMBER_FORMAT, DECIMAL_PLACES, DEFAULT_ACCOUNT
}
