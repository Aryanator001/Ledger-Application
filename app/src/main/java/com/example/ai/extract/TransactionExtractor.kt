package com.example.ai.extract

import com.example.ai.model.CandidateTransaction

/**
 * Abstraction for transaction extraction from a transcript.
 * Implementations parse a natural-language transcript into a structured
 * CandidateTransaction. Swappable: a future on-device extractor could
 * replace the Gemini one without changing the ViewModel.
 */
interface TransactionExtractor {
    /**
     * Extract a candidate transaction from a (already normalized) transcript.
     * Suspend function — runs network/IO work off the main thread.
     * Returns null on failure (network error, parse error, empty result).
     */
    suspend fun extract(normalizedTranscript: String, originalTranscript: String): CandidateTransaction?
}
