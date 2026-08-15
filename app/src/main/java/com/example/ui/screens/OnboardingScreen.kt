package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors
import com.example.ui.viewmodel.OnboardingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit
) {
    val colors = MaterialTheme.appColors
    val currentSlide by viewModel.currentSlide.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { OnboardingViewModel.SLIDE_COUNT })
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentSlide) {
        if (pagerState.currentPage != currentSlide) {
            pagerState.animateScrollToPage(currentSlide)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != currentSlide) {
            viewModel.goToSlide(pagerState.currentPage)
        }
    }

    val showSkip = currentSlide < 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> Slide1(colors = colors)
                1 -> Slide2(colors = colors)
                2 -> Slide3(colors = colors)
                3 -> Slide4(colors = colors)
            }
        }

        // Progress segments + Skip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(OnboardingViewModel.SLIDE_COUNT) { i ->
                val fill by animateFloatAsState(
                    targetValue = if (i <= currentSlide) 1f else 0f,
                    animationSpec = tween(400),
                    label = "seg$i"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.onboardingTrack)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .background(
                                brush = Brush.horizontalGradient(
                                    listOf(colors.onboardingBlue, colors.onboardingPurple)
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
            if (showSkip) {
                Text(
                    text = stringResource(R.string.ob_btn_skip),
                    style = AppTypography.onboardingSkip,
                    color = colors.textTertiary,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clickable { scope.launch { viewModel.skipToLast() } }
                )
            }
        }

        // Bottom CTA
        val isLastSlide = currentSlide == OnboardingViewModel.SLIDE_COUNT - 1
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLastSlide) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(colors.onboardingBlue, colors.onboardingPurple)
                            )
                        )
                        .clickable {
                            viewModel.completeOnboarding()
                            onComplete()
                        }
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ob_btn_get_started),
                        style = AppTypography.onboardingCtaFinal,
                        color = Color.White
                    )
                }
                Text(
                    text = stringResource(R.string.ob_free_forever),
                    style = AppTypography.onboardingRowSub,
                    color = colors.textTertiary
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.onboardingGhostBtn)
                        .border(1.dp, colors.onboardingGhostBtnBorder, RoundedCornerShape(20.dp))
                        .clickable { scope.launch { viewModel.nextSlide() } }
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ob_btn_continue),
                            style = AppTypography.onboardingCta,
                            color = colors.textPrimary
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_ob_arrow_right),
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Slide 1 – Welcome
// ─────────────────────────────────────────────────────────────
@Composable
private fun Slide1(colors: AppColors) {
    val floatAnim = rememberInfiniteTransition(label = "float")
    val floatY by floatAnim.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "floatY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawSlideGlow(colors.onboardingBlue, 0.24f, 0.12f) }
            .padding(top = 120.dp, start = 32.dp, end = 32.dp, bottom = 160.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ObChip(
                text = stringResource(R.string.ob_slide1_chip),
                textColor = colors.onboardingAccentBlueText,
                bgColor = colors.onboardingAccentBlueChip
            )
            Spacer(modifier = Modifier.height(34.dp))
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .offset(y = floatY.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(colors.onboardingBlue, colors.onboardingBlueDark),
                            start = Offset(0f, 0f),
                            end = Offset(128f, 128f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ob_mic),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
            Spacer(modifier = Modifier.height(36.dp))
            val title = buildAnnotatedString {
                append(stringResource(R.string.ob_slide1_title_part1))
                withStyle(
                    SpanStyle(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF6D7FFF), Color(0xFF9B5CF0))
                        )
                    )
                ) {
                    append(stringResource(R.string.ob_slide1_title_brand))
                }
            }
            Text(
                text = title,
                style = AppTypography.onboardingTitle1,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ob_slide1_body),
                style = AppTypography.onboardingBody,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Slide 2 – Just Speak
// ─────────────────────────────────────────────────────────────
@Composable
private fun Slide2(colors: AppColors) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val p1s by pulse.animateFloat(
        0.85f, 1.35f,
        infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        "p1s"
    )
    val p1a by pulse.animateFloat(
        0.55f, 0f,
        infiniteRepeatable(tween(2400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        "p1a"
    )
    val p2s by pulse.animateFloat(
        0.85f, 1.35f,
        infiniteRepeatable(tween(2400, 800, LinearOutSlowInEasing), RepeatMode.Restart),
        "p2s"
    )
    val p2a by pulse.animateFloat(
        0.55f, 0f,
        infiniteRepeatable(tween(2400, 800, LinearOutSlowInEasing), RepeatMode.Restart),
        "p2a"
    )
    val p3s by pulse.animateFloat(
        0.85f, 1.35f,
        infiniteRepeatable(tween(2400, 1600, LinearOutSlowInEasing), RepeatMode.Restart),
        "p3s"
    )
    val p3a by pulse.animateFloat(
        0.55f, 0f,
        infiniteRepeatable(tween(2400, 1600, LinearOutSlowInEasing), RepeatMode.Restart),
        "p3a"
    )

    var chipVisible by remember { mutableStateOf(false) }
    val chipScale by animateFloatAsState(
        targetValue = if (chipVisible) 1f else 0.8f,
        animationSpec = keyframes {
            durationMillis = 500
            0.8f at 0
            1.04f at 300
            1f at 500
        },
        label = "chipPop"
    )
    val chipAlpha by animateFloatAsState(
        targetValue = if (chipVisible) 1f else 0f,
        animationSpec = tween(300, 300),
        label = "chipAlpha"
    )
    LaunchedEffect(Unit) {
        delay(300)
        chipVisible = true
    }

    val barHeights = remember { listOf(14, 22, 30, 20, 32, 18, 26, 16, 24) }
    val barDurations = remember { listOf(900, 900, 1100, 900, 1300, 1100, 1100, 900, 1300) }
    val barDelays = remember { barHeights.indices.map { i -> (i * 80) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawSlideGlow(colors.onboardingBlue, 0.34f, 0.10f) }
            .padding(top = 120.dp, start = 32.dp, end = 32.dp, bottom = 160.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ObChip(
                text = stringResource(R.string.ob_slide2_chip),
                textColor = colors.onboardingAccentBlueText,
                bgColor = colors.onboardingAccentBlueChip
            )
            Spacer(modifier = Modifier.height(26.dp))
            Box(modifier = Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                PulseRing(p1s, p1a, colors.onboardingRing, 176.dp)
                PulseRing(p2s, p2a, colors.onboardingRing, 176.dp)
                PulseRing(p3s, p3a, colors.onboardingRing, 176.dp)
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(colors.onboardingBlue, colors.onboardingBlueDark),
                                Offset(0f, 0f), Offset(96f, 96f)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ob_mic),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(32.dp)
            ) {
                barHeights.forEachIndexed { i, h ->
                    WaveBar(
                        targetHeightDp = h.dp,
                        durationMs = barDurations[i],
                        delayMs = barDelays[i],
                        barColor = Brush.linearGradient(
                            listOf(Color(0xFF8AA2FF), Color(0xFF4F6DF0)),
                            Offset(0f, 0f), Offset(0f, h.toFloat())
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = stringResource(R.string.ob_slide2_title),
                style = AppTypography.onboardingTitle3,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        colors.onboardingCard,
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
                    )
                    .border(
                        1.dp, colors.onboardingCardBorder,
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 4.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = stringResource(R.string.ob_slide2_bubble),
                    style = AppTypography.onboardingBubbleText,
                    color = colors.textPrimary.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .scale(chipScale)
                        .graphicsLayer { alpha = chipAlpha }
                        .background(colors.onboardingSuccessChip, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ob_check),
                            contentDescription = null,
                            tint = colors.onboardingSuccess,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = stringResource(R.string.ob_slide2_categorized),
                            style = AppTypography.onboardingSuccessChipLabel,
                            color = colors.onboardingSuccess
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Slide 3 – Organize
// ─────────────────────────────────────────────────────────────
@Composable
private fun Slide3(colors: AppColors) {
    data class ExpenseRow(
        val emoji: String,
        val name: Int,
        val category: Int,
        val amount: Int,
        val isIncome: Boolean,
        val delayMs: Int
    )
    val rows = listOf(
        ExpenseRow("💰", R.string.ob_row1_name, R.string.ob_row1_cat, R.string.ob_row1_amount, true, 0),
        ExpenseRow("💵", R.string.ob_row2_name, R.string.ob_row2_cat, R.string.ob_row2_amount, true, 100),
        ExpenseRow("🍕", R.string.ob_row3_name, R.string.ob_row3_cat, R.string.ob_row3_amount, false, 200),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawSlideGlow(colors.onboardingBlue, 0.12f, 0.10f) }
            .padding(top = 120.dp, start = 32.dp, end = 32.dp, bottom = 160.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ObChip(
                text = stringResource(R.string.ob_slide3_chip),
                textColor = colors.onboardingAccentBlueText,
                bgColor = colors.onboardingAccentBlueChip
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.ob_slide3_title),
                style = AppTypography.onboardingTitle2,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ob_slide3_body),
                style = AppTypography.onboardingBodySm,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 270.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.onboardingSuccessChip, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ob_slide3_logged_today),
                    style = AppTypography.onboardingRowSub,
                    color = colors.textSecondary
                )
                Text(
                    text = stringResource(R.string.ob_slide3_entries),
                    style = AppTypography.onboardingRowTitle.copy(fontSize = 15.sp),
                    color = colors.onboardingSuccess
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    val accentColor = if (row.isIncome) colors.onboardingSuccess else colors.onboardingDanger
                    val iconBg = if (row.isIncome) colors.onboardingSuccessChip else colors.onboardingDanger.copy(alpha = 0.12f)
                    val amountColor = if (row.isIncome) colors.onboardingSuccess else colors.textPrimary
                    RiseInRow(delayMs = row.delayMs) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.onboardingCard, RoundedCornerShape(16.dp))
                                .border(1.dp, colors.onboardingCardBorder, RoundedCornerShape(16.dp))
                                .drawBehind {
                                    drawRect(
                                        color = accentColor,
                                        size = Size(3.dp.toPx(), size.height)
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(iconBg, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = row.emoji, fontSize = 18.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(row.name),
                                    style = AppTypography.onboardingRowTitle,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = stringResource(row.category),
                                    style = AppTypography.onboardingRowSub,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            Text(
                                text = stringResource(row.amount),
                                style = AppTypography.onboardingRowTitle,
                                color = amountColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Slide 4 – Understand
// ─────────────────────────────────────────────────────────────
@Composable
private fun Slide4(colors: AppColors) {
    val chartData = remember { listOf(30, 45, 38, 60, 42, 70, 55) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawSlideGlow(colors.onboardingPurple, 0.12f, 0.10f) }
            .padding(top = 120.dp, start = 32.dp, end = 32.dp, bottom = 160.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ObChip(
                text = stringResource(R.string.ob_slide4_chip),
                textColor = colors.onboardingAccentPurpleText,
                bgColor = colors.onboardingAccentPurpleChip
            )
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.ob_slide4_title),
                style = AppTypography.onboardingTitle2,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ob_slide4_body),
                style = AppTypography.onboardingBodySm,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 270.dp)
            )
            Spacer(modifier = Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.onboardingCard, RoundedCornerShape(22.dp))
                    .border(1.dp, colors.onboardingCardBorder, RoundedCornerShape(22.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ob_slide4_this_month),
                        style = AppTypography.onboardingRowTitle,
                        color = colors.textPrimary
                    )
                    Box(
                        modifier = Modifier
                            .background(colors.onboardingSuccessChip, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ob_slide4_net),
                            style = AppTypography.onboardingRowSub,
                            color = colors.onboardingSuccess
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = stringResource(R.string.ob_slide4_income_label),
                        value = stringResource(R.string.ob_slide4_income_value),
                        valueColor = colors.onboardingSuccess,
                        labelColor = colors.textSecondary,
                        bgColor = colors.onboardingStatTile,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = stringResource(R.string.ob_slide4_expense_label),
                        value = stringResource(R.string.ob_slide4_expense_value),
                        valueColor = colors.onboardingDanger,
                        labelColor = colors.textSecondary,
                        bgColor = colors.onboardingStatTile,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    chartData.forEachIndexed { i, h ->
                        val barBrush = if (i == 5) {
                            Brush.linearGradient(
                                listOf(Color(0xFF8AA2FF), Color(0xFF4F6DF0)),
                                Offset(0f, 0f), Offset(0f, 56f)
                            )
                        } else {
                            Brush.linearGradient(listOf(colors.onboardingTrack, colors.onboardingTrack))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(h / 100f)
                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                .background(barBrush)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared composables
// ─────────────────────────────────────────────────────────────

@Composable
private fun ObChip(text: String, textColor: Color, bgColor: Color) {
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text = text, style = AppTypography.onboardingChipLabel, color = textColor)
    }
}

@Composable
private fun PulseRing(ringScale: Float, ringAlpha: Float, ringColor: Color, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .scale(ringScale)
            .graphicsLayer { alpha = ringAlpha }
            .border(1.5.dp, ringColor, CircleShape)
    )
}

@Composable
private fun WaveBar(
    targetHeightDp: Dp,
    durationMs: Int,
    delayMs: Int,
    barColor: Brush
) {
    val anim = rememberInfiniteTransition(label = "bar")
    val barScaleY by anim.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, delayMs, FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "barScale"
    )
    Box(
        modifier = Modifier
            .width(3.5.dp)
            .height(targetHeightDp)
            .graphicsLayer { scaleY = barScaleY }
            .clip(RoundedCornerShape(3.dp))
            .background(barColor)
    )
}

@Composable
private fun RiseInRow(delayMs: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val rowAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "riseAlpha"
    )
    val rowOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 14f,
        animationSpec = tween(500),
        label = "riseY"
    )
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        visible = true
    }
    Box(modifier = Modifier.graphicsLayer { alpha = rowAlpha; translationY = rowOffsetY * density }) {
        content()
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(text = label, style = AppTypography.onboardingStatLabel, color = labelColor)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = AppTypography.onboardingStatValue, color = valueColor)
    }
}

// ─────────────────────────────────────────────────────────────
// Glow background helper
// ─────────────────────────────────────────────────────────────
private fun DrawScope.drawSlideGlow(color: Color, centerYFraction: Float, alphaMax: Float) {
    val cx = size.width * 0.5f
    val cy = size.height * centerYFraction
    val radius = size.width * 0.6f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alphaMax), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius
        ),
        center = Offset(cx, cy),
        radius = radius
    )
}
