package com.example.ui.screens

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.example.ai.overlay.AssistantRoleHelper
import com.example.ai.overlay.OverlayPermissionHelper
import com.example.ai.overlay.QuickAddOverlayService
import com.example.ui.components.settings.OptionPickerSheet
import com.example.ui.theme.appColors
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import com.example.ui.viewmodel.SettingsViewModel
import com.example.R

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
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    // Pre-compute localized picker option strings (must be outside lambdas)
    val themeSystem = stringResource(R.string.general_settings_theme_option_system)
    val themeLight = stringResource(R.string.general_settings_theme_option_light)
    val themeDark = stringResource(R.string.general_settings_theme_option_dark)
    val firstDaySunday = stringResource(R.string.general_settings_first_day_option_sunday)
    val firstDayMonday = stringResource(R.string.general_settings_first_day_option_monday)
    val numberFormatIndian = stringResource(R.string.general_settings_number_format_option_indian)
    val numberFormatIntl = stringResource(R.string.general_settings_number_format_option_intl)
    val langEnglish = stringResource(R.string.lang_english)
    val langHindi = stringResource(R.string.lang_hindi)
    val langGujarati = stringResource(R.string.lang_gujarati)
    val cashLabel = stringResource(R.string.account_cash)

    val currentAccountName = remember(accounts, defaultAccountId, cashLabel) {
        accounts.find { it.id == defaultAccountId }?.name ?: cashLabel
    }

    var pickerType by remember { mutableStateOf<PickerType?>(null) }
    var showClearCacheAlert by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var hasOverlayPermission by remember { mutableStateOf(OverlayPermissionHelper.canDrawOverlays(context)) }
    var isDefaultAssistant by remember { mutableStateOf(AssistantRoleHelper.isDefaultAssistant(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = OverlayPermissionHelper.canDrawOverlays(context)
                isDefaultAssistant = AssistantRoleHelper.isDefaultAssistant(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.general_settings_header), style = AppTypography.headline3) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.general_settings_back_desc))
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
            SettingsSection(stringResource(R.string.general_settings_section_appearance)) {
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_theme_label),
                    value = when (theme) {
                        "System" -> themeSystem
                        "Light" -> themeLight
                        else -> themeDark
                    },
                    onClick = { pickerType = PickerType.THEME }
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.settings_language),
                    value = when (appLanguage) {
                        "hi" -> langHindi
                        "gu" -> langGujarati
                        else -> langEnglish
                    },
                    onClick = { pickerType = PickerType.LANGUAGE }
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_app_icon_label),
                    value = stringResource(R.string.general_settings_app_icon_default),
                    onClick = { /* Pick icon logic */ }
                )
            }

            SettingsSection(stringResource(R.string.general_settings_section_date_time)) {
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_first_day_of_week_label),
                    value = when (firstDayOfWeek) {
                        "Sunday" -> firstDaySunday
                        else -> firstDayMonday
                    },
                    onClick = { pickerType = PickerType.FIRST_DAY_OF_WEEK }
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_date_format_label),
                    value = dateFormat,
                    onClick = { pickerType = PickerType.DATE_FORMAT }
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_time_format_label),
                    value = timeFormat,
                    onClick = { pickerType = PickerType.TIME_FORMAT }
                )
            }

            SettingsSection(stringResource(R.string.general_settings_section_numbers_display)) {
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_currency_label),
                    value = currencyCode,
                    onClick = onNavigateToCurrency
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_number_format_label),
                    value = when (numberFormat) {
                        "Indian" -> numberFormatIndian
                        else -> numberFormatIntl
                    },
                    onClick = { pickerType = PickerType.NUMBER_FORMAT }
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_decimal_places_label),
                    value = decimalPlaces.toString(),
                    onClick = { pickerType = PickerType.DECIMAL_PLACES }
                )
                GeneralSettingsToggleRow(
                    label = stringResource(R.string.general_settings_show_balance_label),
                    checked = showBalance,
                    onCheckedChange = { viewModel.setShowBalance(it) }
                )
            }

            SettingsSection(stringResource(R.string.general_settings_section_data)) {
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_default_account_label),
                    value = currentAccountName,
                    onClick = { pickerType = PickerType.DEFAULT_ACCOUNT }
                )
                GeneralSettingsToggleRow(
                    label = stringResource(R.string.general_settings_auto_categorise_label),
                    checked = autoCategorise,
                    onCheckedChange = { viewModel.setAutoCategorise(it) }
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_clear_cache_label),
                    labelColor = MaterialTheme.appColors.error,
                    showChevron = false,
                    onClick = { showClearCacheAlert = true }
                )
            }

            SettingsSection(stringResource(R.string.quick_add_settings_section)) {
                GeneralSettingsRow(
                    label = stringResource(R.string.quick_add_settings_permission_label),
                    value = if (hasOverlayPermission) {
                        stringResource(R.string.quick_add_settings_permission_granted)
                    } else {
                        stringResource(R.string.quick_add_settings_permission_not_granted)
                    },
                    onClick = { context.startActivity(OverlayPermissionHelper.buildPermissionIntent(context)) }
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.quick_add_settings_try_label),
                    showChevron = false,
                    onClick = {
                        if (hasOverlayPermission) {
                            QuickAddOverlayService.start(context)
                        } else {
                            context.startActivity(OverlayPermissionHelper.buildPermissionIntent(context))
                        }
                    }
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.quick_add_assistant_role_label),
                    value = if (isDefaultAssistant) {
                        stringResource(R.string.quick_add_settings_permission_granted)
                    } else {
                        stringResource(R.string.quick_add_settings_permission_not_granted)
                    },
                    onClick = {
                        AssistantRoleHelper.buildRequestRoleIntent(context)?.let { context.startActivity(it) }
                    }
                )
            }

            SettingsSection(stringResource(R.string.general_settings_section_about)) {
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_app_version_label),
                    value = stringResource(R.string.general_settings_app_version_val),
                    showChevron = false,
                    onClick = {}
                )
                GeneralSettingsRow(
                    label = stringResource(R.string.general_settings_changelog_label),
                    onClick = { /* Nav to changelog */ }
                )
            }
        }

        // Pickers
        when (pickerType) {
            PickerType.THEME -> OptionPickerSheet(
                title = stringResource(R.string.general_settings_theme_label),
                options = listOf(themeSystem, themeLight, themeDark),
                selectedOption = when (theme) {
                    "System" -> themeSystem
                    "Light" -> themeLight
                    else -> themeDark
                },
                onOptionSelected = { selected ->
                    viewModel.setTheme(when (selected) {
                        themeSystem -> "System"
                        themeLight -> "Light"
                        else -> "Dark"
                    })
                },
                onDismiss = { pickerType = null }
            )
            PickerType.FIRST_DAY_OF_WEEK -> OptionPickerSheet(
                title = stringResource(R.string.general_settings_first_day_of_week_label),
                options = listOf(firstDaySunday, firstDayMonday),
                selectedOption = when (firstDayOfWeek) {
                    "Sunday" -> firstDaySunday
                    else -> firstDayMonday
                },
                onOptionSelected = { selected ->
                    viewModel.setFirstDayOfWeek(if (selected == firstDaySunday) "Sunday" else "Monday")
                },
                onDismiss = { pickerType = null }
            )
            PickerType.DATE_FORMAT -> OptionPickerSheet(
                title = stringResource(R.string.general_settings_date_format_label),
                options = listOf("DD/MM/YYYY", "MM/DD/YYYY", "YYYY-MM-DD"),
                selectedOption = dateFormat,
                onOptionSelected = { viewModel.setDateFormat(it) },
                onDismiss = { pickerType = null }
            )
            PickerType.TIME_FORMAT -> OptionPickerSheet(
                title = stringResource(R.string.general_settings_time_format_label),
                options = listOf("12h", "24h"),
                selectedOption = timeFormat,
                onOptionSelected = { viewModel.setTimeFormat(it) },
                onDismiss = { pickerType = null }
            )
            PickerType.NUMBER_FORMAT -> OptionPickerSheet(
                title = stringResource(R.string.general_settings_number_format_label),
                options = listOf(numberFormatIndian, numberFormatIntl),
                selectedOption = when (numberFormat) {
                    "Indian" -> numberFormatIndian
                    else -> numberFormatIntl
                },
                onOptionSelected = { selected ->
                    viewModel.setNumberFormat(if (selected == numberFormatIndian) "Indian" else "International")
                },
                onDismiss = { pickerType = null }
            )
            PickerType.DECIMAL_PLACES -> OptionPickerSheet(
                title = stringResource(R.string.general_settings_decimal_places_label),
                options = listOf("0", "1", "2"),
                selectedOption = decimalPlaces.toString(),
                onOptionSelected = { viewModel.setDecimalPlaces(it.toInt()) },
                onDismiss = { pickerType = null }
            )
            PickerType.DEFAULT_ACCOUNT -> OptionPickerSheet(
                title = stringResource(R.string.general_settings_default_account_label),
                options = accounts.map { it.name },
                selectedOption = currentAccountName,
                onOptionSelected = { name ->
                    accounts.find { it.name == name }?.let { viewModel.setDefaultAccountId(it.id) }
                },
                onDismiss = { pickerType = null }
            )
            PickerType.LANGUAGE -> OptionPickerSheet(
                title = stringResource(R.string.settings_language),
                options = listOf(langEnglish, langHindi, langGujarati),
                selectedOption = when (appLanguage) {
                    "hi" -> langHindi
                    "gu" -> langGujarati
                    else -> langEnglish
                },
                onOptionSelected = { selected ->
                    val code = when (selected) {
                        langHindi -> "hi"
                        langGujarati -> "gu"
                        else -> "en"
                    }
                    viewModel.setAppLanguage(code)
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                    pickerType = null
                },
                onDismiss = { pickerType = null }
            )
            else -> {}
        }

        if (showClearCacheAlert) {
            AlertDialog(
                onDismissRequest = { showClearCacheAlert = false },
                title = { Text(stringResource(R.string.general_settings_clear_cache_title)) },
                text = { Text(stringResource(R.string.general_settings_clear_cache_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearCache()
                        showClearCacheAlert = false
                    }) {
                        Text(stringResource(R.string.action_confirm), color = MaterialTheme.appColors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCacheAlert = false }) {
                        Text(stringResource(R.string.action_cancel))
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
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.appColors.primary
            )
        )
    }
}

enum class PickerType {
    THEME, FIRST_DAY_OF_WEEK, DATE_FORMAT, TIME_FORMAT, NUMBER_FORMAT, DECIMAL_PLACES, DEFAULT_ACCOUNT, LANGUAGE
}
