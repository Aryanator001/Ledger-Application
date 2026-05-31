package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.analytics.AnalyticsCache
import com.example.analytics.AnalyticsCacheDao

@Database(entities = [Transaction::class, Category::class, Account::class, Budget::class, AnalyticsCache::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun analyticsCacheDao(): AnalyticsCacheDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ledger_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_8)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
