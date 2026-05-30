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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.appColors
import androidx.compose.material3.MaterialTheme
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToGeneral: () -> Unit,
    onNavigateToSecurity: () -> Unit,
    onNavigateToCurrency: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToAccounts: () -> Unit
) {
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.appColors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // 1. Header
        Text(
            text = "Settings",
            style = AppTypography.display.copy(fontSize = 34.sp),
            color = MaterialTheme.appColors.textPrimary,
            modifier = Modifier.padding(16.dp)
        )

        // 2. Upgrade Banner
        UpgradeBanner(onClick = { /* Navigate to ProUpgradeScreen */ })

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
                    label = "General",
                    onPress = onNavigateToGeneral
                )
                SettingsRow(
                    icon = Icons.Default.AccountBalance,
                    label = "Accounts",
                    onPress = onNavigateToAccounts
                )
                SettingsRow(
                    icon = Icons.Default.Business,
                    label = "Business Details",
                    rightBadge = "Pro",
                    onPress = { /* Navigate to BusinessDetailsScreen */ }
                )
                SettingsRow(
                    icon = Icons.Default.Lock,
                    label = "Security",
                    onPress = onNavigateToSecurity
                )
                SettingsRow(
                    icon = Icons.Default.CurrencyExchange,
                    label = "Change Currency",
                    rightLabel = currencyCode,
                    onPress = onNavigateToCurrency
                )
                SettingsRow(
                    icon = Icons.Default.CloudUpload,
                    label = "Backup & Restore",
                    onPress = onNavigateToBackup
                )
                SettingsRow(
                    icon = Icons.Default.Notifications,
                    label = "Reminders",
                    rightLabel = "Lite",
                    onPress = onNavigateToReminders
                )
                SettingsRow(
                    icon = Icons.Default.Star,
                    label = "Need new feature?",
                    showDivider = false,
                    onPress = { /* Navigate to FeatureRequestScreen */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Section 2 — Support & info group
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
                    label = "FAQs",
                    onPress = { /* Navigate to FAQScreen */ }
                )
                SettingsRow(
                    icon = Icons.Default.Apps,
                    label = "My Other Apps",
                    onPress = { /* Navigate to OtherAppsScreen */ }
                )
                SettingsRow(
                    icon = Icons.Default.StarHalf,
                    label = "Love the App? Rate It",
                    onPress = { /* Trigger native App Store rating prompt */ }
                )
                SettingsRow(
                    icon = Icons.Default.Share,
                    label = "Share App",
                    showDivider = false,
                    onPress = { /* Trigger native share sheet */ }
                )
            }
        }
    }
}

@Composable
fun UpgradeBanner(onClick: () -> Unit) {
    Card(
        shape = AppShapes.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.primary),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Stars,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Upgrade to Pro",
                    color = Color.White,
                    style = AppTypography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Unlock all features and Remove Ads.",
                    color = Color.White.copy(alpha = 0.8f),
                    style = AppTypography.bodySmall
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
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
                        color = Color.White,
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
