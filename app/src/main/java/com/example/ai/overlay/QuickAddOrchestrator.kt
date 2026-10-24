package com.example.ai.overlay

import com.example.ai.extract.CandidateResolver
import com.example.ai.extract.GeminiTransactionExtractor
import com.example.ai.extract.TranscriptNormalizer
import com.example.ai.extract.TransactionExtractor
import com.example.ai.model.CandidateTransaction
import com.example.ai.model.Direction
import com.example.ai.model.TransactionSource
import com.example.data.Transaction
import com.example.data.TransactionRepository
import com.example.data.UserPreferences
import kotlinx.coroutines.flow.first

sealed class QuickAddResult {
    data class Committed(val transaction: Transaction) : QuickAddResult()
    data class Failed(val message: String) : QuickAddResult()
}

/**
 * Collapsed version of AiTransactionViewModel's extract -> validate -> commit flow for the
 * overlay pill: no confirmation step, any parsed candidate (even low-confidence/incomplete)
 * is committed. The only case that writes nothing is a hard extraction failure with zero
 * usable data — writing a junk amount-less placeholder would be worse than surfacing an error.
 */
class QuickAddOrchestrator(
    private val transactionExtractor: TransactionExtractor = GeminiTransactionExtractor(),
    private val normalizer: TranscriptNormalizer = TranscriptNormalizer(),
    private val resolver: CandidateResolver = CandidateResolver(),
    private val transactionRepository: TransactionRepository,
    private val userPreferences: UserPreferences? = null,
) {
    suspend fun handle(transcript: String, source: TransactionSource = TransactionSource.TYPED): QuickAddResult {
        if (transcript.isBlank()) return QuickAddResult.Failed("Nothing to save")

        val normalized = normalizer.normalize(transcript)
        val rawCandidate = try {
            transactionExtractor.extract(normalized, transcript) ?: return QuickAddResult.Failed("Couldn't understand that")
        } catch (e: Exception) {
            return QuickAddResult.Failed(e.message ?: "Couldn't understand that")
        }

        val resolvedEntity = rawCandidate.entity?.takeIf { it.isNotBlank() }
            ?: rawCandidate.notes?.split(" ")?.firstOrNull()?.replaceFirstChar { it.uppercase() }
            ?: "General"
        val candidate: CandidateTransaction = rawCandidate.copy(entity = resolvedEntity, source = source)
        val amount = candidate.amount
        if (amount == null || amount <= 0) return QuickAddResult.Failed("Couldn't understand the amount")

        val categoryId = resolver.resolveCategoryId(candidate.categoryHint)
        val accountId = resolver.resolveAccountId(candidate.paymentMethodHint)
        val type = when (candidate.direction) {
            Direction.INCOME -> "INCOME"
            Direction.EXPENSE, Direction.TRANSFER -> "EXPENSE"
            Direction.CREDIT -> "CREDIT"
            Direction.DEBIT -> "DEBIT"
            null -> "EXPENSE"
        }
        val toAccountId = if (candidate.direction == Direction.TRANSFER) "default_cash" else null
        val scopeId = userPreferences?.activeLedgerScopeId?.first()

        val transaction = Transaction(
            title = candidate.entity ?: "General",
            amount = amount,
            categoryId = categoryId,
            accountId = accountId,
            toAccountId = toAccountId,
            type = type,
            date = candidate.timestamp,
            description = candidate.notes,
            aiCategorized = true,
            rawTranscript = candidate.rawTranscript,
            ledgerScopeId = scopeId,
            paymentMethod = candidate.paymentMethodHint,
        )

        transactionRepository.insertTransaction(transaction)
        return QuickAddResult.Committed(transaction)
    }
}
