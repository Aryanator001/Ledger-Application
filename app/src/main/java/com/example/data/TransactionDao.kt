package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT SUM(CASE WHEN transaction_type = 'CREDIT' THEN amount ELSE -amount END) FROM transactions WHERE party_name LIKE '%' || :partyName || '%'")
    suspend fun getPartyBalance(partyName: String): Double?

    @Query("SELECT * FROM transactions WHERE party_name = :name AND amount = :amount AND transaction_type = :type AND timestamp >= :fiveMinutesAgo LIMIT 1")
    suspend fun getDuplicateTransaction(name: String, amount: Double, type: String, fiveMinutesAgo: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)
}
