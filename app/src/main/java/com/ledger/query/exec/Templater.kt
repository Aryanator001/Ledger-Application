package com.ledger.query.exec

import com.ledger.query.core.QueryFrame

/**
 * Converts a QueryFrame into a grammar template by replacing variable slots
 * (entity, category, time tokens, bare integers) with placeholders.
 *
 * "iss mahine vijay ko kitna diya" → "{TIME} {ENTITY} ko kitna diya"
 *
 * This template is the cache key for the plan cache.
 * Two queries are "the same grammar" if and only if they produce the same template.
 */
object Templater {

    fun templatize(frame: QueryFrame): String {
        val tokens = frame.normalizedQuery.split(" ").filter { it.isNotBlank() }

        val result = tokens.map { token ->
            when {
                frame.consumed[token] == "ENTITY" -> "{ENTITY}"
                frame.consumed[token] == "CATEGORY" -> "{CATEGORY}"
                frame.consumed[token] == "TIME" -> "{TIME}"
                // Bare integers (count, N in "last N days") are variable
                token.toIntOrNull() != null -> "{NUM}"
                else -> token
            }
        }

        // Collapse consecutive same-placeholder tokens:
        // "{ENTITY} {ENTITY}" (two-word name) → "{ENTITY}"
        // "{TIME} {TIME}" (two-word time phrase) → "{TIME}"
        val collapsible = setOf("{ENTITY}", "{CATEGORY}", "{TIME}")
        val collapsed = result.fold(mutableListOf<String>()) { acc, tok ->
            if (tok in collapsible && acc.lastOrNull() == tok) acc
            else { acc += tok; acc }
        }

        return collapsed.joinToString(" ").trim()
    }
}
