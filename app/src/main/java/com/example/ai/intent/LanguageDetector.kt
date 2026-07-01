package com.example.ai.intent

import com.example.ai.model.DetectedLanguage

/**
 * Detects the language family of a transcript using script analysis
 * and a Hindi vocabulary lookup. Fully on-device, synchronous.
 */
class LanguageDetector {

    // Common Hindi words frequently transcribed in Latin script (Hinglish).
    private val hinglishVocabulary = setOf(
        "ko", "ka", "ki", "ke", "se", "mein", "me", "ne", "par", "tak",
        "diya", "diye", "diyaa", "di", "dia",
        "liya", "liye", "li", "lia",
        "mila", "mile",
        "rupaye", "rupye", "rupaiya", "paisa", "paise",
        "kitna", "kitne", "kitni", "kya", "kaun", "kab", "kahan", "kaise",
        "kharch", "kharcha", "kharida", "kharide",
        "bheja", "bheje", "aaya", "aaye", "gaya", "gaye",
        "udhaar", "udhar", "hisab", "hisaab", "baki", "baaki",
        "batao", "bata", "dikhao", "dikha", "wala", "waala", "bhai", "ji",
        // Time reference words
        "aaj", "kal", "parso", "pichle", "pichhle", "mahine", "hafte", "saal",
        // Transaction verbs
        "jama", "nikala", "nikali", "nikale", "kata", "kati", "kate",
        "wapas", "vapas", "becha", "bechi", "khareed",
        "utha", "uthai", "bhara", "bhari",
        // Account / reporting words
        "khata", "hisaab"
    )

    fun detect(transcript: String): DetectedLanguage {
        if (transcript.isBlank()) return DetectedLanguage.UNKNOWN

        val devanagariCount = transcript.count { it in '\u0900'..'\u097F' }
        val latinCount = transcript.count { it in 'a'..'z' || it in 'A'..'Z' }
        val total = devanagariCount + latinCount

        if (total == 0) return DetectedLanguage.UNKNOWN

        val devanagariRatio = devanagariCount.toFloat() / total

        // Mostly Devanagari → Hindi; with some Latin mixed in → Hinglish.
        if (devanagariRatio > 0.6f) {
            return if (latinCount > 0) DetectedLanguage.HINGLISH else DetectedLanguage.HINDI
        }

        // Both scripts present in meaningful amounts → Hinglish.
        if (devanagariCount > 0 && latinCount > 0) {
            return DetectedLanguage.HINGLISH
        }

        // All Latin: decide English vs Hinglish by vocabulary.
        val words = transcript.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        val hinglishHits = words.count { it in hinglishVocabulary }

        return if (hinglishHits > 0) DetectedLanguage.HINGLISH else DetectedLanguage.ENGLISH
    }
}
