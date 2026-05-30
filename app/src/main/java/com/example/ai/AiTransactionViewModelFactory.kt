package com.example.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.analytics.AnalyticsRepository
import com.example.ai.extract.CandidateResolver
import com.example.ai.extract.TranscriptNormalizer
import com.example.ai.extract.TransactionExtractor
import com.example.ai.intent.IntentClassifier
import com.example.ai.query.FuzzyEntityMatcher
import com.example.ai.query.GeminiQueryParser
import com.example.ai.query.GeminiQuerySynthesizer
import com.example.ai.query.LocalQueryParser
import com.example.ai.query.QueryEngine
import com.example.ai.query.QueryResponseFormatter
import com.example.ai.query.QueryTriageRouter
import com.example.ai.query.TimeTokenResolver
import com.example.ai.validation.TransactionValidator
import com.example.data.AccountRepository
import com.example.data.TransactionRepository

class AiTransactionViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val intentClassifier: IntentClassifier,
    private val transactionExtractor: TransactionExtractor,
    private val analyticsRepository: AnalyticsRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val timeTokenResolver = TimeTokenResolver()
        val triageRouter = QueryTriageRouter(
            localParser = LocalQueryParser(),
            geminiParser = GeminiQueryParser(),
            timeTokenResolver = timeTokenResolver
        )
        val queryEngine = QueryEngine(
            transactionRepository = transactionRepository,
            analyticsRepository = analyticsRepository,
            triageRouter = triageRouter,
            timeTokenResolver = timeTokenResolver,
            fuzzyEntityMatcher = FuzzyEntityMatcher(transactionRepository),
            geminiSynthesizer = GeminiQuerySynthesizer(),
            fallbackFormatter = QueryResponseFormatter(timeTokenResolver)
        )

        @Suppress("UNCHECKED_CAST")
        return AiTransactionViewModel(
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            intentClassifier = intentClassifier,
            transactionExtractor = transactionExtractor,
            normalizer = TranscriptNormalizer(),
            resolver = CandidateResolver(),
            transactionValidator = TransactionValidator(transactionRepository),
            queryEngine = queryEngine
        ) as T
    }
}
