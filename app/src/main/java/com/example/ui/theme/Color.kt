package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

data class AppColors(
    val bgBase: Color,
    val bgSurface: Color,
    val bgSurface2: Color,
    val bgSurface3: Color,

    val primary: Color,
    val primaryLight: Color,

    val income: Color,
    val incomeLight: Color,
    val expense: Color,
    val expenseLight: Color,

    val warning: Color,
    val warningLight: Color,
    val success: Color,
    val error: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,

    val borderLight: Color,
    val borderMedium: Color,

    val tabBackground: Color,
    val tabBorder: Color,
    val tabActive: Color,
    val tabActiveBg: Color,
    val tabInactive: Color,
    val surfaceInverse: Color,
    val surfaceSubtle: Color,
    val swipeDeleteBg: Color,
    val swipeArchiveBg: Color,

    // Onboarding palette
    val onboardingBlue: Color,
    val onboardingBlueDark: Color,
    val onboardingPurple: Color,
    val onboardingAccentBlueText: Color,
    val onboardingAccentPurpleText: Color,
    val onboardingAccentBlueChip: Color,
    val onboardingAccentPurpleChip: Color,
    val onboardingSuccess: Color,
    val onboardingSuccessChip: Color,
    val onboardingDanger: Color,
    val onboardingRing: Color,
    val onboardingCard: Color,
    val onboardingCardBorder: Color,
    val onboardingTrack: Color,
    val onboardingGhostBtn: Color,
    val onboardingGhostBtnBorder: Color,
    val onboardingStatTile: Color
)

val LightTokens = AppColors(
    bgBase = Color(0xFFF5F6FA),
    bgSurface = Color(0xFFFFFFFF),
    bgSurface2 = Color(0xFFEEF0F6),
    bgSurface3 = Color(0xFFE4E6F0),
    primary = Color(0xFF1A56E8),
    primaryLight = Color(0xFFEBF0FD),
    income = Color(0xFF12A67A),
    incomeLight = Color(0xFFE6F7F2),
    expense = Color(0xFFE03F3F),
    expenseLight = Color(0xFFFDEAEA),
    warning = Color(0xFFD97706),
    warningLight = Color(0xFFFEF3C7),
    success = Color(0xFF12A67A),
    error = Color(0xFFE03F3F),
    textPrimary = Color(0xFF0D0F1A),
    textSecondary = Color(0xFF5A5F7A),
    textTertiary = Color(0xFF9095AE),
    textDisabled = Color(0xFFBCC0D0),
    borderLight = Color(0x0F000000), // ~0.06 alpha
    borderMedium = Color(0x1F000000), // ~0.12 alpha
    tabBackground = Color(0xFFFFFFFF),
    tabBorder = Color(0x12000000), // ~0.07 alpha
    tabActive = Color(0xFF1A56E8),
    tabActiveBg = Color(0xFFEBF0FD),
    tabInactive = Color(0xFF9095AE),
    surfaceInverse = Color(0xFF1F2235),
    surfaceSubtle = Color(0xFFEEF1F6),
    swipeDeleteBg = Color(0xFFE03F3F).copy(alpha = 0.9f),
    swipeArchiveBg = Color(0xFF12A67A).copy(alpha = 0.7f),

    onboardingBlue = Color(0xFF4361EE),
    onboardingBlueDark = Color(0xFF3D5BE0),
    onboardingPurple = Color(0xFF8B5CF6),
    onboardingAccentBlueText = Color(0xFF4A5FD9),
    onboardingAccentPurpleText = Color(0xFF8248D6),
    onboardingAccentBlueChip = Color(0xFF4361EE).copy(alpha = 0.10f),
    onboardingAccentPurpleChip = Color(0xFF8B5CF6).copy(alpha = 0.12f),
    onboardingSuccess = Color(0xFF1F9D63),
    onboardingSuccessChip = Color(0xFF1F9D63).copy(alpha = 0.10f),
    onboardingDanger = Color(0xFFE0473A),
    onboardingRing = Color(0xFF4361EE).copy(alpha = 0.28f),
    onboardingCard = Color(0xFFFFFFFF),
    onboardingCardBorder = Color(0xFF15172A).copy(alpha = 0.07f),
    onboardingTrack = Color(0xFF15172A).copy(alpha = 0.10f),
    onboardingGhostBtn = Color(0xFF15172A).copy(alpha = 0.045f),
    onboardingGhostBtnBorder = Color(0xFF15172A).copy(alpha = 0.10f),
    onboardingStatTile = Color(0xFF15172A).copy(alpha = 0.035f)
)

val DarkTokens = AppColors(
    bgBase = Color(0xFF0D0F1A),
    bgSurface = Color(0xFF161928),
    bgSurface2 = Color(0xFF1E2236),
    bgSurface3 = Color(0xFF252A45),
    primary = Color(0xFF4F7EF7),
    primaryLight = Color(0xFF1A2245),
    income = Color(0xFF27C98E),
    incomeLight = Color(0xFF0D2B22),
    expense = Color(0xFFF06464),
    expenseLight = Color(0xFF2B0F0F),
    warning = Color(0xFFF59E0B),
    warningLight = Color(0xFF2C1E05),
    success = Color(0xFF27C98E),
    error = Color(0xFFF06464),
    textPrimary = Color(0xFFE8EAF4),
    textSecondary = Color(0xFF7C82A0),
    textTertiary = Color(0xFF4A4F6A),
    textDisabled = Color(0xFF2E3350),
    borderLight = Color(0x0FFFFFFF), // ~0.06 alpha
    borderMedium = Color(0x1FFFFFFF), // ~0.12 alpha
    tabBackground = Color(0xFF161928),
    tabBorder = Color(0x12FFFFFF), // ~0.07 alpha
    tabActive = Color(0xFF4F7EF7),
    tabActiveBg = Color(0xFF1A2245),
    tabInactive = Color(0xFF4A4F6A),
    surfaceInverse = Color(0xFF2A2E45),
    surfaceSubtle = Color(0xFF1E2233),
    swipeDeleteBg = Color(0xFFF06464).copy(alpha = 0.9f),
    swipeArchiveBg = Color(0xFF27C98E).copy(alpha = 0.7f),

    onboardingBlue = Color(0xFF6B8BFF),
    onboardingBlueDark = Color(0xFF3D5BE0),
    onboardingPurple = Color(0xFF8B5CF6),
    onboardingAccentBlueText = Color(0xFFA9B8FF),
    onboardingAccentPurpleText = Color(0xFFC3A8FF),
    onboardingAccentBlueChip = Color(0xFF5B7CFF).copy(alpha = 0.14f),
    onboardingAccentPurpleChip = Color(0xFF8B5CF6).copy(alpha = 0.15f),
    onboardingSuccess = Color(0xFF5EE6A8),
    onboardingSuccessChip = Color(0xFF5EE6A8).copy(alpha = 0.14f),
    onboardingDanger = Color(0xFFFF8A80),
    onboardingRing = Color(0xFF5B7CFF).copy(alpha = 0.35f),
    onboardingCard = Color(0xFFFFFFFF).copy(alpha = 0.045f),
    onboardingCardBorder = Color(0xFFFFFFFF).copy(alpha = 0.07f),
    onboardingTrack = Color(0xFFE8EAF3).copy(alpha = 0.15f),
    onboardingGhostBtn = Color(0xFFFFFFFF).copy(alpha = 0.06f),
    onboardingGhostBtnBorder = Color(0xFFFFFFFF).copy(alpha = 0.10f),
    onboardingStatTile = Color(0xFFFFFFFF).copy(alpha = 0.04f)
)

val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("No AppColors provided")
}

val accountPalette = listOf(
    "#1A56E8", "#12A67A", "#E03F3F", "#F59E0B", "#8B5CF6", "#EC4899",
    "#06B6D4", "#10B981", "#EF4444", "#F97316", "#6366F1", "#64748B"
)
