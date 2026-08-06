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
        if (direction == null) {
            // Net balance: sign-correct in SQL (INCOME/CREDIT = +, everything else = -).
            // Summing raw amounts in-memory when direction=null gives gross volume (wrong).
            val row = transactionDao.getNetBalanceRow(entity, categoryId, startMs, endMs, getScopeId())
            val net   = row?.netAmount ?: 0.0
            val count = row?.rowCount  ?: 0
            val ids   = row?.idList?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            return com.example.ai.query.QueryAggregate.fromDouble(net, count, ids)
        } else {
            // Directional query: SQL already filters to one sign (all amounts are same-sign).
            val rows = transactionDao.getQueryAggregateRows(entity, categoryId, direction, startMs, endMs, getScopeId())
            return com.example.ai.query.QueryAggregate.fromDouble(
                rows.sumOf { it.amount },
                rows.size,
                rows.map { it.id }
            )
        }
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
        return com.example.ai.query.QueryAggregate.fromDouble(net, count, ids)
    }

    suspend fun getQueryAggregatePaise(
        entity: String?,
        categoryId: String?,
        direction: com.example.ai.model.Direction?,
        startMs: Long?,
        endMs: Long?
    ): com.example.ai.query.QueryAggregate {
        val scope = getScopeId()
        val row = when {
            entity != null && direction == com.example.ai.model.Direction.EXPENSE ->
                transactionDao.getEntityOutgoingPaise(entity, scope, startMs, endMs)
            entity != null && direction == com.example.ai.model.Direction.TRANSFER ->
                transactionDao.getEntityOutgoingPaise(entity, scope, startMs, endMs)
            entity != null && direction == com.example.ai.model.Direction.DEBIT ->
                transactionDao.getEntityOutgoingPaise(entity, scope, startMs, endMs)
            entity != null && direction == com.example.ai.model.Direction.INCOME ->
                transactionDao.getEntityIncomingPaise(entity, scope, startMs, endMs)
            entity != null && direction == com.example.ai.model.Direction.CREDIT ->
                transactionDao.getEntityIncomingPaise(entity, scope, startMs, endMs)
            entity != null ->
                transactionDao.getEntityNetBalancePaise(entity, scope, startMs, endMs)
            direction == com.example.ai.model.Direction.INCOME ||
            direction == com.example.ai.model.Direction.CREDIT ->
                transactionDao.getScopeDirectionalPaise(categoryId, "IN", scope, startMs, endMs)
            direction == com.example.ai.model.Direction.EXPENSE ||
            direction == com.example.ai.model.Direction.TRANSFER ||
            direction == com.example.ai.model.Direction.DEBIT ->
                transactionDao.getScopeDirectionalPaise(categoryId, "OUT", scope, startMs, endMs)
            else ->
                transactionDao.getScopeNetBalancePaise(categoryId, scope, startMs, endMs)
        }
        return com.example.ai.query.QueryAggregate.fromPaise(
            paise = row?.netAmountPaise ?: 0L,
            count = row?.rowCount ?: 0,
            ids = row?.idList?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }

    suspend fun getDistinctTitles(): List<String> = transactionDao.getDistinctTitles(getScopeId())

    suspend fun getByIds(ids: List<String>): List<Transaction> =
        if (ids.isEmpty()) emptyList() else transactionDao.getByIds(ids, getScopeId())

    suspend fun getIncomeExpenseTotalsForRange(startMs: Long, endMs: Long) =
        transactionDao.getIncomeExpenseTotalsForRange(startMs, endMs, getScopeId())

    suspend fun getAllTransactionsOnce(): List<Transaction> =
        transactionDao.getAllTransactionsOnce(getScopeId())
}
