package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TransactionSaveOperationTest {
    private val transaction = Transaction("saved-id", "Lunch", 10.0, "food", type = "EXPENSE", date = 0)

    @Test fun successWaitsForPersistenceAndReturnsRealId() = runTest {
        val writing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val operation = TransactionSaveOperation { writing.complete(Unit); release.await() }
        val result = async { operation.save(transaction) }
        writing.await()
        assertFalse(result.isCompleted)
        release.complete(Unit)
        assertEquals("saved-id", result.await().getOrThrow())
    }

    @Test fun failedWriteCannotReportSuccess() = runTest {
        val error = IllegalStateException("Database write failed")
        val result = TransactionSaveOperation { throw error }.save(transaction)
        assertTrue(result.isFailure)
        assertSame(error, result.exceptionOrNull())
    }

    @Test fun cancellationPropagatesInsteadOfBecomingSaveFailure() = runTest {
        val result = async { TransactionSaveOperation { throw CancellationException("Disposed ledger") }.save(transaction) }
        runCurrent()
        assertTrue(result.isCancelled)
    }
}
