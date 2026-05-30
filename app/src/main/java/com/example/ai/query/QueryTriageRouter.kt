package com.example.ai.query

import com.example.ai.model.Direction
import com.example.ai.model.ParsedQuery

/**
 * Routes between local and Gemini parsing.
 *
 * Strategy:
 *   1. Run LocalQueryParser
 *   2. If confidence >= 0.70, use local result
 *   3. Else invoke GeminiQueryParser
 *   4. If both fail, return null → caller routes to Failed(QueryParseFailed)
 *
 * Time resolution happens here so the caller receives a fully-built ParsedQuery.
 */
class QueryTriageRouter(
    private val localParser: LocalQueryParser,
    private val geminiParser: GeminiQueryParser,
    private val timeTokenResolver: TimeTokenResolver
) {

    companion object {
        const val LOCAL_CONFIDENCE_THRESHOLD = 0.70f
    }

    suspend fun parse(transcript: String): ParsedQuery? {
        val localResult = localParser.parse(transcript)
        if (localResult != null && localResult.confidence >= LOCAL_CONFIDENCE_THRESHOLD) {
            return buildParsedQuery(
                transcript = transcript,
                entity = localResult.entity,
                direction = localResult.direction,
                categoryHint = localResult.categoryHint,
                timeToken = localResult.timeToken,
                customStartDate = null,
                customEndDate = null
            )
        }

        val geminiResult = geminiParser.parse(transcript) ?: return null

        return buildParsedQuery(
            transcript = transcript,
            entity = geminiResult.entity,
            direction = geminiResult.direction,
            categoryHint = geminiResult.categoryHint,
            timeToken = geminiResult.timeToken,
            customStartDate = geminiResult.customStartDate,
            customEndDate = geminiResult.customEndDate
        )
    }

    private fun buildParsedQuery(
        transcript: String,
        entity: String?,
        direction: Direction?,
        categoryHint: String?,
        timeToken: TimeToken,
        customStartDate: String?,
        customEndDate: String?
    ): ParsedQuery {
        val (startMs, endMs) = if (timeToken == TimeToken.CUSTOM
            && customStartDate != null
            && customEndDate != null) {
            timeTokenResolver.resolveCustom(customStartDate, customEndDate)
        } else {
            timeTokenResolver.resolve(timeToken)
        }

        return ParsedQuery(
            entity = entity,
            direction = direction,
            categoryHint = categoryHint,
            timeRangeStart = startMs,
            timeRangeEnd = endMs,
            rawTranscript = transcript
        )
    }
}
