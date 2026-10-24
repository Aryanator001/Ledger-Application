package com.example.ui.components.inputs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.pill.PillCircleButton
import com.example.ui.components.pill.PillWaveform
import com.example.ui.components.pill.PremiumPillShell
import com.example.ui.components.pill.RecLightIndicator
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors

/**
 * Status the pill reflects in its middle content/trailing button, replacing the old
 * separate AiTranscriptCard popup — listening/transcribing/processing/failed all render
 * inline in the pill now that it's large enough to carry them.
 */
sealed class ChatInputBarStatus {
    object Idle : ChatInputBarStatus()
    object Listening : ChatInputBarStatus()
    data class Transcribing(val text: String) : ChatInputBarStatus()
    data class Processing(val text: String = "") : ChatInputBarStatus()
    data class Failed(val message: String) : ChatInputBarStatus()
}

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onMicClick: () -> Unit,
    onAddClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    status: ChatInputBarStatus = ChatInputBarStatus.Idle,
    placeholder: String = "Describe your expense...",
) {
    val colors = MaterialTheme.appColors
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        PremiumPillShell(
            leading = {
                PillCircleButton(onClick = onAddClick, ring = colors.pillButtonRing) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Manually",
                        tint = colors.pillButtonIcon,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            content = {
                when (status) {
                    is ChatInputBarStatus.Idle -> {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(text = placeholder, color = colors.textTertiary, style = AppTypography.bodyLarge)
                            }
                            BasicTextField(
                                value = value,
                                onValueChange = onValueChange,
                                textStyle = TextStyle(color = colors.textPrimary, fontSize = AppTypography.bodyLarge.fontSize),
                                modifier = Modifier.fillMaxWidth(),
                                cursorBrush = SolidColor(colors.primary),
                            )
                        }
                    }
                    is ChatInputBarStatus.Listening -> PillWaveform(modifier = Modifier.weight(1f))
                    is ChatInputBarStatus.Transcribing -> {
                        Text(
                            text = status.text.ifBlank { "Listening..." },
                            color = colors.textPrimary,
                            style = AppTypography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    is ChatInputBarStatus.Processing -> {
                        Text(
                            text = status.text.ifBlank { "Processing..." },
                            color = colors.textSecondary,
                            style = AppTypography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    is ChatInputBarStatus.Failed -> {
                        Text(
                            text = status.message,
                            color = colors.error,
                            style = AppTypography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            },
            trailing = {
                when {
                    status is ChatInputBarStatus.Listening || status is ChatInputBarStatus.Transcribing -> {
                        PillCircleButton(onClick = onMicClick, ring = colors.pillRecRing) {
                            RecLightIndicator()
                        }
                    }
                    status is ChatInputBarStatus.Processing -> {
                        PillCircleButton(onClick = {}, ring = colors.pillButtonRing, enabled = false) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.pillButtonIcon,
                            )
                        }
                    }
                    status is ChatInputBarStatus.Idle && value.isNotEmpty() -> {
                        PillCircleButton(onClick = onSendClick, ring = colors.pillButtonRing) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = colors.pillButtonIcon,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    else -> {
                        PillCircleButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMicClick()
                            },
                            ring = colors.pillButtonRing,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Input",
                                tint = colors.pillButtonIcon,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            },
        )

        if (status !is ChatInputBarStatus.Idle) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = (-4).dp)
                    .size(22.dp)
                    .background(colors.bgSurface, CircleShape)
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
}
