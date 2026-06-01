package com.example.ai.state

sealed class FailureReason {
    abstract val userMessage: String

    object SttFailed : FailureReason() {
        override val userMessage = "Could not hear you properly"
    }
    object SttPermissionDenied : FailureReason() {
        override val userMessage = "Microphone permission needed. Please enable it in Settings."
    }
    object EmptyTranscript : FailureReason() {
        override val userMessage = "No voice heard"
    }
    data class ExtractionFailed(val detail: String? = null) : FailureReason() {
        override val userMessage = if (detail != null) "Could not process transaction: $detail" else "Could not process your transaction"
    }
    object IntentUnclear : FailureReason() {
        override val userMessage = "Not sure what you want to do"
    }
    data class NetworkError(val message: String) : FailureReason() {
        override val userMessage = "Network error: $message"
    }
    data class Unknown(val message: String) : FailureReason() {
        override val userMessage = "Something went wrong"
    }

    data class QueryParseFailed(val transcript: String) : FailureReason() {
        override val userMessage = "Couldn't understand the question. Try: \"Total expense this month\""
    }

    data class NoResultsFound(val query: com.example.ai.model.ParsedQuery) : FailureReason() {
        override val userMessage = "No entries found for this query"
    }

    data class QueryEngineError(val errorMsg: String? = null) : FailureReason() {
        override val userMessage = if (errorMsg != null) "Error: $errorMsg" else "Something went wrong while answering. Please try again."
    }

    object NoDataYet : FailureReason() {
        override val userMessage = "No transactions yet — record one to get started!"
    }
}
