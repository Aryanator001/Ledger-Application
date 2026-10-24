package com.example.ai.validation

import com.example.ai.model.CandidateTransaction
import com.example.ai.model.ConfidenceScore
import com.example.ai.model.Direction
import org.junit.Assert.*
import org.junit.Test

class CandidateAmountTest {
    @Test fun onlyFinitePositiveAmountsCanBeComplete() {
        val candidate = CandidateTransaction(10.0, Direction.EXPENSE, "Lunch", "food", "cash", null,
            rawTranscript = "paid ten", confidence = ConfidenceScore(1f))
        assertTrue(candidate.isComplete())
        for (amount in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(candidate.copy(amount = amount).isComplete())
        }
    }
}
