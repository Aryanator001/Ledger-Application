package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.analytics.AnalyticsCache
import com.example.analytics.AnalyticsCacheDao
import com.ledger.query.exec.QueryPlanCacheDao
import com.ledger.query.exec.QueryPlanCacheEntity

@Database(entities = [Transaction::class, Category::class, Account::class, Budget::class, AnalyticsCache::class, UserProfile::class, LedgerScope::class, QueryPlanCacheEntity::class], version = 12, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun analyticsCacheDao(): AnalyticsCacheDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun ledgerScopeDao(): LedgerScopeDao
    abstract fun queryPlanCacheDao(): QueryPlanCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN to_account_id TEXT")
                database.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE transactions ADD COLUMN recurring_id TEXT")
                database.execSQL("ALTER TABLE accounts ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
                database.execSQL("ALTER TABLE accounts ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE accounts ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // ALTER transactions
                database.execSQL("ALTER TABLE transactions ADD COLUMN to_account_id TEXT")
                database.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE transactions ADD COLUMN recurring_id TEXT")

                // ALTER accounts
                database.execSQL("ALTER TABLE accounts ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
                database.execSQL("ALTER TABLE accounts ADD COLUMN is_archived INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE accounts ADD COLUMN is_hidden INTEGER NOT NULL DEFAULT 0")

                // Drop and recreate categories to match new schema
                database.execSQL("DROP TABLE IF EXISTS `categories`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT NOT NULL, `color_hex` TEXT NOT NULL, `type` TEXT NOT NULL, `parent_id` TEXT, `is_system` INTEGER NOT NULL, `order` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_type` ON `categories` (`type`)")

                // CREATE budgets
                database.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `category_id` TEXT NOT NULL, `limit_amount` INTEGER NOT NULL, `period` TEXT NOT NULL, `rollover` INTEGER NOT NULL, `alert_at` REAL NOT NULL, `start_date` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_category_id` ON `budgets` (`category_id`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try {
                    database.execSQL("ALTER TABLE accounts RENAME COLUMN balance TO starting_balance")
                } catch (e: Exception) {
                    // Falls back or handles if already renamed
                }
                database.execSQL("ALTER TABLE accounts ADD COLUMN exclude_from_total INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE accounts ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_8 = object : Migration(6, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Stage 2 introduced the analytics_cache table at version 8.
                // This migration creates it cleanly without dropping user data.
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `analytics_cache` (
                        `key` TEXT NOT NULL PRIMARY KEY,
                        `total_amount` REAL NOT NULL,
                        `count` INTEGER NOT NULL,
                        `transaction_ids` TEXT NOT NULL,
                        `bucket_start_ms` INTEGER NOT NULL,
                        `bucket_end_ms` INTEGER NOT NULL,
                        `last_updated` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create user_profiles and ledger_scopes tables
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_profiles` (
                        `uid` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `email` TEXT NOT NULL,
                        `photoUrl` TEXT,
                        `businessName` TEXT,
                        `phoneNumber` TEXT,
                        `lastUpdated` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        PRIMARY KEY(`uid`)
                    )
                """.trimIndent())

                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ledger_scopes` (
                        `id` TEXT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`profileId`) REFERENCES `user_profiles`(`uid`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                // Alter Accounts
                database.execSQL("ALTER TABLE accounts ADD COLUMN ledger_scope_id TEXT")
                database.execSQL("ALTER TABLE accounts ADD COLUMN last_updated INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE accounts ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")

                // Alter Transactions
                database.execSQL("ALTER TABLE transactions ADD COLUMN ledger_scope_id TEXT")
                database.execSQL("ALTER TABLE transactions ADD COLUMN last_updated INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE transactions ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")

                // Alter Budgets
                database.execSQL("ALTER TABLE budgets ADD COLUMN ledger_scope_id TEXT")
                database.execSQL("ALTER TABLE budgets ADD COLUMN last_updated INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE budgets ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE user_profiles ADD COLUMN businessType TEXT")
                database.execSQL("ALTER TABLE user_profiles ADD COLUMN address TEXT")
                database.execSQL("ALTER TABLE user_profiles ADD COLUMN website TEXT")
                database.execSQL("ALTER TABLE user_profiles ADD COLUMN gstNumber TEXT")
                database.execSQL("ALTER TABLE user_profiles ADD COLUMN panNumber TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE transactions ADD COLUMN payment_method TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `query_plan_cache` (
                        `template` TEXT NOT NULL PRIMARY KEY,
                        `direction` TEXT NOT NULL,
                        `metric` TEXT NOT NULL,
                        `group_by` TEXT NOT NULL,
                        `uses_entity` INTEGER NOT NULL,
                        `uses_category` INTEGER NOT NULL,
                        `limit_val` INTEGER,
                        `hit_count` INTEGER NOT NULL DEFAULT 0,
                        `created_at` INTEGER NOT NULL,
                        `last_used_at` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_qpc_last_used` ON `query_plan_cache` (`last_used_at`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_txn_date` ON `transactions` (`date`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_txn_title` ON `transactions` (`title`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ledger_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
