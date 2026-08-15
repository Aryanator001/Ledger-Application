package com.example.ui.components.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.ai.state.AiState
import com.example.ai.state.ClarificationPreset
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors

@Composable
fun ClarificationCard(
    state: AiState.ClarificationRequired,
    presetLabels: List<String>,
    onOptionSelected: (ClarificationPreset) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.appColors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = colors.bgSurface,
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {

            // Header: question + close button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(AppSpacing.sm))
                Text(
                    text = state.question,
                    style = AppTypography.headline3,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = colors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.md))

            // Quick-select chips — two rows of up to 3
            val chips = state.presets.mapIndexed { i, preset ->
                val label = presetLabels.getOrNull(i) ?: preset.label
                label to preset
            }
            val firstRow = chips.take(3)
            val secondRow = chips.drop(3)

            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                firstRow.forEach { (label, preset) ->
                    ClarificationChip(label = label, onClick = { onOptionSelected(preset) })
                }
            }
            if (secondRow.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    secondRow.forEach { (label, preset) ->
                        ClarificationChip(label = label, onClick = { onOptionSelected(preset) })
                    }
                }
            }

            // Listening indicator
            if (state.isListening) {
                Spacer(modifier = Modifier.height(AppSpacing.md))
                HorizontalDivider(color = colors.borderLight)
                Spacer(modifier = Modifier.height(AppSpacing.md))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ClarificationMicPulse(color = colors.primary)
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    val displayText = state.partialText.ifBlank { "Listening…" }
                    Text(
                        text = displayText,
                        style = AppTypography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = colors.textTertiary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ClarificationChip(label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.appColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = colors.bgSurface2,
        tonalElevation = 0.dp
    ) {
        Text(
            text = label,
            style = AppTypography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        )
    }
}

@Composable
private fun ClarificationMicPulse(color: Color) {
    val transition = rememberInfiniteTransition(label = "clarification_mic_pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
    }
}
