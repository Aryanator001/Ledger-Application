package com.example.data.backup

import com.example.data.Account
import com.example.data.Budget
import com.example.data.Category
import com.example.data.Transaction
import kotlinx.serialization.Serializable

const val CURRENT_BACKUP_SCHEMA_VERSION = 1

@Serializable
data class BackupCategoryDto(
    val id: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val type: String,
    val parentId: String?,
    val isSystem: Boolean,
    val order: Int
)

@Serializable
data class BackupAccountDto(
    val id: String,
    val name: String,
    val type: String,
    val color: String,
    val icon: String?,
    val startingBalance: Double,
    val createdAt: Long,
    val currency: String,
    val isArchived: Boolean,
    val isHidden: Boolean,
    val excludeFromTotal: Boolean,
    val sortOrder: Int,
    val ledgerScopeId: String?,
    val lastUpdated: Long
)

@Serializable
data class BackupBudgetDto(
    val id: String,
    val categoryId: String,
    val limitAmount: Long,
    val period: String,
    val rollover: Boolean,
    val alertAt: Double,
    val startDate: Long,
    val ledgerScopeId: String?,
    val lastUpdated: Long
)

@Serializable
data class BackupTransactionDto(
    val id: String,
    val title: String,
    val amount: Double,
    val categoryId: String,
    val accountId: String,
    val type: String,
    val date: Long,
    val description: String?,
    val isRecurring: Boolean,
    val aiCategorized: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val toAccountId: String?,
    val tags: String,
    val recurringId: String?,
    val isArchived: Boolean,
    val ledgerScopeId: String?,
    val lastUpdated: Long,
    val paymentMethod: String?
)

@Serializable
data class LedgerBackup(
    val schemaVersion: Int = CURRENT_BACKUP_SCHEMA_VERSION,
    val exportedAt: Long,
    val ledgerScopeId: String,
    val categories: List<BackupCategoryDto>,
    val accounts: List<BackupAccountDto>,
    val budgets: List<BackupBudgetDto>,
    val transactions: List<BackupTransactionDto>
)

enum class BackupError {
    IO_ERROR,
    MALFORMED_JSON,
    UNSUPPORTED_SCHEMA_VERSION,
    NO_ACTIVE_LEDGER_SCOPE,
    MALFORMED_CSV
}

sealed interface BackupOutcome {
    data object Success : BackupOutcome
    data class Failure(val error: BackupError) : BackupOutcome
}

fun Category.toBackupDto() = BackupCategoryDto(
    id = id,
    name = name,
    icon = icon,
    colorHex = colorHex,
    type = type,
    parentId = parentId,
    isSystem = isSystem,
    order = order
)

fun BackupCategoryDto.toEntity() = Category(
    id = id,
    name = name,
    icon = icon,
    colorHex = colorHex,
    type = type,
    parentId = parentId,
    isSystem = isSystem,
    order = order
)

fun Account.toBackupDto() = BackupAccountDto(
    id = id,
    name = name,
    type = type,
    color = color,
    icon = icon,
    startingBalance = startingBalance,
    createdAt = createdAt,
    currency = currency,
    isArchived = isArchived,
    isHidden = isHidden,
    excludeFromTotal = excludeFromTotal,
    sortOrder = sortOrder,
    ledgerScopeId = ledgerScopeId,
    lastUpdated = lastUpdated
)

fun BackupAccountDto.toEntity(scopeId: String) = Account(
    id = id,
    name = name,
    type = type,
    color = color,
    icon = icon,
    startingBalance = startingBalance,
    createdAt = createdAt,
    currency = currency,
    isArchived = isArchived,
    isHidden = isHidden,
    excludeFromTotal = excludeFromTotal,
    sortOrder = sortOrder,
    ledgerScopeId = scopeId,
    lastUpdated = lastUpdated
)

fun Budget.toBackupDto() = BackupBudgetDto(
    id = id,
    categoryId = categoryId,
    limitAmount = limitAmount,
    period = period,
    rollover = rollover,
    alertAt = alertAt,
    startDate = startDate,
    ledgerScopeId = ledgerScopeId,
    lastUpdated = lastUpdated
)

fun BackupBudgetDto.toEntity(scopeId: String) = Budget(
    id = id,
    categoryId = categoryId,
    limitAmount = limitAmount,
    period = period,
    rollover = rollover,
    alertAt = alertAt,
    startDate = startDate,
    ledgerScopeId = scopeId,
    lastUpdated = lastUpdated
)

fun Transaction.toBackupDto() = BackupTransactionDto(
    id = id,
    title = title,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    type = type,
    date = date,
    description = description,
    isRecurring = isRecurring,
    aiCategorized = aiCategorized,
    createdAt = createdAt,
    updatedAt = updatedAt,
    toAccountId = toAccountId,
    tags = tags,
    recurringId = recurringId,
    isArchived = isArchived,
    ledgerScopeId = ledgerScopeId,
    lastUpdated = lastUpdated,
    paymentMethod = paymentMethod
)

fun BackupTransactionDto.toEntity(scopeId: String) = Transaction(
    id = id,
    title = title,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    type = type,
    date = date,
    description = description,
    isRecurring = isRecurring,
    aiCategorized = aiCategorized,
    createdAt = createdAt,
    updatedAt = updatedAt,
    toAccountId = toAccountId,
    tags = tags,
    recurringId = recurringId,
    isArchived = isArchived,
    ledgerScopeId = scopeId,
    lastUpdated = lastUpdated,
    paymentMethod = paymentMethod
)
