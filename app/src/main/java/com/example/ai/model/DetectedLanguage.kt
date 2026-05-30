package com.example.ai.model

/**
 * Language family detected from a transcript.
 * Used to route intent classification to language-specific logic.
 */
enum class DetectedLanguage {
    HINDI,      // predominantly Devanagari script
    ENGLISH,    // predominantly Latin script, no Hindi vocabulary
    HINGLISH,   // Latin script with Hindi vocabulary, or mixed Devanagari + Latin
    UNKNOWN     // could not determine (empty or non-alphabetic input)
}
