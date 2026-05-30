package com.example

import com.example.ai.model.Direction
import com.example.ai.model.ParsedQuery
import com.example.ai.query.TimeToken
import com.example.ai.query.LocalQueryParser
import com.example.ai.query.TimeTokenResolver
import com.example.ai.query.QueryResponseFormatter

fun main() {
    val parser = LocalQueryParser()
    val transcript = "Ramesh ko kitna diya"
    val result = parser.parse(transcript)
    println("LocalParseResult: ${result}")

    if (result != null) {
        val resolver = TimeTokenResolver()
        val (start, end) = resolver.resolve(result.timeToken)
        val pq = ParsedQuery(
            entity = result.entity,
            direction = result.direction,
            categoryHint = result.categoryHint,
            timeRangeStart = start,
            timeRangeEnd = end,
            rawTranscript = transcript
        )
        println("ParsedQuery: ${pq}")

        val formatter = QueryResponseFormatter(resolver)
        val formatted = formatter.format(pq, result.timeToken, 0.0, 0)
        println("Formatted: ${formatted}")
    }
}
