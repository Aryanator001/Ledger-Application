package com.example.ai.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppSpacing
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

    Surface(
        shape = AppShapes.xl,
        color = colors.bgSurface,
        shadowElevation = AppSpacing.sm,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg)
            .scale(0.85f + 0.15f * entrance)
            .alpha(entrance)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.padding(end = AppSpacing.xs)) {
                when (status) {
                    is QuickAddPillStatus.Committed -> Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.success,
                    )
                    else -> Unit
                }
            }

            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = AppTypography.bodyLarge.copy(color = colors.textPrimary),
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
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

            IconButton(onClick = onMicClick, enabled = micAvailable) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    tint = when {
                        !micAvailable -> colors.textDisabled
                        isListening -> colors.primary
                        else -> colors.textSecondary
                    },
                )
            }

            IconButton(onClick = onSubmit, enabled = text.isNotBlank()) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    tint = if (text.isNotBlank()) colors.primary else colors.textDisabled,
                )
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(AppSpacing.xxl)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = colors.textTertiary,
                )
            }
        }
    }
}
