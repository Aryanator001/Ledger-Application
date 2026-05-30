package com.example.ai.intent

import com.example.ai.model.IntentResult

/**
 * Abstraction for intent classification.
 * Implementations decide whether a transcript is a transaction or a query.
 * Swappable: a future GeminiIntentClassifier could replace the rule-based one
 * without changing the ViewModel.
 */
interface IntentClassifier {
    fun classify(transcript: String): IntentResult
}
