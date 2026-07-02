package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE ledger_scope_id = :ledgerScopeId AND is_deleted = 0 ORDER BY sort_order ASC, name ASC")
    fun getAllAccounts(ledgerScopeId: String): Flow<List<Account>>

    @Query("SELECT * FROM accounts WHERE id = :id AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0")
    suspend fun getAccountById(id: String, ledgerScopeId: String): Account?

    @Query("SELECT COUNT(*) FROM accounts WHERE ledger_scope_id = :ledgerScopeId AND is_deleted = 0")
    suspend fun getAccountCount(ledgerScopeId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: Account)

    @Update
    suspend fun updateAccount(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id AND ledger_scope_id = :ledgerScopeId")
    suspend fun deleteAccountById(id: String, ledgerScopeId: String)

    @Query("UPDATE accounts SET ledger_scope_id = :ledgerScopeId WHERE ledger_scope_id IS NULL")
    suspend fun bindUnassignedAccounts(ledgerScopeId: String): Int
}
