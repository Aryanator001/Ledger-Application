package com.example.data

import kotlinx.coroutines.CancellationException

/** A success is observable only after persistence returns; cancellation stays cancellation. */
class TransactionSaveOperation(private val persist: suspend (Transaction) -> Unit) {
    suspend fun save(transaction: Transaction): Result<String> = try {
        persist(transaction)
        Result.success(transaction.id)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
