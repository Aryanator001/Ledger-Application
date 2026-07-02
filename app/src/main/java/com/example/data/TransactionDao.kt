package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY date DESC")
    fun getAllTransactions(ledgerScopeId: String): Flow<List<Transaction>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY date DESC")
    fun getAllTransactionsWithCategory(ledgerScopeId: String): Flow<List<TransactionWithCategory>>

    @Query("SELECT COUNT(*) FROM transactions WHERE ledger_scope_id = :ledgerScopeId AND is_deleted = 0")
    suspend fun getTransactionCount(ledgerScopeId: String): Int

    @Query("SELECT * FROM transactions WHERE account_id = :accountId AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY date DESC")
    fun getTransactionsByAccount(accountId: String, ledgerScopeId: String): Flow<List<Transaction>>

    @Query("""
    SELECT SUM(CASE WHEN type IN ('INCOME', 'CREDIT') THEN amount ELSE -amount END) 
    FROM transactions 
    WHERE title LIKE '%' || :title || '%' AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0""")
    suspend fun getPartyBalance(title: String, ledgerScopeId: String): Double?

    @Query("SELECT * FROM transactions WHERE title = :title AND amount = :amount AND type = :type AND date >= :fiveMinutesAgo AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0 LIMIT 1")
    suspend fun getDuplicateTransaction(title: String, amount: Double, type: String, fiveMinutesAgo: Long, ledgerScopeId: String): Transaction?

    @Query("SELECT * FROM transactions WHERE id = :id AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0 LIMIT 1")
    suspend fun getTransactionById(id: String, ledgerScopeId: String): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id AND ledger_scope_id = :ledgerScopeId")
    suspend fun deleteTransactionById(id: String, ledgerScopeId: String)

    @Query("SELECT * FROM transactions WHERE date >= :startMs AND date <= :endMs AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY date DESC")
    suspend fun getByDateRange(startMs: Long, endMs: Long, ledgerScopeId: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE account_id = :accountId AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY date DESC")
    suspend fun getByAccount(accountId: String, ledgerScopeId: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE category_id = :categoryId AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY date DESC")
    suspend fun getByCategory(categoryId: String, ledgerScopeId: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE (title LIKE '%' || :query || '%' OR (description IS NOT NULL AND description LIKE '%' || :query || '%') OR tags LIKE '%' || :query || '%') AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY date DESC")
    suspend fun search(query: String, ledgerScopeId: String): List<Transaction>

    @Query("""
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND type = :transactionType 
        AND ledger_scope_id = :ledgerScopeId
        AND is_deleted = 0
        AND date >= :sinceTimestamp 
        ORDER BY date DESC 
        LIMIT 1
    """)
    suspend fun findDuplicateAggressive(
        amount: Double,
        transactionType: String,
        ledgerScopeId: String,
        sinceTimestamp: Long
    ): Transaction?

    @Query("UPDATE transactions SET ledger_scope_id = :ledgerScopeId WHERE ledger_scope_id IS NULL")
    suspend fun bindUnassignedTransactions(ledgerScopeId: String): Int

    @Query("""
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND type = :transactionType 
        AND LOWER(title) = LOWER(:entity) 
        AND date >= :sinceTimestamp 
        AND date < :untilTimestamp 
        AND ledger_scope_id = :ledgerScopeId
        AND is_deleted = 0
        ORDER BY date DESC 
        LIMIT 1
    """)
    suspend fun findDuplicateModerate(
        amount: Double,
        transactionType: String,
        entity: String,
        sinceTimestamp: Long,
        untilTimestamp: Long,
        ledgerScopeId: String
    ): Transaction?

    @Query("""
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND type = :transactionType 
        AND LOWER(title) = LOWER(:entity) 
        AND category_id = :categoryId 
        AND date >= :sinceTimestamp 
        AND date < :untilTimestamp 
        AND ledger_scope_id = :ledgerScopeId
        AND is_deleted = 0
        ORDER BY date DESC 
        LIMIT 1
    """)
    suspend fun findDuplicateWeak(
        amount: Double,
        transactionType: String,
        entity: String,
        categoryId: String,
        sinceTimestamp: Long,
        untilTimestamp: Long,
        ledgerScopeId: String
    ): Transaction?

    @Query("""
        SELECT amount, id FROM transactions
        WHERE (:entity IS NULL OR title LIKE '%' || :entity || '%')
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (
                :direction IS NULL
                OR (:direction = 'INCOME' AND type IN ('INCOME', 'CREDIT'))
                OR (:direction = 'EXPENSE' AND type = 'EXPENSE')
              )
          AND date >= :startMs
          AND date <= :endMs
          AND ledger_scope_id = :ledgerScopeId
          AND is_deleted = 0
        ORDER BY date DESC
    """)
    suspend fun getQueryAggregateRows(
        entity: String?,
        categoryId: String?,
        direction: String?,
        startMs: Long,
        endMs: Long,
        ledgerScopeId: String
    ): List<AmountIdRow>

    @Query("""
        SELECT 
            SUM(CASE WHEN type IN ('INCOME', 'CREDIT') THEN amount ELSE -amount END) AS netAmount,
            COUNT(*) AS rowCount,
            GROUP_CONCAT(id) AS idList
        FROM transactions
        WHERE title LIKE '%' || :entity || '%'
          AND date >= :startMs
          AND date <= :endMs
          AND ledger_scope_id = :ledgerScopeId
          AND is_deleted = 0
    """)
    suspend fun getEntityNetBalanceRow(
        entity: String,
        startMs: Long,
        endMs: Long,
        ledgerScopeId: String
    ): EntityBalanceRow?
    @Query("SELECT DISTINCT title FROM transactions WHERE title != '' AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY title")
    suspend fun getDistinctTitles(ledgerScopeId: String): List<String>

    @Query("""
        SELECT
            SUM(CASE WHEN type IN ('INCOME', 'CREDIT') THEN amount ELSE 0 END) AS incomeTotal,
            SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS expenseTotal
        FROM transactions
        WHERE date >= :startMs AND date <= :endMs
        AND ledger_scope_id = :ledgerScopeId
        AND is_deleted = 0
    """)
    suspend fun getIncomeExpenseTotalsForRange(startMs: Long, endMs: Long, ledgerScopeId: String): IncomeExpenseTotalsRow?

    @Query("SELECT * FROM transactions WHERE ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY date DESC")
    suspend fun getAllTransactionsOnce(ledgerScopeId: String): List<Transaction>
}

data class IncomeExpenseTotalsRow(
    val incomeTotal: Double?,
    val expenseTotal: Double?
)

data class AmountIdRow(
    val amount: Double,
    val id: String
)

data class EntityBalanceRow(
    val netAmount: Double?,
    val rowCount: Int,
    val idList: String?
)
