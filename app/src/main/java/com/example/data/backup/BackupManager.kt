package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.Account
import com.example.data.AccountRepository
import com.example.data.BudgetRepository
import com.example.data.Category
import com.example.data.TransactionRepository
import com.example.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "BackupManager"

class BackupManager(
    private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val budgetRepository: BudgetRepository,
    private val userPreferences: UserPreferences
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun exportBackup(destination: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        Log.d(TAG, "exportBackup: start, destination=$destination")
        val scopeId = userPreferences.activeLedgerScopeId.first()
        Log.d(TAG, "exportBackup: scopeId=$scopeId")
        if (scopeId == null) {
            Log.w(TAG, "exportBackup: no active ledger scope, aborting")
            return@withContext BackupOutcome.Failure(BackupError.NO_ACTIVE_LEDGER_SCOPE)
        }

        try {
            val backup = LedgerBackup(
                exportedAt = System.currentTimeMillis(),
                ledgerScopeId = scopeId,
                categories = transactionRepository.allCategories.first().map { it.toBackupDto() },
                accounts = accountRepository.allAccounts.first().map { it.toBackupDto() },
                budgets = budgetRepository.allBudgets.first().map { it.toBackupDto() },
                transactions = transactionRepository.getAllTransactionsOnce().map { it.toBackupDto() }
            )
            Log.d(TAG, "exportBackup: collected ${backup.categories.size} categories, " +
                "${backup.accounts.size} accounts, ${backup.budgets.size} budgets, " +
                "${backup.transactions.size} transactions")
            val content = json.encodeToString(LedgerBackup.serializer(), backup)
            val written = context.contentResolver.openOutputStream(destination)?.use { stream ->
                stream.write(content.toByteArray())
                true
            } ?: false
            Log.d(TAG, "exportBackup: write success=$written")
            if (!written) return@withContext BackupOutcome.Failure(BackupError.IO_ERROR)

            userPreferences.setLastBackupAt(System.currentTimeMillis())
            Log.d(TAG, "exportBackup: done")
            BackupOutcome.Success
        } catch (e: Exception) {
            Log.e(TAG, "exportBackup: failed", e)
            BackupOutcome.Failure(BackupError.IO_ERROR)
        }
    }

    suspend fun restoreBackup(source: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        Log.d(TAG, "restoreBackup: start, source=$source")
        val text = try {
            context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext BackupOutcome.Failure(BackupError.IO_ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "restoreBackup: failed to read source", e)
            return@withContext BackupOutcome.Failure(BackupError.IO_ERROR)
        }

        val backup = try {
            json.decodeFromString<LedgerBackup>(text)
        } catch (e: SerializationException) {
            return@withContext BackupOutcome.Failure(BackupError.MALFORMED_JSON)
        } catch (e: IllegalArgumentException) {
            return@withContext BackupOutcome.Failure(BackupError.MALFORMED_JSON)
        }

        if (backup.schemaVersion > CURRENT_BACKUP_SCHEMA_VERSION) {
            return@withContext BackupOutcome.Failure(BackupError.UNSUPPORTED_SCHEMA_VERSION)
        }

        val scopeId = userPreferences.activeLedgerScopeId.first()
            ?: return@withContext BackupOutcome.Failure(BackupError.NO_ACTIVE_LEDGER_SCOPE)

        try {
            backup.categories.forEach { transactionRepository.insertCategory(it.toEntity()) }
            backup.accounts.forEach { accountRepository.insertAccount(it.toEntity(scopeId)) }
            backup.budgets.forEach { budgetRepository.insertBudget(it.toEntity(scopeId)) }
            backup.transactions.forEach { transactionRepository.insertTransaction(it.toEntity(scopeId)) }
            Log.d(TAG, "restoreBackup: done, restored ${backup.transactions.size} transactions")
            BackupOutcome.Success
        } catch (e: Exception) {
            Log.e(TAG, "restoreBackup: failed to write entities", e)
            BackupOutcome.Failure(BackupError.IO_ERROR)
        }
    }

    suspend fun exportCsv(destination: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        Log.d(TAG, "exportCsv: start, destination=$destination")
        try {
            val categoryNames = transactionRepository.allCategories.first().associate { it.id to it.name }
            val accountNames = accountRepository.allAccounts.first().associate { it.id to it.name }
            val transactions = transactionRepository.getAllTransactionsOnce()
            Log.d(TAG, "exportCsv: collected ${transactions.size} transactions")

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val sb = StringBuilder()
            sb.appendLine("Date,Type,Category,Amount,Account,Note")
            transactions.forEach { tx ->
                val note = (tx.description ?: "").replace("\n", " ")
                sb.appendLine(listOf(
                    sdf.format(Date(tx.date)),
                    tx.type,
                    categoryNames[tx.categoryId] ?: tx.categoryId,
                    tx.amount.toString(),
                    accountNames[tx.accountId] ?: tx.accountId,
                    note
                ).joinToString(",") { csvField(it) })
            }

            val written = context.contentResolver.openOutputStream(destination)?.use { stream ->
                stream.write(sb.toString().toByteArray())
                true
            } ?: false
            Log.d(TAG, "exportCsv: write success=$written")
            if (!written) return@withContext BackupOutcome.Failure(BackupError.IO_ERROR)

            userPreferences.setLastBackupAt(System.currentTimeMillis())
            Log.d(TAG, "exportCsv: done")
            BackupOutcome.Success
        } catch (e: Exception) {
            Log.e(TAG, "exportCsv: failed", e)
            BackupOutcome.Failure(BackupError.IO_ERROR)
        }
    }

    suspend fun importCsv(source: Uri): BackupOutcome = withContext(Dispatchers.IO) {
        Log.d(TAG, "importCsv: start, source=$source")
        val text = try {
            context.contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext BackupOutcome.Failure(BackupError.IO_ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "importCsv: failed to read source", e)
            return@withContext BackupOutcome.Failure(BackupError.IO_ERROR)
        }

        val scopeId = userPreferences.activeLedgerScopeId.first()
            ?: return@withContext BackupOutcome.Failure(BackupError.NO_ACTIVE_LEDGER_SCOPE)

        val rows = parseCsvRows(text)
        if (rows.isEmpty()) {
            Log.w(TAG, "importCsv: no data rows found")
            return@withContext BackupOutcome.Failure(BackupError.MALFORMED_CSV)
        }
        val dataRows = if (rows.first().map { it.trim() } ==
            listOf("Date", "Type", "Category", "Amount", "Account", "Note")
        ) rows.drop(1) else rows

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val categoriesByName = transactionRepository.allCategories.first()
            .associateBy { it.name.trim().lowercase() }.toMutableMap()
        val accountsByName = accountRepository.allAccounts.first()
            .associateBy { it.name.trim().lowercase() }.toMutableMap()

        try {
            var imported = 0
            for (row in dataRows) {
                if (row.size < 6) {
                    Log.w(TAG, "importCsv: malformed row, expected 6 columns, got ${row.size}")
                    return@withContext BackupOutcome.Failure(BackupError.MALFORMED_CSV)
                }
                val dateStr = row[0]
                val typeStr = row[1]
                val categoryName = row[2]
                val amountStr = row[3]
                val accountName = row[4]
                val note = row[5]

                val dateMs = try {
                    sdf.parse(dateStr.trim())?.time
                        ?: return@withContext BackupOutcome.Failure(BackupError.MALFORMED_CSV)
                } catch (e: Exception) {
                    return@withContext BackupOutcome.Failure(BackupError.MALFORMED_CSV)
                }
                val amount = amountStr.trim().toDoubleOrNull()
                    ?: return@withContext BackupOutcome.Failure(BackupError.MALFORMED_CSV)
                val type = typeStr.trim().uppercase().ifEmpty { "EXPENSE" }

                val categoryKey = categoryName.trim().lowercase()
                val category = categoriesByName[categoryKey] ?: run {
                    val newCategory = Category(
                        name = categoryName.trim(),
                        icon = "🏷️",
                        colorHex = "#9E9E9E",
                        type = type
                    )
                    transactionRepository.insertCategory(newCategory)
                    categoriesByName[categoryKey] = newCategory
                    newCategory
                }

                val accountKey = accountName.trim().lowercase()
                val account = accountsByName[accountKey] ?: run {
                    val newAccount = Account(
                        name = accountName.trim(),
                        type = "CASH",
                        color = "#9E9E9E",
                        ledgerScopeId = scopeId
                    )
                    accountRepository.insertAccount(newAccount)
                    accountsByName[accountKey] = newAccount
                    newAccount
                }

                transactionRepository.insertTransaction(
                    com.example.data.Transaction(
                        title = note.trim().ifEmpty { categoryName.trim() },
                        amount = amount,
                        categoryId = category.id,
                        accountId = account.id,
                        type = type,
                        date = dateMs,
                        description = note.trim().ifEmpty { null },
                        ledgerScopeId = scopeId
                    )
                )
                imported++
            }
            Log.d(TAG, "importCsv: done, imported $imported transactions")
            BackupOutcome.Success
        } catch (e: Exception) {
            Log.e(TAG, "importCsv: failed to write entities", e)
            BackupOutcome.Failure(BackupError.IO_ERROR)
        }
    }

    private fun csvField(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n'))
            "\"${value.replace("\"", "\"\"")}\"" else value

    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString())
                        field = StringBuilder()
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(field.toString())
                        field = StringBuilder()
                        if (row.any { it.isNotBlank() }) rows.add(row)
                        row = mutableListOf()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotBlank() }) rows.add(row)
        }
        return rows
    }
}
