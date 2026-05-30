package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Account::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_DEFAULT
        )
    ]
)
data class Transaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "account_id", defaultValue = "default_cash") val accountId: String = "default_cash",
    val type: String, // "INCOME" or "EXPENSE"
    val date: Long, // Unix timestamp
    val description: String? = null,
    @ColumnInfo(name = "is_recurring") val isRecurring: Boolean = false,
    @ColumnInfo(name = "ai_categorized") val aiCategorized: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "to_account_id") val toAccountId: String? = null,
    val tags: String = "",
    @ColumnInfo(name = "recurring_id") val recurringId: String? = null,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    
    // Legacy fields maintained temporarily just in case if UI expects them
    @ColumnInfo(name = "raw_transcript") val rawTranscript: String? = null
)

