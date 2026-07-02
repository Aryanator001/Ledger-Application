package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi

class AccountRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val userPreferences: UserPreferences
) {
    private suspend fun getScopeId(): String = userPreferences.activeLedgerScopeId.first() ?: ""

    @OptIn(ExperimentalCoroutinesApi::class)
    val allAccounts: Flow<List<Account>> = userPreferences.activeLedgerScopeId.flatMapLatest { scopeId ->
        if (scopeId == null) flowOf(emptyList()) else accountDao.getAllAccounts(scopeId)
    }

    suspend fun getAccountById(id: String): Account? {
        return accountDao.getAccountById(id, getScopeId())
    }

    suspend fun getAccountCount(): Int {
        return accountDao.getAccountCount(getScopeId())
    }

    suspend fun insertAccount(account: Account) {
        val finalAccount = if (account.ledgerScopeId == null) account.copy(ledgerScopeId = getScopeId()) else account
        accountDao.insertAccount(finalAccount)
    }

    suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account)
    }

    suspend fun deleteAccountById(id: String) {
        accountDao.deleteAccountById(id, getScopeId())
    }

    suspend fun deleteAccountAndTransactions(accountId: String) {
        val scopeId = getScopeId()
        val transactions = transactionDao.getByAccount(accountId, scopeId)
        transactions.forEach { tx ->
            transactionDao.deleteTransactionById(tx.id, scopeId)
        }
        accountDao.deleteAccountById(accountId, scopeId)
    }

    // Derived balances single source of truth reactively
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAccountWithBalanceListFlow(): Flow<List<AccountWithBalance>> {
        val transactionsFlow = userPreferences.activeLedgerScopeId.flatMapLatest { scopeId ->
            if (scopeId == null) flowOf(emptyList()) else transactionDao.getAllTransactions(scopeId)
        }
        return combine(allAccounts, transactionsFlow) { accounts, transactions ->
            accounts.map { account ->
                AccountWithBalance(account, account.calculateBalance(transactions))
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCurrentBalance(accountId: String): Flow<Double> {
        val transactionsFlow = userPreferences.activeLedgerScopeId.flatMapLatest { scopeId ->
            if (scopeId == null) flowOf(emptyList()) else transactionDao.getAllTransactions(scopeId)
        }
        return combine(allAccounts, transactionsFlow) { accounts, transactions ->
            val account = accounts.find { it.id == accountId }
            account?.calculateBalance(transactions) ?: 0.0
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getNetWorthFlow(): Flow<Double> {
        val transactionsFlow = userPreferences.activeLedgerScopeId.flatMapLatest { scopeId ->
            if (scopeId == null) flowOf(emptyList()) else transactionDao.getAllTransactions(scopeId)
        }
        return combine(allAccounts, transactionsFlow) { accounts, transactions ->
            accounts.filter { !it.isHidden && !it.isArchived && !it.excludeFromTotal }
                .sumOf { it.calculateBalance(transactions) }
        }
    }
}

data class AccountWithBalance(
    val account: Account,
    val balance: Double
)
