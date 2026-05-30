package com.example.ai.intent

import com.example.ai.model.AiIntent
import com.example.ai.model.DetectedLanguage
import com.example.ai.model.IntentResult

/**
 * Rule-based intent classifier.
 * Step 1: detect language.
 * Step 2: route to language-specific keyword classification.
 * Step 3: default to Transaction when signals are ambiguous.
 */
class RuleBasedIntentClassifier(
    private val languageDetector: LanguageDetector = LanguageDetector()
) : IntentClassifier {

    // ---- English keyword sets ----
    private val englishQueryMarkers = setOf(
        "how", "what", "when", "total", "balance", "show", "list",
        "much", "many", "owe", "owed", "remaining", "left", "summary", "report"
    )
    private val englishTransactionMarkers = setOf(
        "paid", "gave", "received", "got", "spent", "bought", "sent",
        "add", "give", "pay", "send", "buy", "purchase"
    )

    // ---- Hinglish keyword sets (includes English markers since Hinglish mixes both) ----
    private val hinglishQueryMarkers = setOf(
        "kitna", "kitne", "kitni", "kya", "kaun", "kab", "kahan", "kaise",
        "batao", "bata", "dikhao", "dikha", "hisab", "hisaab", "baki", "baaki",
        "how", "what", "total", "balance", "show", "much", "owe", "remaining"
    )
    private val hinglishTransactionMarkers = setOf(
        "diya", "diye", "diyaa", "di", "dia",
        "liya", "liye", "li", "lia",
        "mila", "mile",
        "kharch", "kharcha", "kharida", "kharide",
        "bheja", "bheje", "de", "le",
        "paid", "gave", "received", "got", "spent", "bought", "sent", "add", "give", "pay"
    )

    // Strong query words that override transaction verbs.
    // e.g. "kitna diya" = "how much did I give" = QUERY about past data, not a new transaction.
    private val strongQueryWords = setOf(
        "kitna", "kitne", "kitni", "kya", "how", "what", "total", "balance",
        "batao", "dikhao", "hisab", "hisaab", "baki", "baaki", "much", "owe", "remaining"
    )

    override fun classify(transcript: String): IntentResult {
        val language = languageDetector.detect(transcript)

        return when (language) {
            DetectedLanguage.ENGLISH -> classifyWith(
                transcript, language, englishQueryMarkers, englishTransactionMarkers
            )
            DetectedLanguage.HINDI, DetectedLanguage.HINGLISH -> classifyWith(
                transcript, language, hinglishQueryMarkers, hinglishTransactionMarkers
            )
            DetectedLanguage.UNKNOWN -> {
                // Empty or garbage: cannot classify meaningfully.
                if (transcript.isBlank()) {
                    IntentResult(AiIntent.Unknown, language, 0f)
                } else {
                    // Has content but no recognizable script — default to Transaction, low confidence.
                    IntentResult(AiIntent.Transaction, language, 0.3f)
                }
            }
        }
    }

    private fun classifyWith(
        transcript: String,
        language: DetectedLanguage,
        queryMarkers: Set<String>,
        transactionMarkers: Set<String>
    ): IntentResult {
        val words = transcript.lowercase()
            .split(Regex("[^a-z\\u0900-\\u097F]+"))
            .filter { it.isNotBlank() }

        val hasQuestionMark = transcript.contains("?")
        val queryHits = words.count { it in queryMarkers }
        val transactionHits = words.count { it in transactionMarkers }
        val hasStrongQuery = words.any { it in strongQueryWords }

        val (intent, confidence) = when {
            // Strong query word present → Query, even if transaction verbs also appear.
            hasStrongQuery -> AiIntent.Query to 0.9f
            // Question mark and no transaction verbs → Query.
            hasQuestionMark && transactionHits == 0 -> AiIntent.Query to 0.8f
            // Clear transaction, no query signals → Transaction.
            transactionHits > 0 && queryHits == 0 -> AiIntent.Transaction to 0.9f
            // More transaction signals than query signals → Transaction.
            transactionHits > queryHits -> AiIntent.Transaction to 0.7f
            // More query signals than transaction signals → Query.
            queryHits > transactionHits -> AiIntent.Query to 0.7f
            // Ambiguous: default to Transaction (product decision).
            else -> AiIntent.Transaction to 0.4f
        }

        return IntentResult(intent = intent, language = language, confidence = confidence)
    }
}
