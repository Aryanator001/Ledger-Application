package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["category_id"])]
)
data class Budget(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "category_id") val categoryId: String,
    @ColumnInfo(name = "limit_amount") val limitAmount: Long,
    // stored in paise (×100) to avoid float errors
    val period: String = "MONTHLY",   // "WEEKLY" | "MONTHLY" | "YEARLY"
    val rollover: Boolean = false,
    @ColumnInfo(name = "alert_at") val alertAt: Double = 0.80,
    @ColumnInfo(name = "start_date") val startDate: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "ledger_scope_id") val ledgerScopeId: String? = null,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false
)
