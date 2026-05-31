package com.example.analytics

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.Category
import com.example.data.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages one-time data migrations for the categories table.
 *
 * Pattern (mirrors CacheRecoveryManager):
 *   A version constant defines the required migration state. A
 *   SharedPreferences flag tracks the last applied version. On launch,
 *   if applied < required, the migration runs and bumps the flag.
 *
 * Why this exists:
 *   The AI extraction layer (CandidateResolver) emits category IDs
 *   "general", "food", "rent", "shopping" that did not exist in the
 *   original seed. Transactions saved with those IDs became orphan FKs.
 *   This migration adds those 4 categories to existing installs so
 *   the orphan FKs heal and the UI renders proper labels/icons.
 *
 *   The seeder in LedgerApplication is also updated to include these
 *   4 categories for fresh installs, but the seeder is gated by a
 *   "categoriesSeeded" flag that's already true on existing installs.
 *   This migrator covers that gap.
 *
 * Current migration state:
 *   Version 1: Add the 4 AI-aligned categories via INSERT OR IGNORE,
 *              which is safe whether or not they already exist.
 */
class CategoryMigrationManager(
    private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        private const val PREFS_NAME = "category_migration_prefs"
        private const val KEY_APPLIED_VERSION = "applied_migration_version"
        private const val REQUIRED_VERSION = 1
        private const val TAG = "CategoryMigration"
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun runIfNeeded() {
        val applied = prefs.getInt(KEY_APPLIED_VERSION, 0)
        if (applied >= REQUIRED_VERSION) {
            Log.d(TAG, "Category migrations up to date (applied=$applied, required=$REQUIRED_VERSION)")
            return
        }

        Log.i(TAG, "Running category migration: $applied → $REQUIRED_VERSION")
        scope.launch {
            try {
                runMigrationSteps(fromVersion = applied)
                prefs.edit().putInt(KEY_APPLIED_VERSION, REQUIRED_VERSION).apply()
                Log.i(TAG, "Category migration complete, flag bumped to $REQUIRED_VERSION")
            } catch (e: Exception) {
                Log.e(TAG, "Category migration failed — flag NOT bumped, will retry next launch", e)
            }
        }
    }

    private suspend fun runMigrationSteps(fromVersion: Int) {
        // Step 0 → 1: Insert the 4 AI-aligned categories.
        if (fromVersion < 1) {
            val aiCategories = listOf(
                Category(
                    id = "general",
                    name = "General",
                    icon = "category",
                    colorHex = "#9E9E9E",
                    type = "EXPENSE",
                    isSystem = true,
                    order = 200
                ),
                Category(
                    id = "food",
                    name = "Food",
                    icon = "restaurant",
                    colorHex = "#FF5722",
                    type = "EXPENSE",
                    isSystem = true,
                    order = 201
                ),
                Category(
                    id = "rent",
                    name = "Rent",
                    icon = "home",
                    colorHex = "#FF9800",
                    type = "EXPENSE",
                    isSystem = true,
                    order = 202
                ),
                Category(
                    id = "shopping",
                    name = "Shopping",
                    icon = "shopping_bag",
                    colorHex = "#E91E63",
                    type = "EXPENSE",
                    isSystem = true,
                    order = 203
                )
            )

            for (category in aiCategories) {
                try {
                    transactionRepository.insertCategory(category)
                    Log.d(TAG, "Inserted/refreshed category: ${category.id}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to insert ${category.id}, continuing", e)
                }
            }
            Log.i(TAG, "Step 0→1: AI-aligned categories ensured")
        }
    }
}
