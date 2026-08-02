package com.example.ai.validation

import com.example.ai.model.CandidateTransaction
import com.example.ai.model.ValidationError
import com.example.data.TransactionRepository

class TransactionValidator(
    private val transactionRepository: TransactionRepository
) {

    /**
     * Run all field validations on the candidate.
     * Returns an empty list if the candidate is valid.
     * This does NOT include duplicate detection — call checkDuplicate() separately.
     */
    fun validateFields(candidate: CandidateTransaction): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        // 1. Amount checks
        if (candidate.amount == null || candidate.amount == 0.0) {
            errors.add(ValidationError.AmountMissing)
        } else if (candidate.amount < 0.0) {
            errors.add(ValidationError.AmountInvalid)
        }

        // 2. Direction check
        if (candidate.direction == null) {
            errors.add(ValidationError.DirectionMissing)
        }

        return errors
    }

    /**
     * Layered duplicate detection against Room.
     *
     * Three time windows, each with a different strictness:
     *   - 0–1 min  (AGGRESSIVE):  amount + direction match is enough
     *   - 1–5 min  (MODERATE):    amount + direction + entity must match
     *   - 5–30 min (WEAK):        amount + direction + entity + category must all match
     *
     * Returns DuplicateSuspected error if a match is found, or null if clean.
     */
    suspend fun checkDuplicate(candidate: CandidateTransaction, resolvedCategoryId: String?): ValidationError.DuplicateSuspected? {
        val now = System.currentTimeMillis()

        // --- Window 1: Aggressive (0–1 minute) ---
        val oneMinAgo = now - 60_000L
        val aggressiveMatch = transactionRepository.findDuplicateAggressive(
            amount = candidate.amount ?: return null,
            transactionType = candidate.direction?.toTransactionType() ?: return null,
            sinceTimestamp = oneMinAgo
        )
        if (aggressiveMatch != null) {
            return ValidationError.DuplicateSuspected(
                existingTransactionId = aggressiveMatch.id,
                similarityScore = 0.95f
            )
        }

        // --- Window 2: Moderate (1–5 minutes) ---
        val fiveMinAgo = now - 300_000L
        val moderateMatch = transactionRepository.findDuplicateModerate(
            amount = candidate.amount,
            transactionType = candidate.direction.toTransactionType() ?: return null,
            entity = candidate.entity ?: "",
            sinceTimestamp = fiveMinAgo,
            untilTimestamp = oneMinAgo
        )
        if (moderateMatch != null) {
            return ValidationError.DuplicateSuspected(
                existingTransactionId = moderateMatch.id,
                similarityScore = 0.80f
            )
        }

        // --- Window 3: Weak (5–30 minutes) ---
        val thirtyMinAgo = now - 1_800_000L
        val weakMatch = transactionRepository.findDuplicateWeak(
            amount = candidate.amount,
            transactionType = candidate.direction.toTransactionType() ?: return null,
            entity = candidate.entity ?: "",
            categoryId = resolvedCategoryId ?: "general",
            sinceTimestamp = thirtyMinAgo,
            untilTimestamp = fiveMinAgo
        )
        if (weakMatch != null) {
            return ValidationError.DuplicateSuspected(
                existingTransactionId = weakMatch.id,
                similarityScore = 0.60f
            )
        }

        return null
    }
}

/**
 * Helper extension: map Direction enum to the string stored in Room ("INCOME" / "EXPENSE").
 * TRANSFER maps to "EXPENSE" per MVP directive.
 */
private fun com.example.ai.model.Direction?.toTransactionType(): String? {
    return when (this) {
        com.example.ai.model.Direction.INCOME -> "INCOME"
        com.example.ai.model.Direction.EXPENSE -> "EXPENSE"
        com.example.ai.model.Direction.TRANSFER -> "EXPENSE"
        com.example.ai.model.Direction.CREDIT -> "CREDIT"
        com.example.ai.model.Direction.DEBIT -> "DEBIT"
        null -> null
    }
}
