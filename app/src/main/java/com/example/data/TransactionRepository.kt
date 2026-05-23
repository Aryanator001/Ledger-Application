package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()

    suspend fun insert(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun checkDuplicate(partyName: String, amount: Double, transactionType: String, timeWindow: Long): Boolean {
        return transactionDao.getDuplicateTransaction(partyName, amount, transactionType, timeWindow) != null
    }

    suspend fun getPartyBalance(partyName: String): Double {
        return transactionDao.getPartyBalance(partyName) ?: 0.0
    }

    suspend fun deleteById(id: Int) {
        transactionDao.deleteTransactionById(id)
    }
}
