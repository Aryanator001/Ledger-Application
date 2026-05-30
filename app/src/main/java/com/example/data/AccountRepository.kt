package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AccountRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao
) {
    val allAccounts: Flow<List<Account>> = accountDao.getAllAccounts()

    suspend fun getAccountById(id: String): Account? {
        return accountDao.getAccountById(id)
    }

    suspend fun getAccountCount(): Int {
        return accountDao.getAccountCount()
    }

    suspend fun insertAccount(account: Account) {
        accountDao.insertAccount(account)
    }

    suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account)
    }

    suspend fun deleteAccountById(id: String) {
        accountDao.deleteAccountById(id)
    }

    // Derived balances single source of truth reactively
    fun getAccountWithBalanceListFlow(): Flow<List<AccountWithBalance>> {
        return combine(allAccounts, transactionDao.getAllTransactions()) { accounts, transactions ->
            accounts.map { account ->
                AccountWithBalance(account, account.calculateBalance(transactions))
            }
        }
    }

    fun getCurrentBalance(accountId: String): Flow<Double> {
        return combine(allAccounts, transactionDao.getAllTransactions()) { accounts, transactions ->
            val account = accounts.find { it.id == accountId }
            account?.calculateBalance(transactions) ?: 0.0
        }
    }

    fun getNetWorthFlow(): Flow<Double> {
        return combine(allAccounts, transactionDao.getAllTransactions()) { accounts, transactions ->
            accounts.filter { !it.isHidden && !it.isArchived && !it.excludeFromTotal }
                .sumOf { it.calculateBalance(transactions) }
        }
    }
}

data class AccountWithBalance(
    val account: Account,
    val balance: Double
)
