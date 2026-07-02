package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE ledger_scope_id = :ledgerScopeId AND is_deleted = 0")
    fun getAllBudgets(ledgerScopeId: String): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE id = :id AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0")
    suspend fun getBudgetById(id: String, ledgerScopeId: String): Budget?

    @Query("SELECT * FROM budgets WHERE category_id = :categoryId AND ledger_scope_id = :ledgerScopeId AND is_deleted = 0 LIMIT 1")
    suspend fun getBudgetByCategory(categoryId: String, ledgerScopeId: String): Budget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget)

    @Update
    suspend fun updateBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE id = :id AND ledger_scope_id = :ledgerScopeId")
    suspend fun deleteBudgetById(id: String, ledgerScopeId: String)

    @Query("UPDATE budgets SET ledger_scope_id = :ledgerScopeId WHERE ledger_scope_id IS NULL")
    suspend fun bindUnassignedBudgets(ledgerScopeId: String): Int
}
