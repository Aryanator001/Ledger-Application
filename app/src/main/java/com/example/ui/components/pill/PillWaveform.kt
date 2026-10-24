package com.example.ui.components.pill

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.theme.appColors

/**
 * Ambient 31-bar EQ-style waveform shown while recording. Not driven by real audio
 * amplitude — a staggered infinite animation per bar, matching the Figma "Pearl Edge"
 * mock's non-interactive prototype.
 */
@Composable
fun PillWaveform(barCount: Int = 31, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val transition = rememberInfiniteTransition(label = "pillWaveform")
    val fractions = List(barCount) { i ->
        transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 1f,
            label = "pillWaveformBar_$i",
            animationSpec = infiniteRepeatable(
                animation = tween(1450 + (i % 7) * 110, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(i * 113),
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        0.04f to androidx.compose.ui.graphics.Color.Black,
                        0.96f to androidx.compose.ui.graphics.Color.Black,
                        1f to androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gap = size.width / barCount
            val barWidth = 2.dp.toPx()
            fractions.forEachIndexed { index, fraction ->
                val f by fraction
                val baseHeight = when {
                    index % 3 == 0 -> 8.dp.toPx()
                    index % 4 == 1 -> 5.dp.toPx()
                    else -> 6.dp.toPx()
                }
                val barHeight = (baseHeight * (0.6f + f)).coerceAtMost(size.height)
                val x = gap * index + gap / 2f - barWidth / 2f
                val y = (size.height - barHeight) / 2f
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(colors.pillButtonIcon, colors.pillButtonIcon.copy(alpha = 0.35f)),
                    ),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
    }
}

/**
 * Solid red "REC" dot shown in place of the mic icon while recording: blinks on/off
 * and radiates a pulsing ring outward from the trailing button.
 */
@Composable
fun RecLightIndicator(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val transition = rememberInfiniteTransition(label = "recLight")

    val blinkAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        label = "recLightBlink",
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 900
                1f at 0
                1f at 593
                0.35f at 594
                0.35f at 899
            },
        ),
    )

    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        label = "recLightPulse",
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    Box(
        modifier = modifier.size(PillDefaults.ButtonSize),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f
            drawCircle(
                color = colors.pillRecDot.copy(alpha = (1f - pulse) * 0.35f),
                radius = maxRadius * (0.55f + pulse * 0.45f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .graphicsLayer(alpha = blinkAlpha)
                .background(colors.pillRecDot),
        )
    }
}
