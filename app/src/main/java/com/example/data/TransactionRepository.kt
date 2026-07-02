package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val userPreferences: UserPreferences,
    private val analyticsCacheUpdater: com.example.analytics.AnalyticsCacheUpdater? = null
) {
    private suspend fun getScopeId(): String = userPreferences.activeLedgerScopeId.first() ?: ""

    val allTransactions: Flow<List<Transaction>> = userPreferences.activeLedgerScopeId.flatMapLatest { scopeId ->
        if (scopeId == null) flowOf(emptyList()) else transactionDao.getAllTransactions(scopeId)
    }

    val allTransactionsWithCategory: Flow<List<TransactionWithCategory>> = userPreferences.activeLedgerScopeId.flatMapLatest { scopeId ->
        if (scopeId == null) flowOf(emptyList()) else transactionDao.getAllTransactionsWithCategory(scopeId)
    }

    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    
    val allAccounts: Flow<List<Account>> = userPreferences.activeLedgerScopeId.flatMapLatest { scopeId ->
        if (scopeId == null) flowOf(emptyList()) else accountDao.getAllAccounts(scopeId)
    }

    suspend fun getAccountCount(): Int = accountDao.getAccountCount(getScopeId())

    suspend fun getTransactionCount(): Int = transactionDao.getTransactionCount(getScopeId())

    suspend fun getAccountById(id: String): Account? = accountDao.getAccountById(id, getScopeId())

    suspend fun getTransactionById(id: String): Transaction? = transactionDao.getTransactionById(id, getScopeId())

    fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>> {
        return userPreferences.activeLedgerScopeId.flatMapLatest { scopeId ->
            if (scopeId == null) flowOf(emptyList())
            else if (accountId == "all") transactionDao.getAllTransactions(scopeId)
            else transactionDao.getTransactionsByAccount(accountId, scopeId)
        }
    }

    suspend fun insertTransaction(transaction: Transaction) {
        val finalTransaction = if (transaction.ledgerScopeId == null) transaction.copy(ledgerScopeId = getScopeId()) else transaction
        transactionDao.insertTransaction(finalTransaction)
        analyticsCacheUpdater?.onTransactionInserted(finalTransaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        val scopeId = transaction.ledgerScopeId ?: getScopeId()
        val finalTransaction = if (transaction.ledgerScopeId == null) transaction.copy(ledgerScopeId = scopeId) else transaction
        val old = transactionDao.getTransactionById(finalTransaction.id, scopeId)
        transactionDao.updateTransaction(finalTransaction)
        if (old != null) {
            analyticsCacheUpdater?.onTransactionUpdated(old, finalTransaction)
        }
    }

    suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category)
    }

    suspend fun insertAccount(account: Account) {
        val finalAccount = if (account.ledgerScopeId == null) account.copy(ledgerScopeId = getScopeId()) else account
        accountDao.insertAccount(finalAccount)
    }

    suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account)
    }

    suspend fun checkDuplicate(title: String, amount: Double, transactionType: String, timeWindow: Long): Boolean {
        return transactionDao.getDuplicateTransaction(title, amount, transactionType, timeWindow, getScopeId()) != null
    }

    suspend fun getPartyBalance(title: String): Double {
        return transactionDao.getPartyBalance(title, getScopeId()) ?: 0.0
    }

    suspend fun deleteById(id: String) {
        val scopeId = getScopeId()
        val tx = transactionDao.getTransactionById(id, scopeId)
        transactionDao.deleteTransactionById(id, scopeId)
        if (tx != null) {
            analyticsCacheUpdater?.onTransactionDeleted(tx)
        }
    }

    suspend fun getByDateRange(startMs: Long, endMs: Long): List<Transaction> {
        return transactionDao.getByDateRange(startMs, endMs, getScopeId())
    }

    suspend fun getByAccount(accountId: String): List<Transaction> {
        return transactionDao.getByAccount(accountId, getScopeId())
    }

    suspend fun getByCategory(categoryId: String): List<Transaction> {
        return transactionDao.getByCategory(categoryId, getScopeId())
    }

    suspend fun search(query: String): List<Transaction> {
        return transactionDao.search(query, getScopeId())
    }

    suspend fun findDuplicateAggressive(
        amount: Double,
        transactionType: String,
        sinceTimestamp: Long
    ): Transaction? {
        return transactionDao.findDuplicateAggressive(amount, transactionType, getScopeId(), sinceTimestamp)
    }

    suspend fun findDuplicateModerate(
        amount: Double,
        transactionType: String,
        entity: String,
        sinceTimestamp: Long,
        untilTimestamp: Long
    ): Transaction? {
        return transactionDao.findDuplicateModerate(amount, transactionType, entity, sinceTimestamp, untilTimestamp, getScopeId())
    }

    suspend fun findDuplicateWeak(
        amount: Double,
        transactionType: String,
        entity: String,
        categoryId: String,
        sinceTimestamp: Long,
        untilTimestamp: Long
    ): Transaction? {
        return transactionDao.findDuplicateWeak(amount, transactionType, entity, categoryId, sinceTimestamp, untilTimestamp, getScopeId())
    }

    suspend fun getQueryAggregate(
        entity: String?,
        categoryId: String?,
        direction: String?,
        startMs: Long,
        endMs: Long
    ): com.example.ai.query.QueryAggregate {
        val rows = transactionDao.getQueryAggregateRows(entity, categoryId, direction, startMs, endMs, getScopeId())
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
        val row = transactionDao.getEntityNetBalanceRow(entity, startMs, endMs, getScopeId())
        val net = row?.netAmount ?: 0.0
        val count = row?.rowCount ?: 0
        val ids = row?.idList?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        return com.example.ai.query.QueryAggregate(
            totalAmount = net,
            count = count,
            transactionIds = ids
        )
    }

    suspend fun getDistinctTitles(): List<String> = transactionDao.getDistinctTitles(getScopeId())

    suspend fun getIncomeExpenseTotalsForRange(startMs: Long, endMs: Long) =
        transactionDao.getIncomeExpenseTotalsForRange(startMs, endMs, getScopeId())

    suspend fun getAllTransactionsOnce(): List<Transaction> =
        transactionDao.getAllTransactionsOnce(getScopeId())
}
