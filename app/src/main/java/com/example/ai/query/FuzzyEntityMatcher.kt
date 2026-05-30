package com.example.ai.query

import com.example.data.TransactionRepository

/**
 * Suggests corrected entity names when a query yields zero results.
 *
 * Strategy: Levenshtein distance ≤ 2 against distinct title values.
 * Returns the closest match, or null if no candidate is within threshold.
 *
 * Substring matches are already handled by LIKE-based aggregate queries
 * upstream, so this only catches genuine typos.
 */
class FuzzyEntityMatcher(
    private val transactionRepository: TransactionRepository
) {

    companion object {
        const val MAX_DISTANCE = 2
    }

    suspend fun suggest(query: String): String? {
        val candidates = transactionRepository.getDistinctTitles()
        if (candidates.isEmpty()) return null

        val target = query.lowercase().trim()

        var bestMatch: String? = null
        var bestDistance = Int.MAX_VALUE

        for (candidate in candidates) {
            val candidateLower = candidate.lowercase()
            if (candidateLower == target) return null
            if (kotlin.math.abs(candidateLower.length - target.length) > MAX_DISTANCE) continue

            val distance = levenshtein(target, candidateLower)
            if (distance < bestDistance && distance <= MAX_DISTANCE) {
                bestDistance = distance
                bestMatch = candidate
            }
        }

        return bestMatch
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        var prev = IntArray(shorter.length + 1) { it }
        var curr = IntArray(shorter.length + 1)

        for (i in 1..longer.length) {
            curr[0] = i
            for (j in 1..shorter.length) {
                val cost = if (longer[i - 1] == shorter[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[shorter.length]
    }
}
