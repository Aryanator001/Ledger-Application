package com.example.ai.model

/**
 * The output of intent classification: what the user wants to do,
 * what language they used, and how confident the classifier is.
 */
data class IntentResult(
    val intent: AiIntent,
    val language: DetectedLanguage,
    val confidence: Float   // 0f..1f — heuristic confidence in the classification
)
