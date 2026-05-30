package com.example.data

import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val budgetDao: BudgetDao) {
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets()

    suspend fun getBudgetById(id: String): Budget? {
        return budgetDao.getBudgetById(id)
    }

    suspend fun getBudgetByCategory(categoryId: String): Budget? {
        return budgetDao.getBudgetByCategory(categoryId)
    }

    suspend fun insertBudget(budget: Budget) {
        budgetDao.insertBudget(budget)
    }

    suspend fun updateBudget(budget: Budget) {
        budgetDao.updateBudget(budget)
    }

    suspend fun deleteBudgetById(id: String) {
        budgetDao.deleteBudgetById(id)
    }
}
