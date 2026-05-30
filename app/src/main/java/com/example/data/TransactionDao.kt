package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactionsWithCategory(): Flow<List<TransactionWithCategory>>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("SELECT * FROM transactions WHERE account_id = :accountId ORDER BY date DESC")
    fun getTransactionsByAccount(accountId: String): Flow<List<Transaction>>

    @Query("""
    SELECT SUM(CASE WHEN type IN ('INCOME', 'CREDIT') THEN amount ELSE -amount END) 
    FROM transactions 
    WHERE title LIKE '%' || :title || '%'""")
    suspend fun getPartyBalance(title: String): Double?

    @Query("SELECT * FROM transactions WHERE title = :title AND amount = :amount AND type = :type AND date >= :fiveMinutesAgo LIMIT 1")
    suspend fun getDuplicateTransaction(title: String, amount: Double, type: String, fiveMinutesAgo: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: String): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: String)

    @Query("SELECT * FROM transactions WHERE date >= :startMs AND date <= :endMs ORDER BY date DESC")
    suspend fun getByDateRange(startMs: Long, endMs: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE account_id = :accountId ORDER BY date DESC")
    suspend fun getByAccount(accountId: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE category_id = :categoryId ORDER BY date DESC")
    suspend fun getByCategory(categoryId: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE title LIKE '%' || :query || '%' OR (description IS NOT NULL AND description LIKE '%' || :query || '%') OR tags LIKE '%' || :query || '%' ORDER BY date DESC")
    suspend fun search(query: String): List<Transaction>
    @Query("""
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND type = :transactionType 
        AND date >= :sinceTimestamp 
        ORDER BY date DESC 
        LIMIT 1
    """)
    suspend fun findDuplicateAggressive(
        amount: Double,
        transactionType: String,
        sinceTimestamp: Long
    ): Transaction?

    @Query("""
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND type = :transactionType 
        AND LOWER(title) = LOWER(:entity) 
        AND date >= :sinceTimestamp 
        AND date < :untilTimestamp 
        ORDER BY date DESC 
        LIMIT 1
    """)
    suspend fun findDuplicateModerate(
        amount: Double,
        transactionType: String,
        entity: String,
        sinceTimestamp: Long,
        untilTimestamp: Long
    ): Transaction?

    @Query("""
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND type = :transactionType 
        AND LOWER(title) = LOWER(:entity) 
        AND category_id = :categoryId 
        AND date >= :sinceTimestamp 
        AND date < :untilTimestamp 
        ORDER BY date DESC 
        LIMIT 1
    """)
    suspend fun findDuplicateWeak(
        amount: Double,
        transactionType: String,
        entity: String,
        categoryId: String,
        sinceTimestamp: Long,
        untilTimestamp: Long
    ): Transaction?

    /**
     * Polymorphic aggregate query. NULL parameters act as wildcards.
     * Uses LIKE for entity matching to stay consistent with getPartyBalance().
     * Uses type IN ('INCOME', 'CREDIT') for positive-side queries, matching
     * the existing CREDIT legacy value handling.
     */
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
        ORDER BY date DESC
    """)
    suspend fun getQueryAggregateRows(
        entity: String?,
        categoryId: String?,
        direction: String?,
        startMs: Long,
        endMs: Long
    ): List<AmountIdRow>

    /**
     * Net balance for an entity (INCOME/CREDIT minus EXPENSE) within a time range.
     * Mirrors the SQL pattern from getPartyBalance() exactly, with optional time bounds.
     */
    @Query("""
        SELECT 
            SUM(CASE WHEN type IN ('INCOME', 'CREDIT') THEN amount ELSE -amount END) AS netAmount,
            COUNT(*) AS rowCount,
            GROUP_CONCAT(id) AS idList
        FROM transactions
        WHERE title LIKE '%' || :entity || '%'
          AND date >= :startMs
          AND date <= :endMs
    """)
    suspend fun getEntityNetBalanceRow(
        entity: String,
        startMs: Long,
        endMs: Long
    ): EntityBalanceRow?
    @Query("SELECT DISTINCT title FROM transactions WHERE title != '' ORDER BY title")
    suspend fun getDistinctTitles(): List<String>
}

data class AmountIdRow(
    val amount: Double,
    val id: String
)

data class EntityBalanceRow(
    val netAmount: Double?,
    val rowCount: Int,
    val idList: String?
)
