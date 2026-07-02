package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi

class BudgetRepository(
    private val budgetDao: BudgetDao,
    private val userPreferences: UserPreferences
) {
    private suspend fun getScopeId(): String = userPreferences.activeLedgerScopeId.first() ?: ""

    @OptIn(ExperimentalCoroutinesApi::class)
    val allBudgets: Flow<List<Budget>> = userPreferences.activeLedgerScopeId.flatMapLatest { scopeId ->
        if (scopeId == null) flowOf(emptyList()) else budgetDao.getAllBudgets(scopeId)
    }

    suspend fun getBudgetById(id: String): Budget? {
        return budgetDao.getBudgetById(id, getScopeId())
    }

    suspend fun getBudgetByCategory(categoryId: String): Budget? {
        return budgetDao.getBudgetByCategory(categoryId, getScopeId())
    }

    suspend fun insertBudget(budget: Budget) {
        val finalBudget = if (budget.ledgerScopeId == null) budget.copy(ledgerScopeId = getScopeId()) else budget
        budgetDao.insertBudget(finalBudget)
    }

    suspend fun updateBudget(budget: Budget) {
        budgetDao.updateBudget(budget)
    }

    suspend fun deleteBudgetById(id: String) {
        budgetDao.deleteBudgetById(id, getScopeId())
    }
}
