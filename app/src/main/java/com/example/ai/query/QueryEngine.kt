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

        // 1. CACHE LOOKUP — safely. A cache failure must never break the query.
        val cached = try {
            analyticsRepository.tryGet(parsedQuery, timeToken)
        } catch (e: Exception) {
            android.util.Log.e("QueryEngine", "Cache tryGet failed for $timeToken, falling through to Room", e)
            null
        }

        // 2. ANTI-POISONING — trust cache only when it has positive data.
        //    A cached count == 0 is treated as a miss because it was likely
        //    written before any real transactions existed (B2 root cause).
        val aggregate = if (cached != null && cached.count > 0) {
            android.util.Log.d("QueryEngine", "Cache HIT for ${parsedQuery.entity}/$timeToken: ${cached.count} txs")
            cached
        } else {
            if (cached != null && cached.count == 0) {
                android.util.Log.w("QueryEngine", "Ignoring poisoned cache row (count=0), falling through to Room")
            }
            // 3. ROOM AGGREGATION — the canonical source of truth.
            val computed = try {
                if (isBalanceQuery) {
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
            } catch (e: Exception) {
                android.util.Log.e("QueryEngine", "Room aggregation failed", e)
                return QueryResult.Failure(FailureReason.QueryEngineError())
            }

            // 4. WRITE-THROUGH — only cache positive results. Never poison.
            if (computed.count > 0) {
                try {
                    analyticsRepository.put(parsedQuery, timeToken, computed)
                } catch (e: Exception) {
                    android.util.Log.e("QueryEngine", "Cache put failed (non-fatal)", e)
                }
            }
            computed
        }

        // 5. FUZZY CORRECTION — wrapped so a fuzzy bug can't crash the query.
        if (aggregate.count == 0 && parsedQuery.entity != null && fuzzyAttempts < maxFuzzyRecursion) {
            val correction = try {
                fuzzyEntityMatcher.suggest(parsedQuery.entity)
            } catch (e: Exception) {
                android.util.Log.e("QueryEngine", "Fuzzy matcher threw", e)
                null
            }
            if (correction != null && !correction.equals(parsedQuery.entity, ignoreCase = true)) {
                android.util.Log.d("QueryEngine", "Fuzzy: '${parsedQuery.entity}' → '$correction'")
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

        // 6. HEADLINE — Gemini primary, template fallback. Both wrapped.
        val timeLabel = timeTokenResolver.label(timeToken)
        val geminiHeadline = try {
            geminiSynthesizer.synthesize(
                transcript = originalTranscript,
                query = parsedQuery,
                totalAmount = aggregate.totalAmount,
                transactionCount = aggregate.count,
                timeLabel = timeLabel
            )
        } catch (e: Exception) {
            android.util.Log.e("QueryEngine", "Gemini synthesizer threw, using template", e)
            null
        }

        val headline = geminiHeadline ?: try {
            fallbackFormatter.format(
                query = parsedQuery,
                timeToken = timeToken,
                totalAmount = aggregate.totalAmount,
                transactionCount = aggregate.count
            )
        } catch (e: Exception) {
            android.util.Log.e("QueryEngine", "Template formatter also failed", e)
            "Result: ${aggregate.count} transaction(s)"
        }

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
        Direction.CREDIT -> "CREDIT"
        Direction.DEBIT -> "DEBIT"
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
