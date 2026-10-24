package com.example.ai.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.example.ui.components.pill.PillCircleButton
import com.example.ui.components.pill.PillWaveform
import com.example.ui.components.pill.PremiumPillShell
import com.example.ui.components.pill.RecLightIndicator
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors

sealed class QuickAddPillStatus {
    object Idle : QuickAddPillStatus()
    object Committed : QuickAddPillStatus()
    data class Error(val message: String) : QuickAddPillStatus()
}

@Composable
fun QuickAddPill(
    text: String,
    onTextChange: (String) -> Unit,
    isListening: Boolean,
    micAvailable: Boolean,
    onMicClick: () -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onAddClick: () -> Unit,
    status: QuickAddPillStatus,
    placeholder: String,
) {
    val colors = MaterialTheme.appColors
    val focusRequester = remember { FocusRequester() }
    val entranceTarget = remember { mutableFloatStateOf(0f) }
    val entrance by animateFloatAsState(
        targetValue = entranceTarget.floatValue,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "quickAddPillEntrance",
    )

    LaunchedEffect(Unit) {
        entranceTarget.floatValue = 1f
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .scale(0.85f + 0.15f * entrance)
            .alpha(entrance),
    ) {
        PremiumPillShell(
            leading = {
                PillCircleButton(onClick = onAddClick, ring = colors.pillButtonRing) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add manually",
                        tint = colors.pillButtonIcon,
                    )
                }
            },
            content = {
                if (status is QuickAddPillStatus.Committed) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.success,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                if (isListening) {
                    PillWaveform(modifier = Modifier.weight(1f))
                } else {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        textStyle = AppTypography.bodyLarge.copy(color = colors.textPrimary),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.primary),
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) {
                                Text(
                                    text = if (status is QuickAddPillStatus.Error) status.message else placeholder,
                                    style = AppTypography.bodyLarge,
                                    color = if (status is QuickAddPillStatus.Error) colors.error else colors.textTertiary,
                                )
                            }
                            innerTextField()
                        },
                    )
                }
            },
            trailing = {
                when {
                    isListening -> PillCircleButton(onClick = onMicClick, ring = colors.pillRecRing) {
                        RecLightIndicator()
                    }
                    text.isNotBlank() -> PillCircleButton(onClick = onSubmit, ring = colors.pillButtonRing) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = colors.pillButtonIcon,
                        )
                    }
                    else -> PillCircleButton(
                        onClick = onMicClick,
                        ring = colors.pillButtonRing,
                        enabled = micAvailable,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Start recording",
                            tint = if (micAvailable) colors.pillButtonIcon else colors.textDisabled,
                        )
                    }
                }
            },
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.bgSurface)
                .border(1.dp, colors.borderMedium, CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
