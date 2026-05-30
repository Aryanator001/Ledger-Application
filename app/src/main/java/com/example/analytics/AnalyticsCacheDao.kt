package com.example.analytics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface AnalyticsCacheDao {

    @Query("SELECT * FROM analytics_cache WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): AnalyticsCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AnalyticsCache)

    @Update
    suspend fun update(entry: AnalyticsCache)

    @Query("DELETE FROM analytics_cache WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM analytics_cache WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM analytics_cache")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM analytics_cache WHERE `key` LIKE 'entity_balance:%'")
    suspend fun entityCacheCount(): Int

    @Query("""
        SELECT * FROM analytics_cache
        WHERE `key` LIKE 'entity_balance:%'
        ORDER BY last_updated ASC
        LIMIT :limit
    """)
    suspend fun oldestEntityEntries(limit: Int): List<AnalyticsCache>
}
