package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.ui.theme.appColors
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onNavigateToProfile: () -> Unit,
    onNavigateToGeneral: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToCurrency: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToAccounts: () -> Unit
) {
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val session by authViewModel.activeSession.collectAsStateWithLifecycle()
    val initials = remember(session?.displayName) {
        session?.displayName.orEmpty().trim().split(" ").take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("").ifEmpty { "?" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // 1. Header
        Text(
            text = stringResource(R.string.settings_title),
            style = AppTypography.display.copy(fontSize = 34.sp),
            color = MaterialTheme.appColors.textPrimary,
            modifier = Modifier.padding(16.dp)
        )

        // 2. Profile Card
        ProfileCard(
            displayName = session?.displayName.orEmpty(),
            initials = initials,
            onClick = onNavigateToProfile
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Section 1 — App settings group
        Card(
            shape = AppShapes.xl,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Column {
                SettingsRow(
                    icon = Icons.Default.Settings,
                    label = stringResource(R.string.settings_general_label),
                    onPress = onNavigateToGeneral
                )
                SettingsRow(
                    icon = Icons.Default.AccountBalance,
                    label = stringResource(R.string.settings_accounts_label),
                    onPress = onNavigateToAccounts
                )
                SettingsRow(
                    icon = Icons.Default.Business,
                    label = stringResource(R.string.settings_business_details_label),
                    rightBadge = stringResource(R.string.settings_pro_badge),
                    onPress = { /* Navigate to BusinessDetailsScreen */ }
                )
                SettingsRow(
                    icon = Icons.Default.Lock,
                    label = stringResource(R.string.settings_security_label),
                    onPress = onNavigateToSecurity
                )
                SettingsRow(
                    icon = Icons.Default.CurrencyExchange,
                    label = stringResource(R.string.settings_change_currency_label),
                    rightLabel = currencyCode,
                    onPress = onNavigateToCurrency
                )
                SettingsRow(
                    icon = Icons.Default.CloudUpload,
                    label = stringResource(R.string.settings_backup_restore_label),
                    onPress = onNavigateToBackup
                )
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    label = stringResource(R.string.settings_reminders_label),
                    rightLabel = stringResource(R.string.settings_lite_badge),
                    onPress = onNavigateToReminders
                )
                SettingsRow(
                    icon = Icons.Default.Star,
                    label = stringResource(R.string.settings_need_new_feature_label),
                    showDivider = false,
                    onPress = { /* Navigate to FeatureRequestScreen */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Section 2 — Support & info group
        Card(
            shape = AppShapes.xl,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        ) {
            Column {
                SettingsRow(
                    icon = Icons.Default.Help,
                    label = stringResource(R.string.settings_faqs_label),
                    onPress = { /* Navigate to FAQScreen */ }
                )
                SettingsRow(
                    icon = Icons.Default.Apps,
                    label = stringResource(R.string.settings_my_other_apps_label),
                    onPress = { /* Navigate to OtherAppsScreen */ }
                )
                SettingsRow(
                    icon = Icons.Default.StarHalf,
                    label = stringResource(R.string.settings_rate_app_label),
                    onPress = { /* Trigger native App Store rating prompt */ }
                )
                SettingsRow(
                    icon = Icons.Default.Share,
                    label = stringResource(R.string.settings_share_app_label),
                    showDivider = false,
                    onPress = { /* Trigger native share sheet */ }
                )
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    onPress: () -> Unit,
    iconBg: Color? = null,
    rightLabel: String? = null,
    rightBadge: String? = null,
    badgeColor: String = "blue",
    showDivider: Boolean = true
) {
    val appliedIconBg = iconBg ?: MaterialTheme.appColors.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPress)
    ) {
        Row(
            modifier = Modifier
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon in colored square
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(appliedIconBg.copy(alpha = 0.1f)), // Subtle background
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = appliedIconBg,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = label,
                color = MaterialTheme.appColors.textPrimary,
                style = AppTypography.bodyRegular,
                modifier = Modifier.weight(1f)
            )

            if (rightBadge != null) {
                Surface(
                    color = if (badgeColor == "blue") MaterialTheme.appColors.primary else MaterialTheme.appColors.textTertiary,
                    shape = AppShapes.full,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = rightBadge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = AppTypography.caption,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (rightLabel != null) {
                Text(
                    text = rightLabel,
                    color = MaterialTheme.appColors.primary,
                    style = AppTypography.bodySmall,
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

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp), // Inset from left: starts after icon
                thickness = 0.5.dp,
                color = MaterialTheme.appColors.borderLight
            )
        }
    }
}

@Composable
private fun ProfileCard(
    displayName: String,
    initials: String,
    onClick: () -> Unit
) {
    Card(
        shape = AppShapes.xl,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = AppTypography.headline2,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName.ifEmpty { stringResource(R.string.settings_profile_name_placeholder) },
                    style = AppTypography.headline2,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.settings_view_profile),
                    style = AppTypography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}
