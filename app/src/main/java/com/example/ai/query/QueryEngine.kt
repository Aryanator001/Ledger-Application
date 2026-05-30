package com.example.ai.query

import com.example.ai.model.Direction
import com.example.ai.state.FailureReason
import com.example.ai.model.ParsedQuery
import com.example.ai.model.QueryResponse
import com.example.data.TransactionRepository

sealed class QueryResult {
    data class Success(val response: QueryResponse) : QueryResult()
    data class Failure(val reason: FailureReason) : QueryResult()
}

class QueryEngine(
    private val transactionRepository: TransactionRepository,
    private val analyticsRepository: com.example.analytics.AnalyticsRepository,
    private val triageRouter: QueryTriageRouter,
    private val timeTokenResolver: TimeTokenResolver,
    private val fuzzyEntityMatcher: FuzzyEntityMatcher,
    private val geminiSynthesizer: GeminiQuerySynthesizer,
    private val fallbackFormatter: QueryResponseFormatter
) {

    private val maxFuzzyRecursion = 1

    suspend fun process(transcript: String): QueryResult {
        if (transactionRepository.getTransactionCount() == 0) {
            return QueryResult.Failure(FailureReason.NoDataYet)
        }

        val parsedQuery = triageRouter.parse(transcript)
            ?: return QueryResult.Failure(FailureReason.QueryParseFailed(transcript))

        return processInternal(parsedQuery, transcript, fuzzyAttempts = 0)
    }

    private suspend fun processInternal(
        parsedQuery: ParsedQuery,
        originalTranscript: String,
        fuzzyAttempts: Int
    ): QueryResult {
        val timeToken = inferTimeToken(parsedQuery)
        val isBalanceQuery = parsedQuery.entity != null && parsedQuery.direction == null

        // 1. Try cache first
        val cached = analyticsRepository.tryGet(parsedQuery, timeToken)

        val aggregate = if (cached != null) {
            cached
        } else {
            // 2. Cache miss — direct Room aggregation
            val computed = if (isBalanceQuery) {
                transactionRepository.getEntityNetBalance(
                    entity = parsedQuery.entity!!,
                    startMs = parsedQuery.timeRangeStart ?: 0L,
                    endMs = parsedQuery.timeRangeEnd ?: Long.MAX_VALUE
                )
            } else {
                transactionRepository.getQueryAggregate(
                    entity = parsedQuery.entity,
                    categoryId = parsedQuery.categoryHint,
                    direction = parsedQuery.direction?.toRoomTypeString(),
                    startMs = parsedQuery.timeRangeStart ?: 0L,
                    endMs = parsedQuery.timeRangeEnd ?: Long.MAX_VALUE
                )
            }
            // 3. Populate cache for next time (skips uncacheable keys silently)
            analyticsRepository.put(parsedQuery, timeToken, computed)
            computed
        }

        // 4. Zero-result fuzzy correction (unchanged from Stage 1)
        if (aggregate.count == 0 && parsedQuery.entity != null && fuzzyAttempts < maxFuzzyRecursion) {
            val correction = fuzzyEntityMatcher.suggest(parsedQuery.entity)
            if (correction != null && !correction.equals(parsedQuery.entity, ignoreCase = true)) {
                return processInternal(
                    parsedQuery = parsedQuery.copy(entity = correction),
                    originalTranscript = originalTranscript,
                    fuzzyAttempts = fuzzyAttempts + 1
                )
            }
        }

        if (aggregate.count == 0 && !isBalanceQuery) {
            return QueryResult.Failure(FailureReason.NoResultsFound(parsedQuery))
        }

        val timeLabel = timeTokenResolver.label(timeToken)
        val headline = geminiSynthesizer.synthesize(
            transcript = originalTranscript,
            query = parsedQuery,
            totalAmount = aggregate.totalAmount,
            transactionCount = aggregate.count,
            timeLabel = timeLabel
        ) ?: fallbackFormatter.format(
            query = parsedQuery,
            timeToken = timeToken,
            totalAmount = aggregate.totalAmount,
            transactionCount = aggregate.count
        )

        return QueryResult.Success(
            QueryResponse(
                headline = headline,
                totalAmount = aggregate.totalAmount,
                transactionCount = aggregate.count,
                transactionIds = aggregate.transactionIds,
                query = parsedQuery
            )
        )
    }

    private fun inferTimeToken(query: ParsedQuery): TimeToken {
        val start = query.timeRangeStart ?: 0L
        val end = query.timeRangeEnd ?: Long.MAX_VALUE
        if (start == 0L && end == Long.MAX_VALUE) return TimeToken.LIFETIME

        for (token in listOf(
            TimeToken.TODAY, TimeToken.YESTERDAY,
            TimeToken.THIS_WEEK, TimeToken.LAST_WEEK,
            TimeToken.THIS_MONTH, TimeToken.LAST_MONTH
        )) {
            val (bStart, bEnd) = timeTokenResolver.resolve(token)
            if (start == bStart && end == bEnd) return token
        }
        return TimeToken.CUSTOM
    }

    private fun Direction.toRoomTypeString(): String = when (this) {
        Direction.INCOME -> "INCOME"
        Direction.EXPENSE -> "EXPENSE"
        Direction.TRANSFER -> "EXPENSE"
    }
}

/**
 * Lightweight value object the DAO returns. Sits in the same package
 * so the repository layer can pass it through cleanly.
 */
data class QueryAggregate(
    val totalAmount: Double,
    val count: Int,
    val transactionIds: List<String>
)
