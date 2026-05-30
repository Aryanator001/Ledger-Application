package com.example.analytics

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached aggregate value for a query key.
 *
 * Generic key naming scheme (validated on read via bucket boundaries):
 *   - "total_expense_today"
 *   - "total_income_today"
 *   - "total_expense_this_week"
 *   - "total_expense_this_month"
 *   - "total_income_this_month"
 *   - "net_balance_this_month"
 *   - "entity_balance:vijay"           (LIFETIME bucket, top 20 entities LRU)
 *   - "category_total:food_this_month"
 *
 * bucketStartMs / bucketEndMs define when the cached value is valid.
 * On read, if current time's bucket boundaries differ from stored,
 * the entry is treated as stale and evicted.
 */
@Entity(tableName = "analytics_cache")
data class AnalyticsCache(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "total_amount") val totalAmount: Double,
    val count: Int,
    @ColumnInfo(name = "transaction_ids") val transactionIds: String,
    @ColumnInfo(name = "bucket_start_ms") val bucketStartMs: Long,
    @ColumnInfo(name = "bucket_end_ms") val bucketEndMs: Long,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long = System.currentTimeMillis()
)
