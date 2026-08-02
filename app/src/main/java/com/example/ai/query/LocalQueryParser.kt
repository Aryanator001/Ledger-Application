package com.example.ai.query

import com.example.ai.model.Direction
import com.example.ai.model.ParsedQuery

/**
 * Internal parsed result before time resolution.
 * Carries the symbolic TimeToken; TimeTokenResolver converts it to ms.
 */
internal data class LocalParseResult(
    val entity: String?,
    val direction: Direction?,
    val categoryHint: String?,
    val timeToken: TimeToken,
    val confidence: Float
)

class LocalQueryParser {

    private val timeKeywords = mapOf(
        // English
        "today" to TimeToken.TODAY,
        "yesterday" to TimeToken.YESTERDAY,
        "this week" to TimeToken.THIS_WEEK,
        "last week" to TimeToken.LAST_WEEK,
        "this month" to TimeToken.THIS_MONTH,
        "last month" to TimeToken.LAST_MONTH,
        // Hinglish / Hindi (Latin script)
        "aaj" to TimeToken.TODAY,
        "kal" to TimeToken.YESTERDAY,    // assumes past context in query path
        "is hafte" to TimeToken.THIS_WEEK,
        "iss hafte" to TimeToken.THIS_WEEK,
        "pichhle hafte" to TimeToken.LAST_WEEK,
        "pichle hafte" to TimeToken.LAST_WEEK,
        "is mahine" to TimeToken.THIS_MONTH,
        "iss mahine" to TimeToken.THIS_MONTH,
        "is mahene" to TimeToken.THIS_MONTH,
        "pichhle mahine" to TimeToken.LAST_MONTH,
        "pichle mahine" to TimeToken.LAST_MONTH,
        "pichle mahene" to TimeToken.LAST_MONTH
    )

    private val sortedTimeKeywords = timeKeywords.keys.sortedByDescending { it.length }

    private val expenseVerbs = setOf(
        "diya", "diye", "diya tha", "diye the", "dii", "spent", "kharch", "paid",
        "kharcha", "kharche"
    )

    private val incomeVerbs = setOf(
        "liya", "liye", "lii", "mila", "milya", "received", "kamai", "aaya", "aaye"
    )

    private val balanceWords = setOf(
        "balance", "hisab", "hisaab", "baki", "baaki", "bacha", "outstanding"
    )

    private val totalKeywords = setOf(
        "total", "kul", "sab", "sara", "saara", "overall"
    )

    private val expenseNouns = setOf(
        "expense", "expenses", "kharch", "kharcha", "spent", "spending", "outflow"
    )

    private val incomeNouns = setOf(
        "income", "kamai", "earning", "earnings", "received", "inflow"
    )

    // Category hints map to seeded category IDs
    private val categoryKeywords = mapOf(
        "food" to "food",
        "khana" to "food",
        "khane" to "food",
        "rent" to "rent",
        "kiraya" to "rent",
        "kiraye" to "rent",
        "shopping" to "shopping",
        "kharidari" to "shopping"
    )

    /**
     * Parse a transcript into structured query intent.
     * Returns null if no pattern matches with reasonable confidence.
     */
    internal fun parse(transcript: String): LocalParseResult? {
        val normalized = transcript.lowercase().trim()
        if (normalized.isBlank()) return null

        val timeToken = detectTimeToken(normalized)
        val categoryHint = detectCategory(normalized)
        val entity = extractEntity(normalized)
        val direction = detectDirection(normalized, hasEntity = entity != null)

        // Confidence gate: require AT LEAST ONE of these to be present.
        // Pure unstructured text like "kuch dikha do" should NOT parse.
        val hasSignal = entity != null
                || categoryHint != null
                || direction != null
                || totalKeywords.any { normalized.contains(it) }
                || timeToken != TimeToken.LIFETIME

        if (!hasSignal) return null

        var signalCount = 0
        if (entity != null) signalCount++
        if (direction != null) signalCount++
        if (categoryHint != null) signalCount++
        if (timeToken != TimeToken.LIFETIME) signalCount++

        val confidence = when {
            signalCount >= 2 -> 0.95f
            signalCount == 1 && totalKeywords.any { normalized.contains(it) } -> 0.85f
            signalCount == 1 -> 0.65f
            else -> 0.0f
        }

        return LocalParseResult(
            entity = entity,
            direction = direction,
            categoryHint = categoryHint,
            timeToken = timeToken,
            confidence = confidence
        )
    }

    private fun detectTimeToken(text: String): TimeToken {
        // Multi-word keys checked first (longest match wins)
        for (key in sortedTimeKeywords) {
            if (text.contains(key)) return timeKeywords.getValue(key)
        }
        return TimeToken.LIFETIME
    }

    private fun detectCategory(text: String): String? {
        for ((keyword, categoryId) in categoryKeywords) {
            // Match whole word boundaries to avoid "food" matching "foothold"
            val regex = Regex("\\b${Regex.escape(keyword)}\\b")
            if (regex.containsMatchIn(text)) return categoryId
        }
        return null
    }

    private fun detectDirection(text: String, hasEntity: Boolean): Direction? {
        val tokens = text.split(Regex("\\s+"))
        val hasBalance = balanceWords.any { it in text }
        if (hasBalance) return null  // Balance queries = net = no direction filter

        val hasExpense = expenseVerbs.any { it in tokens } || expenseNouns.any { it in tokens }
        val hasIncome = incomeVerbs.any { it in tokens } || incomeNouns.any { it in tokens }

        return when {
            hasExpense && !hasIncome -> Direction.EXPENSE
            hasIncome && !hasExpense -> Direction.INCOME
            // If entity is present without clear verb, default per user directive:
            // "{name} ko kitna diya" without explicit direction → EXPENSE (most common case)
            hasEntity && !hasExpense && !hasIncome -> Direction.EXPENSE
            else -> null
        }
    }

    /**
     * Extract entity name. Strategy: look for capitalized words in the ORIGINAL
     * (non-lowercased) transcript, or names that follow "ko" / "se" / "ka" / "ki".
     * Returns the longest plausible candidate.
     *
     * Note: we accept the lowercased text here; the caller passes the lowercased
     * transcript. For pattern-based extraction this is sufficient — we look for
     * tokens between known prepositions and verbs.
     */
    private fun extractEntity(text: String): String? {
        // Pattern: "{name} ko ..." or "{name} se ..." or "{name} ka ..."
        // Captures the word(s) immediately before "ko" / "se" / "ka" / "ki"
        val patterns = listOf(
            Regex("""([a-z]+(?:\s+[a-z]+)?)\s+(ko|se|ka|ki)\s+"""),
            Regex("""(?:from|to)\s+([a-z]+(?:\s+[a-z]+)?)\s+""")
        )

        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val candidate = match.groupValues[1].trim()
            // Reject obvious non-entities (common words)
            if (candidate in stopWords) continue
            if (candidate.length < 2) continue
            return candidate
        }
        return null
    }

    private val stopWords = setOf(
        "kitna", "kitne", "kya", "how", "what", "much", "many",
        "total", "kul", "sab", "is", "this", "last", "today",
        "yesterday", "aaj", "kal", "month", "week", "mahine",
        "hafte", "expense", "income", "balance",
        "is mahine", "iss mahine", "pichle mahine", "pichhle mahine",
        "is hafte", "iss hafte", "pichle hafte", "pichhle hafte",
        "is mahene", "pichle mahene"
    )
}
