package com.example.ai.model

data class ConfidenceScore(
    val overall: Float,
    val amount: Float = 0f,
    val direction: Float = 0f,
    val entity: Float = 0f,
    val category: Float = 0f,
    val paymentMethod: Float = 0f,
    val transcriptQuality: Float = 0f
) {
    companion object {
        const val HIGH_THRESHOLD = 0.90f
        const val MEDIUM_THRESHOLD = 0.70f

        fun tier(overall: Float): ConfidenceTier {
            return when {
                overall >= HIGH_THRESHOLD -> ConfidenceTier.HIGH
                overall >= MEDIUM_THRESHOLD -> ConfidenceTier.MEDIUM
                else -> ConfidenceTier.LOW
            }
        }
    }
}

enum class ConfidenceTier { HIGH, MEDIUM, LOW }
