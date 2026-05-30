package com.example.ai.model

data class CandidateTransaction(
    val amount: Double?,
    val direction: Direction?,
    val entity: String?,
    val categoryHint: String?,
    val paymentMethodHint: String?,
    val notes: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val rawTranscript: String,
    val confidence: ConfidenceScore,
    val source: TransactionSource = TransactionSource.VOICE
) {
    fun isComplete(): Boolean {
        return amount != null && amount > 0 && direction != null && !entity.isNullOrBlank()
    }
}
