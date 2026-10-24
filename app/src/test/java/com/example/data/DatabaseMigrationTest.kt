package com.example.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DatabaseMigrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun fixture(version: Int, create: (SupportSQLiteDatabase) -> Unit): Pair<String, SupportSQLiteOpenHelper> {
        val name = "migration-${UUID.randomUUID()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onConfigure(db: SupportSQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
                override fun onCreate(db: SupportSQLiteDatabase) = create(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture only")
            }).build())
        helper.writableDatabase
        return name to helper
    }

    @Test fun unknownVersionFailsWithoutErasingItsData() {
        val (name, helper) = fixture(2) {
            it.execSQL("CREATE TABLE retained_data(id TEXT PRIMARY KEY, value TEXT)")
            it.execSQL("INSERT INTO retained_data VALUES ('original', 'keep me')")
        }
        helper.close()
        val database = AppDatabase.builder(context, name).allowMainThreadQueries().build()
        try { assertTrue(runCatching { database.openHelper.writableDatabase }.isFailure) }
        finally { database.close() }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(2, it.version)
            it.rawQuery("SELECT value FROM retained_data WHERE id = 'original'", null).use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("keep me", cursor.getString(0))
            }
        }
    }

    @Test fun recognizedVersion2KeepsOriginalTableAndMigratesCashSemantics() {
        val (name, helper) = fixture(2) {
            it.execSQL("""CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                party_name TEXT NOT NULL, amount REAL NOT NULL, transaction_type TEXT NOT NULL,
                particular TEXT, raw_transcript TEXT, timestamp INTEGER NOT NULL)""")
            it.execSQL("INSERT INTO transactions VALUES (1, 'Ramesh', 12.5, 'DEBIT', 'Lunch', 'paid lunch', 123456)")
            it.execSQL("INSERT INTO transactions VALUES (2, 'Salary', 1000, 'CREDIT', NULL, NULL, 654321)")
        }
        helper.close()
        val database = AppDatabase.builder(context, name).allowMainThreadQueries().build()
        try {
            val db = database.openHelper.writableDatabase
            db.query("SELECT title, amount, type, date, description, raw_transcript FROM transactions WHERE id = 'legacy-v2:1'").use {
                assertTrue(it.moveToFirst()); assertEquals("Ramesh", it.getString(0))
                assertEquals(12.5, it.getDouble(1), 0.0); assertEquals("EXPENSE", it.getString(2))
                assertEquals(123456L, it.getLong(3)); assertEquals("Lunch", it.getString(4)); assertEquals("paid lunch", it.getString(5))
            }
            db.query("SELECT type, amount FROM transactions WHERE id = 'legacy-v2:2'").use {
                assertTrue(it.moveToFirst()); assertEquals("INCOME", it.getString(0)); assertEquals(1000.0, it.getDouble(1), 0.0)
            }
            db.query("SELECT transaction_type, amount FROM transactions_v2_backup WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals("DEBIT", it.getString(0)); assertEquals(12.5, it.getDouble(1), 0.0)
            }
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { database.close() }
    }

    @Test fun categoryUpgradeRetainsCategoriesAndReferencingTransactions() {
        val (_, helper) = fixture(4) {
            it.execSQL("CREATE TABLE categories(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, icon TEXT NOT NULL, color_hex TEXT NOT NULL, type TEXT NOT NULL)")
            it.execSQL("CREATE TABLE transactions(id TEXT PRIMARY KEY, category_id TEXT REFERENCES categories(id) ON DELETE CASCADE)")
            it.execSQL("CREATE TABLE accounts(id TEXT PRIMARY KEY)")
            it.execSQL("INSERT INTO categories VALUES ('custom', 'My category', 'icon', '#123456', 'EXPENSE')")
            it.execSQL("INSERT INTO transactions VALUES ('original', 'custom')")
        }
        helper.use {
            val db = it.writableDatabase
            AppDatabase.MIGRATION_4_5.migrate(db)
            db.query("SELECT name FROM categories WHERE id = 'custom'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("My category", cursor.getString(0))
            }
            db.query("SELECT id FROM transactions").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals("original", cursor.getString(0))
            }
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
    }

    @Test fun accountUpgradeHandlesAlreadyRenamedBalanceAndRejectsUnknownLayouts() {
        val (_, helper) = fixture(5) {
            it.execSQL("CREATE TABLE accounts(id TEXT PRIMARY KEY, starting_balance REAL NOT NULL)")
            it.execSQL("INSERT INTO accounts VALUES ('original', 123.45)")
        }
        helper.use {
            AppDatabase.MIGRATION_5_6.migrate(it.writableDatabase)
            it.writableDatabase.query("SELECT starting_balance FROM accounts WHERE id = 'original'").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(123.45, cursor.getDouble(0), 0.0)
            }
        }
        val (_, unknown) = fixture(5) { it.execSQL("CREATE TABLE accounts(id TEXT PRIMARY KEY, unknown_balance REAL)") }
        unknown.use { assertTrue(runCatching { AppDatabase.MIGRATION_5_6.migrate(it.writableDatabase) }.isFailure) }
    }

    @Test fun supportedMigrationChainsValidateWithRoomAndKeepFinancialRows() {
        val stream = javaClass.classLoader!!.getResourceAsStream("com.example.data.AppDatabase/12.json")!!
        val entities = stream.bufferedReader().use { JSONObject(it.readText()).getJSONObject("database").getJSONArray("entities") }
        for (version in listOf(6, 8, 9, 10, 11)) {
            val (name, helper) = fixture(version) { db ->
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    if (version < 8 && table == "analytics_cache") continue
                    if (version < 9 && table in setOf("user_profiles", "ledger_scopes")) continue
                    if (version < 11 && table == "query_plan_cache") continue
                    var sql = entity.getString("createSql").replace("\u0024{TABLE_NAME}", table)
                        .replace(", `payment_method` TEXT", "")
                    if (version < 9 && table in setOf("transactions", "accounts", "budgets")) {
                        sql = sql.replace(", `ledger_scope_id` TEXT", "")
                            .replace(", `last_updated` INTEGER NOT NULL", "").replace(", `is_deleted` INTEGER NOT NULL", "")
                    }
                    if (version == 9 && table == "user_profiles") {
                        for (column in listOf("businessType", "address", "website", "gstNumber", "panNumber")) {
                            sql = sql.replace(", `$column` TEXT", "")
                        }
                    }
                    db.execSQL(sql)
                    val indexes = if (version < 11 && table == "transactions") org.json.JSONArray()
                        else entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indexes.length()) db.execSQL(indexes.getJSONObject(j).getString("createSql")
                        .replace("\u0024{TABLE_NAME}", table))
                }
                fun insert(table: String, values: Map<String, Any>) {
                    val columns = values.keys.joinToString { "`$it`" }
                    val placeholders = values.keys.joinToString { "?" }
                    db.execSQL("INSERT INTO `$table` ($columns) VALUES ($placeholders)", values.values.toTypedArray())
                }
                insert("categories", mapOf("id" to "food", "name" to "Food", "icon" to "icon", "color_hex" to "#123456",
                    "type" to "EXPENSE", "is_system" to 0, "order" to 0))
                val ownership = if (version >= 9) mapOf("last_updated" to 0, "is_deleted" to 0) else emptyMap()
                insert("accounts", mapOf("id" to "cash", "name" to "Cash", "type" to "CASH", "color" to "#123456",
                    "starting_balance" to 123.45, "created_at" to 0, "currency" to "INR", "is_archived" to 0,
                    "is_hidden" to 0, "exclude_from_total" to 0, "sort_order" to 0) + ownership)
                insert("transactions", mapOf("id" to "original", "title" to "Lunch", "amount" to 25.5,
                    "category_id" to "food", "account_id" to "cash", "type" to "EXPENSE", "date" to 0,
                    "is_recurring" to 0, "ai_categorized" to 0, "created_at" to 0, "updated_at" to 0,
                    "tags" to "", "is_archived" to 0) + ownership)
            }
            helper.close()
            val database = AppDatabase.builder(context, name).allowMainThreadQueries().build()
            try {
                val db = database.openHelper.writableDatabase // Room validates the complete migrated schema.
                db.query("SELECT amount, account_id FROM transactions WHERE id = 'original'").use { cursor ->
                    assertTrue("Version $version must retain the transaction", cursor.moveToFirst())
                    assertEquals(25.5, cursor.getDouble(0), 0.0); assertEquals("cash", cursor.getString(1))
                }
                db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
            } finally { database.close() }
        }
    }
}
