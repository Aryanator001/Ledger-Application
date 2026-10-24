package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Recognized transaction-only v2 layout (repository commit aa882ca). Never drop the source data. */
object LegacyV2Migration : Migration(2, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val expected = mapOf("id" to "INTEGER", "party_name" to "TEXT", "amount" to "REAL",
            "transaction_type" to "TEXT", "particular" to "TEXT", "raw_transcript" to "TEXT", "timestamp" to "INTEGER")
        val columns = db.query("PRAGMA table_info(transactions)").use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    cursor.getString(cursor.getColumnIndexOrThrow("type")).uppercase())
            }
        }
        check(columns == expected) { "Unrecognized version-2 transaction schema; original data retained" }
        db.query("SELECT name FROM sqlite_master WHERE type IN ('table', 'view', 'trigger')").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                check(name in setOf("transactions", "room_master_table", "android_metadata") || name.startsWith("sqlite_")) {
                    "Unrecognized version-2 objects; original data retained"
                }
            }
        }
        db.query("PRAGMA foreign_key_list(transactions)").use { cursor ->
            check(!cursor.moveToFirst()) { "Unrecognized version-2 references; original data retained" }
        }
        db.execSQL("ALTER TABLE transactions RENAME TO transactions_v2_backup")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `amount` REAL NOT NULL,
            `category_id` TEXT NOT NULL,
            `account_id` TEXT NOT NULL DEFAULT 'default_cash',
            `type` TEXT NOT NULL,
            `date` INTEGER NOT NULL,
            `description` TEXT,
            `is_recurring` INTEGER NOT NULL,
            `ai_categorized` INTEGER NOT NULL,
            `created_at` INTEGER NOT NULL,
            `updated_at` INTEGER NOT NULL,
            `to_account_id` TEXT,
            `tags` TEXT NOT NULL,
            `recurring_id` TEXT,
            `is_archived` INTEGER NOT NULL,
            `raw_transcript` TEXT,
            PRIMARY KEY(`id`),
            FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
            FOREIGN KEY(`account_id`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET DEFAULT )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `icon` TEXT NOT NULL,
            `color_hex` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `parent_id` TEXT,
            `is_system` INTEGER NOT NULL,
            `order` INTEGER NOT NULL,
            PRIMARY KEY(`id`))
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_type` ON `categories` (`type`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL,
            `name` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `color` TEXT NOT NULL,
            `icon` TEXT,
            `starting_balance` REAL NOT NULL,
            `created_at` INTEGER NOT NULL,
            `currency` TEXT NOT NULL,
            `is_archived` INTEGER NOT NULL,
            `is_hidden` INTEGER NOT NULL,
            `exclude_from_total` INTEGER NOT NULL,
            `sort_order` INTEGER NOT NULL,
            PRIMARY KEY(`id`))
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL,
            `category_id` TEXT NOT NULL,
            `limit_amount` INTEGER NOT NULL,
            `period` TEXT NOT NULL,
            `rollover` INTEGER NOT NULL,
            `alert_at` REAL NOT NULL,
            `start_date` INTEGER NOT NULL,
            PRIMARY KEY(`id`))
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budgets_category_id` ON `budgets` (`category_id`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `analytics_cache` (`key` TEXT NOT NULL,
            `total_amount` REAL NOT NULL,
            `count` INTEGER NOT NULL,
            `transaction_ids` TEXT NOT NULL,
            `bucket_start_ms` INTEGER NOT NULL,
            `bucket_end_ms` INTEGER NOT NULL,
            `last_updated` INTEGER NOT NULL,
            PRIMARY KEY(`key`))
        """.trimIndent())
        db.execSQL("INSERT INTO categories VALUES ('general', 'General', 'category', '#607D8B', 'BOTH', NULL, 1, 0)")
        db.execSQL("""
            INSERT INTO accounts (id, name, type, color, icon, starting_balance, created_at,
                currency, is_archived, is_hidden, exclude_from_total, sort_order)
            VALUES ('default_cash', 'Cash', 'CASH', '#6FAF8F', NULL, 0, 0, 'INR', 0, 0, 0, 0)
        """.trimIndent())
        // In v2 CREDIT/DEBIT meant received/paid cash, not the newer deferred credit/debit feature.
        db.execSQL("""
            INSERT INTO transactions (id, title, amount, category_id, account_id, type, date,
                description, is_recurring, ai_categorized, created_at, updated_at, to_account_id,
                tags, recurring_id, is_archived, raw_transcript)
            SELECT 'legacy-v2:' || id, party_name, amount, 'general', 'default_cash',
                CASE transaction_type WHEN 'CREDIT' THEN 'INCOME' WHEN 'DEBIT' THEN 'EXPENSE' ELSE transaction_type END,
                timestamp, particular, 0, 0, timestamp, timestamp, NULL, '', NULL, 0, raw_transcript
            FROM transactions_v2_backup
        """.trimIndent())
    }
}
