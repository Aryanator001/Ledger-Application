package com.example.ai.model

sealed class ValidationError {
    abstract val userMessage: String

    object AmountMissing : ValidationError() {
        override val userMessage = "Amount missing"
    }
    object AmountInvalid : ValidationError() {
        override val userMessage = "Amount invalid"
    }
    object DirectionMissing : ValidationError() {
        override val userMessage = "Direction missing"
    }
    object EntityMissing : ValidationError() {
        override val userMessage = "Name or entity missing"
    }
    data class DuplicateSuspected(val existingTransactionId: String, val similarityScore: Float) : ValidationError() {
        override val userMessage = "Looks like duplicate"
    }
    data class UnknownCategory(val rawHint: String) : ValidationError() {
        override val userMessage = "Unknown category: $rawHint"
    }
    data class UnknownAccount(val rawHint: String) : ValidationError() {
        override val userMessage = "Unknown account: $rawHint"
    }
}
