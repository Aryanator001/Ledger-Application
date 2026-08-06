package com.example.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ai.extract.CandidateResolver
import com.example.ai.extract.TranscriptNormalizer
import com.example.ai.extract.TransactionExtractor
import com.example.ai.intent.IntentClassifier
import com.example.ai.validation.TransactionValidator
import com.example.data.AccountRepository
import com.example.data.AppDatabase
import com.example.data.TransactionRepository
import com.ledger.query.engine.QueryEngineFactory

class AiTransactionViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val intentClassifier: IntentClassifier,
    private val transactionExtractor: TransactionExtractor,
    private val database: AppDatabase,
    private val scopeIdProvider: () -> String,
    private val geminiApiKeyProvider: () -> String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val queryEngine = QueryEngineFactory.create(
            transactionDao = database.transactionDao(),
            queryPlanCacheDao = database.queryPlanCacheDao(),
            scopeIdProvider = scopeIdProvider,
            apiKeyProvider = geminiApiKeyProvider,
            entityNamesProvider = { emptyList() }  // ViewModel refreshes from DB in init
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
