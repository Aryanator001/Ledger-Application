@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.example.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SoraFontFamily = FontFamily(
    Font(
        R.font.sora,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.sora,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.sora,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        R.font.sora,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

val ManropeFontFamily = FontFamily(
    Font(
        R.font.manrope,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        R.font.manrope,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.manrope,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.manrope,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

object AppTypography {
    val display = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = (32 * 1.2).sp
    )
    val headline1 = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = (28 * 1.3).sp
    )
    val headline2 = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = (24 * 1.3).sp
    )
    val headline3 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = (20 * 1.4).sp
    )
    val bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = (16 * 1.5).sp
    )
    val bodyRegular = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = (14 * 1.5).sp
    )
    val bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = (12 * 1.4).sp
    )
    val caption = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = (11 * 1.3).sp
    )
    val monospace = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
    val amount = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
    val amountLarge = TextStyle(
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    )

    // Onboarding typography
    val onboardingTitle1 = TextStyle(
        fontFamily = SoraFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = (32 * 1.18).sp,
        letterSpacing = (-0.3).sp
    )
    val onboardingTitle2 = TextStyle(
        fontFamily = SoraFontFamily,
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = (26 * 1.2).sp,
        letterSpacing = (-0.3).sp
    )
    val onboardingTitle3 = TextStyle(
        fontFamily = SoraFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = (24 * 1.25).sp,
        letterSpacing = (-0.2).sp
    )
    val onboardingBody = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 15.5.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = (15.5 * 1.6).sp
    )
    val onboardingBodySm = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = (14.5 * 1.55).sp
    )
    val onboardingChipLabel = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp
    )
    val onboardingSkip = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold
    )
    val onboardingCta = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold
    )
    val onboardingCtaFinal = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 16.5.sp,
        fontWeight = FontWeight.Bold
    )
    val onboardingRowTitle = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
    val onboardingRowSub = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
    val onboardingSuccessChipLabel = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold
    )
    val onboardingStatLabel = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold
    )
    val onboardingStatValue = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.ExtraBold
    )
    val onboardingBubbleText = TextStyle(
        fontFamily = ManropeFontFamily,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.SemiBold
    )
}
