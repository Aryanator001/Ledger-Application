package com.example.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.model.AiIntent
import com.example.ai.model.CandidateTransaction
import com.example.ai.state.AiState
import com.example.ui.theme.appColors

@Composable
fun AiTranscriptCard(
    state: AiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = state is AiState.Listening ||
            state is AiState.Transcribing ||
            state is AiState.TranscriptReady ||
            state is AiState.IntentRouting ||
            state is AiState.Extracting ||
            state is AiState.Failed

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(8.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.appColors.bgSurface)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Leading icon: mic for listening states, spinner for processing states.
                when (state) {
                    is AiState.Extracting,
                    is AiState.IntentRouting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.appColors.primary
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = when (state) {
                                is AiState.Listening -> MaterialTheme.appColors.primary
                                is AiState.Failed -> MaterialTheme.appColors.textTertiary
                                else -> MaterialTheme.appColors.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val (label, body) = when (state) {
                        is AiState.Listening -> "Listening..." to ""
                        is AiState.Transcribing -> "Hearing you..." to state.partialText
                        is AiState.TranscriptReady -> "Got it" to state.transcript
                        is AiState.IntentRouting -> "Understanding..." to state.transcript
                        is AiState.Extracting -> {
                            val processingLabel = when (state.intent) {
                                is AiIntent.Transaction -> "Processing transaction..."
                                is AiIntent.Query -> "Processing query..."
                                else -> "Processing..."
                            }
                            processingLabel to state.transcript
                        }
                        is AiState.Failed -> "Couldn't catch that" to state.reason.userMessage
                        else -> "" to ""
                    }

                    Text(
                        text = label,
                        color = when (state) {
                            is AiState.Failed -> MaterialTheme.appColors.textTertiary
                            is AiState.Extracting, is AiState.IntentRouting -> MaterialTheme.appColors.primary
                            else -> MaterialTheme.appColors.textSecondary
                        },
                        fontSize = 12.sp
                    )
                    if (body.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = body,
                            color = MaterialTheme.appColors.textPrimary,
                            fontSize = 15.sp,
                            maxLines = 3
                        )
                    }
                }

                // Dismiss button — always available.
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.appColors.textSecondary
                    )
                }
            }
        }
    }
}
