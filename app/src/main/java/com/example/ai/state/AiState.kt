package com.example.ai.state

import com.example.ai.model.*

sealed class AiState {
    object Idle : AiState()
    object Listening : AiState()
    data class Transcribing(val partialText: String = "") : AiState()
    data class TranscriptReady(val transcript: String) : AiState()
    data class IntentRouting(val transcript: String) : AiState()
    data class Extracting(
        val transcript: String,
        val intent: AiIntent,
        val language: DetectedLanguage = DetectedLanguage.UNKNOWN
    ) : AiState()
    data class Validating(val candidate: CandidateTransaction) : AiState()
    data class ConfirmationRequired(val candidate: CandidateTransaction, val errors: List<ValidationError> = emptyList()) : AiState()
    data class AutoCommitting(val candidate: CandidateTransaction) : AiState()
    data class Committed(val transactionId: String, val candidate: CandidateTransaction) : AiState()
    data class QueryProcessing(val transcript: String) : AiState()
    data class QueryAnswered(val response: QueryResponse) : AiState()
    data class Failed(val reason: FailureReason, val rawTranscript: String? = null) : AiState()
}

val AiState.isInteractive: Boolean
    get() = this is AiState.ConfirmationRequired || this is AiState.Failed || this is AiState.QueryAnswered

val AiState.isBusy: Boolean
    get() = this is AiState.Listening || this is AiState.Transcribing ||
            this is AiState.IntentRouting || this is AiState.Extracting ||
            this is AiState.Validating || this is AiState.AutoCommitting ||
            this is AiState.QueryProcessing
