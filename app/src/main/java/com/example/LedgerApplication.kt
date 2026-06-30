package com.example

import android.app.Application
import com.example.analytics.AnalyticsCacheUpdater
import com.example.analytics.AnalyticsKeyResolver
import com.example.analytics.AnalyticsRepository
import com.example.analytics.CacheRecoveryManager
import com.example.analytics.CategoryMigrationManager
import com.example.ai.query.TimeTokenResolver
import com.example.data.AppDatabase
import com.example.data.TransactionRepository
import com.example.data.UserPreferences
import com.example.data.SecurityStore
import com.example.data.Category
import com.example.data.security.AppLockManager
import com.example.data.security.SecurityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.data.dataStore

class LedgerApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getDatabase(this) }
    val userPreferences by lazy { UserPreferences(this) }
    val securityStore by lazy { SecurityStore(this) }
    val securityRepository by lazy { SecurityRepository(this.dataStore) }

    val analyticsCacheDao by lazy { database.analyticsCacheDao() }
    val timeTokenResolver by lazy { TimeTokenResolver() }
    val analyticsKeyResolver by lazy { AnalyticsKeyResolver(timeTokenResolver) }
    val analyticsCacheUpdater by lazy { AnalyticsCacheUpdater(analyticsCacheDao, analyticsKeyResolver) }
    val analyticsRepository by lazy { AnalyticsRepository(analyticsCacheDao, analyticsKeyResolver, timeTokenResolver) }
    val cacheRecoveryManager by lazy { CacheRecoveryManager(this, analyticsCacheDao) }
    val categoryMigrationManager by lazy {
        CategoryMigrationManager(this, repository)
    }

    val repository by lazy { 
        TransactionRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            accountDao = database.accountDao(),
            analyticsCacheUpdater = analyticsCacheUpdater
        ) 
    }
    val accountRepository by lazy { com.example.data.AccountRepository(database.accountDao(), database.transactionDao()) }
    val budgetRepository by lazy { com.example.data.BudgetRepository(database.budgetDao()) }
    val analyticsService by lazy { com.example.data.AnalyticsService(repository, accountRepository, budgetRepository) }

    override fun onCreate() {
        super.onCreate()
        seedDefaultCategoriesIfNeeded()
        AppLockManager.init(this, securityRepository)
        cacheRecoveryManager.runIfNeeded()
        categoryMigrationManager.runIfNeeded()
    }

    private fun seedDefaultCategoriesIfNeeded() {
        applicationScope.launch {
            try {
                val isSeeded = userPreferences.categoriesSeeded.first()
                if (!isSeeded) {
                    val defaultCategories = listOf(
                        Category(id = "exp_food", name = "Food & Dining", icon = "restaurant", colorHex = "#FF5722", type = "EXPENSE", isSystem = true, order = 0),
                        Category(id = "exp_trans", name = "Transport", icon = "directions_car", colorHex = "#03A9F4", type = "EXPENSE", isSystem = true, order = 1),
                        Category(id = "exp_shop", name = "Shopping", icon = "shopping_bag", colorHex = "#E91E63", type = "EXPENSE", isSystem = true, order = 2),
                        Category(id = "exp_bills", name = "Bills & Utilities", icon = "receipt_long", colorHex = "#FF9800", type = "EXPENSE", isSystem = true, order = 3),
                        Category(id = "exp_ent", name = "Entertainment", icon = "movie", colorHex = "#9C27B0", type = "EXPENSE", isSystem = true, order = 4),
                        Category(id = "exp_health", name = "Health", icon = "medical_services", colorHex = "#4CAF50", type = "EXPENSE", isSystem = true, order = 5),
                        Category(id = "exp_edu", name = "Education", icon = "school", colorHex = "#3F51B5", type = "EXPENSE", isSystem = true, order = 6),
                        Category(id = "exp_personal", name = "Personal Care", icon = "face", colorHex = "#00BCD4", type = "EXPENSE", isSystem = true, order = 7),
                        Category(id = "exp_travel", name = "Travel", icon = "flight", colorHex = "#009688", type = "EXPENSE", isSystem = true, order = 8),
                        Category(id = "exp_other", name = "Other Expense", icon = "more_horiz", colorHex = "#607D8B", type = "EXPENSE", isSystem = true, order = 9),
                        
                        Category(id = "inc_salary", name = "Salary", icon = "payments", colorHex = "#4CAF50", type = "INCOME", isSystem = true, order = 10),
                        Category(id = "inc_freelance", name = "Freelance", icon = "work", colorHex = "#8BC34A", type = "INCOME", isSystem = true, order = 11),
                        Category(id = "inc_business", name = "Business", icon = "store", colorHex = "#FFEB3B", type = "INCOME", isSystem = true, order = 12),
                        Category(id = "inc_invest", name = "Investment", icon = "trending_up", colorHex = "#2196F3", type = "INCOME", isSystem = true, order = 13),
                        Category(id = "inc_gift", name = "Gift", icon = "card_giftcard", colorHex = "#9C27B0", type = "INCOME", isSystem = true, order = 14),
                        Category(id = "inc_other", name = "Other Income", icon = "more_horiz", colorHex = "#607D8B", type = "INCOME", isSystem = true, order = 15),
                        
                        // AI-aligned system categories (Phase 6+ extraction layer targets these IDs)
                        Category(id = "general", name = "General", icon = "category", colorHex = "#9E9E9E", type = "EXPENSE", isSystem = true, order = 200),
                        Category(id = "food", name = "Food", icon = "restaurant", colorHex = "#FF5722", type = "EXPENSE", isSystem = true, order = 201),
                        Category(id = "rent", name = "Rent", icon = "home", colorHex = "#FF9800", type = "EXPENSE", isSystem = true, order = 202),
                        Category(id = "shopping", name = "Shopping", icon = "shopping_bag", colorHex = "#E91E63", type = "EXPENSE", isSystem = true, order = 203)
                    )
                    
                    for (category in defaultCategories) {
                        repository.insertCategory(category)
                    }
                    userPreferences.setCategoriesSeeded(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
