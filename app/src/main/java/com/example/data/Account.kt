package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // CASH | BANK | WALLET | UPI | CREDIT_CARD | SAVINGS | INVESTMENT
    val color: String, // Hex color string
    val icon: String? = null, // Emoji
    @ColumnInfo(name = "starting_balance") val startingBalance: Double = 0.0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    val currency: String = "INR",
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "is_hidden") val isHidden: Boolean = false,
    @ColumnInfo(name = "exclude_from_total") val excludeFromTotal: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "ledger_scope_id") val ledgerScopeId: String? = null,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false
) {
    fun calculateBalance(transactions: List<Transaction>): Double {
        var bal = startingBalance
        transactions.forEach { tx ->
            if (tx.accountId == id) {
                if (tx.type.equals("INCOME", ignoreCase = true) || tx.type.equals("CREDIT", ignoreCase = true)) {
                    bal += tx.amount
                } else {
                    bal -= tx.amount // EXPENSE, DEBIT, TRANSFER (outgoing)
                }
            } else if (tx.toAccountId == id && tx.type.equals("TRANSFER", ignoreCase = true)) {
                bal += tx.amount // TRANSFER (incoming)
            }
        }
        return bal
    }
}
