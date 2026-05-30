package com.example.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val analyticsCacheUpdater: com.example.analytics.AnalyticsCacheUpdater? = null
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val allTransactionsWithCategory: Flow<List<TransactionWithCategory>> = transactionDao.getAllTransactionsWithCategory()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()

    suspend fun getAccountCount(): Int = accountDao.getAccountCount()

    suspend fun getTransactionCount(): Int = transactionDao.getTransactionCount()

    suspend fun getAccountById(id: String): Account? = accountDao.getAccountById(id)

    suspend fun getTransactionById(id: String): Transaction? = transactionDao.getTransactionById(id)

    fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>> {
        return if (accountId == "all") {
            transactionDao.getAllTransactions()
        } else {
            transactionDao.getTransactionsByAccount(accountId)
        }
    }

    suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
        analyticsCacheUpdater?.onTransactionInserted(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        val old = transactionDao.getTransactionById(transaction.id)
        transactionDao.updateTransaction(transaction)
        if (old != null) {
            analyticsCacheUpdater?.onTransactionUpdated(old, transaction)
        }
    }

    suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category)
    }

    suspend fun insertAccount(account: Account) {
        accountDao.insertAccount(account)
    }

    suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account)
    }

    suspend fun checkDuplicate(title: String, amount: Double, transactionType: String, timeWindow: Long): Boolean {
        return transactionDao.getDuplicateTransaction(title, amount, transactionType, timeWindow) != null
    }

    suspend fun getPartyBalance(title: String): Double {
        return transactionDao.getPartyBalance(title) ?: 0.0
    }

    suspend fun deleteById(id: String) {
        val tx = transactionDao.getTransactionById(id)
        transactionDao.deleteTransactionById(id)
        if (tx != null) {
            analyticsCacheUpdater?.onTransactionDeleted(tx)
        }
    }

    suspend fun getByDateRange(startMs: Long, endMs: Long): List<Transaction> {
        return transactionDao.getByDateRange(startMs, endMs)
    }

    suspend fun getByAccount(accountId: String): List<Transaction> {
        return transactionDao.getByAccount(accountId)
    }

    suspend fun getByCategory(categoryId: String): List<Transaction> {
        return transactionDao.getByCategory(categoryId)
    }

    suspend fun search(query: String): List<Transaction> {
        return transactionDao.search(query)
    }

    suspend fun findDuplicateAggressive(
        amount: Double,
        transactionType: String,
        sinceTimestamp: Long
    ): Transaction? {
        return transactionDao.findDuplicateAggressive(amount, transactionType, sinceTimestamp)
    }

    suspend fun findDuplicateModerate(
        amount: Double,
        transactionType: String,
        entity: String,
        sinceTimestamp: Long,
        untilTimestamp: Long
    ): Transaction? {
        return transactionDao.findDuplicateModerate(amount, transactionType, entity, sinceTimestamp, untilTimestamp)
    }

    suspend fun findDuplicateWeak(
        amount: Double,
        transactionType: String,
        entity: String,
        categoryId: String,
        sinceTimestamp: Long,
        untilTimestamp: Long
    ): Transaction? {
        return transactionDao.findDuplicateWeak(amount, transactionType, entity, categoryId, sinceTimestamp, untilTimestamp)
    }

    suspend fun getQueryAggregate(
        entity: String?,
        categoryId: String?,
        direction: String?,
        startMs: Long,
        endMs: Long
    ): com.example.ai.query.QueryAggregate {
        val rows = transactionDao.getQueryAggregateRows(entity, categoryId, direction, startMs, endMs)
        val total = rows.sumOf { it.amount }
        val ids = rows.map { it.id }
        return com.example.ai.query.QueryAggregate(
            totalAmount = total,
            count = rows.size,
            transactionIds = ids
        )
    }

    suspend fun getEntityNetBalance(
        entity: String,
        startMs: Long,
        endMs: Long
    ): com.example.ai.query.QueryAggregate {
        val row = transactionDao.getEntityNetBalanceRow(entity, startMs, endMs)
        val net = row?.netAmount ?: 0.0
        val count = row?.rowCount ?: 0
        val ids = row?.idList?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        return com.example.ai.query.QueryAggregate(
            totalAmount = net,
            count = count,
            transactionIds = ids
        )
    }

    suspend fun getDistinctTitles(): List<String> = transactionDao.getDistinctTitles()
}
