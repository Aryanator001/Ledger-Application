package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
fun RemindersScreen(
    onBack: () -> Unit,
    onNavigateToRecurring: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val openingPickerMsg = stringResource(R.string.reminders_opening_picker_msg)
    val openingDayPickerMsg = stringResource(R.string.reminders_opening_day_picker_msg)
    val proPreviewMsg = stringResource(R.string.reminders_pro_preview_msg)
    val badgeLite = stringResource(R.string.reminders_badge_lite)
    val badgePro = stringResource(R.string.reminders_badge_pro)
    val defaultTime = stringResource(R.string.reminders_default_time)
    val defaultSummaryDay = stringResource(R.string.reminders_default_summary_day)
    val defaultThreshold = stringResource(R.string.reminders_default_threshold)

    var isDailyReminderOn by remember { mutableStateOf(false) }
    var dailyReminderTime by remember(defaultTime) { mutableStateOf(defaultTime) }

    var isBudgetAlertsOn by remember { mutableStateOf(false) }
    var isMonthlySummaryOn by remember { mutableStateOf(false) }
    var monthlySummaryDay by remember(defaultSummaryDay) { mutableStateOf(defaultSummaryDay) }

    var isLowBalanceWarningOn by remember { mutableStateOf(false) }
    var lowBalanceThreshold by remember(defaultThreshold) { mutableStateOf(defaultThreshold) }

    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title), style = AppTypography.display.copy(fontSize = 20.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reminders_back_desc))
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateSheet = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.reminders_add_desc))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.appColors.bgBase,
                    titleContentColor = MaterialTheme.appColors.textPrimary,
                    navigationIconContentColor = MaterialTheme.appColors.textPrimary,
                    actionIconContentColor = MaterialTheme.appColors.primary
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

            // SECTION: Daily reminder
            SettingsSectionHeader(stringResource(R.string.reminders_section_daily))
            Card(
                shape = AppShapes.xl,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            ) {
                Column {
                    ReminderToggleItem(
                        icon = Icons.Default.Event,
                        title = stringResource(R.string.reminders_daily_expense_title),
                        isChecked = isDailyReminderOn,
                        onCheckedChange = { isDailyReminderOn = it }
                    )
                    if (isDailyReminderOn) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)
                        ReminderClickItem(
                            title = stringResource(R.string.reminders_time_title),
                            rightLabel = dailyReminderTime,
                            onClick = {
                                coroutineScope.launch { snackbarHostState.showSnackbar(openingPickerMsg) }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: Budget alerts
            SettingsSectionHeader(stringResource(R.string.reminders_section_budget))
            Card(
                shape = AppShapes.xl,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            ) {
                Column {
                    ReminderToggleItem(
                        icon = Icons.Default.Warning,
                        title = stringResource(R.string.reminders_budget_threshold_title),
                        isChecked = isBudgetAlertsOn,
                        onCheckedChange = { isBudgetAlertsOn = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)
                    ReminderToggleItem(
                        icon = Icons.Default.PieChart,
                        title = stringResource(R.string.reminders_monthly_summary_title),
                        isChecked = isMonthlySummaryOn,
                        onCheckedChange = { isMonthlySummaryOn = it }
                    )
                    if (isMonthlySummaryOn) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)
                        ReminderClickItem(
                            title = stringResource(R.string.reminders_summary_day_title),
                            rightLabel = monthlySummaryDay,
                            onClick = {
                                coroutineScope.launch { snackbarHostState.showSnackbar(openingDayPickerMsg) }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SECTION: Smart reminders
            SettingsSectionHeader(stringResource(R.string.reminders_section_smart))
            Card(
                shape = AppShapes.xl,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            ) {
                Column {
                    ReminderRouteItem(
                        icon = Icons.Default.Autorenew,
                        title = stringResource(R.string.reminders_recurring_bill_title),
                        subtitle = stringResource(R.string.reminders_recurring_bill_sub),
                        badge = badgeLite,
                        onClick = onNavigateToRecurring
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)
                    ReminderToggleItem(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = stringResource(R.string.reminders_low_balance_title),
                        badge = badgePro,
                        isChecked = isLowBalanceWarningOn,
                        onCheckedChange = {
                            isLowBalanceWarningOn = it
                            if (it) {
                                coroutineScope.launch { snackbarHostState.showSnackbar(proPreviewMsg) }
                            }
                        }
                    )
                    if (isLowBalanceWarningOn) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), thickness = 0.5.dp, color = MaterialTheme.appColors.borderLight)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(stringResource(R.string.reminders_threshold_label), modifier = Modifier.weight(1f), style = AppTypography.bodyRegular, color = MaterialTheme.appColors.textSecondary)
                            OutlinedTextField(
                                value = lowBalanceThreshold,
                                onValueChange = { lowBalanceThreshold = it },
                                modifier = Modifier.width(100.dp),
                                textStyle = AppTypography.bodyRegular.copy(color = MaterialTheme.appColors.textPrimary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateReminderSheet(onDismiss = { showCreateSheet = false })
    }
}

@Composable
fun ReminderToggleItem(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.appColors.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.appColors.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.appColors.textPrimary,
                style = AppTypography.bodyRegular
            )
            if (badge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(color = MaterialTheme.appColors.primary, shape = AppShapes.full) {
                    Text(
                        text = badge,
                        color = Color.White,
                        style = AppTypography.caption,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Switch(
            checked = isChecked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.appColors.primary,
                uncheckedThumbColor = MaterialTheme.appColors.textTertiary,
                uncheckedTrackColor = MaterialTheme.appColors.bgSurface,
                uncheckedBorderColor = MaterialTheme.appColors.borderLight
            )
        )
    }
}

@Composable
fun ReminderClickItem(
    title: String,
    rightLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 60.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = MaterialTheme.appColors.textSecondary,
            style = AppTypography.bodyRegular,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = rightLabel,
            color = MaterialTheme.appColors.primary,
            style = AppTypography.bodyRegular,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ReminderRouteItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    onClick: () -> Unit
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
                .background(MaterialTheme.appColors.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.appColors.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = MaterialTheme.appColors.textPrimary,
                    style = AppTypography.bodyRegular
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = MaterialTheme.appColors.primary, shape = AppShapes.full) {
                        Text(
                            text = badge,
                            color = Color.White,
                            style = AppTypography.caption,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.appColors.textSecondary,
                    style = AppTypography.bodySmall
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.appColors.textTertiary,
            modifier = Modifier.size(16.dp)
        )
    }
}
