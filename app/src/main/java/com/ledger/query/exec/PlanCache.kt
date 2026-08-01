package com.ledger.query.exec

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.ledger.query.core.Direction
import com.ledger.query.core.GroupBy
import com.ledger.query.core.Metric
import com.ledger.query.core.QueryFrame
import com.ledger.query.core.QueryPlan
import java.time.LocalDate
import java.util.Collections

// ─────────────────────────────────────────────────────────────
// PlanSkeleton — captures the "shape" of a plan, sans specific values
// ─────────────────────────────────────────────────────────────

data class PlanSkeleton(
    val direction: Direction,
    val metric: Metric,
    val groupBy: GroupBy,
    val usesEntity: Boolean,
    val usesCategory: Boolean,
    val limit: Int?
) {
    companion object {
        fun from(p: QueryPlan) = PlanSkeleton(
            direction = p.direction,
            metric = p.metric,
            groupBy = p.groupBy,
            usesEntity = p.entityId != null,
            usesCategory = p.categoryId != null,
            limit = p.limit
        )
    }

    /** Re-inject this query's entity/time into the skeleton to produce a full plan. */
    fun hydrate(frame: QueryFrame): QueryPlan {
        val entityId = if (usesEntity) (frame.entity?.id ?: 1L) else null
        val categoryId = if (usesCategory) frame.category?.id else null
        val label = buildLabel(frame)
        return QueryPlan(
            direction = direction,
            metric = metric,
            groupBy = groupBy,
            entityId = entityId,
            categoryId = categoryId,
            start = frame.timeRange.start,
            end = frame.timeRange.end,
            limit = limit,
            label = label
        )
    }

    private fun buildLabel(frame: QueryFrame): String {
        val parts = mutableListOf<String>()
        parts += when (direction) {
            Direction.OUT -> "Paid"
            Direction.IN -> "Received"
            Direction.NET -> "Balance"
            Direction.UNSPECIFIED -> "Activity"
        }
        if (usesEntity) frame.entity?.canonicalName?.let { parts += it }
        if (usesCategory) frame.category?.canonicalName?.let { parts += it }
        parts += frame.timeRange.label
        return parts.joinToString(" · ")
    }
}

// ─────────────────────────────────────────────────────────────
// Interface
// ─────────────────────────────────────────────────────────────

interface PlanCache {
    suspend fun get(template: String): PlanSkeleton?
    suspend fun put(template: String, skeleton: PlanSkeleton)
    suspend fun size(): Int
    suspend fun clear()
}

// ─────────────────────────────────────────────────────────────
// Room entity + DAO
// ─────────────────────────────────────────────────────────────

@Entity(
    tableName = "query_plan_cache",
    indices = [Index(value = ["last_used_at"], name = "idx_qpc_last_used")]
)
data class QueryPlanCacheEntity(
    @PrimaryKey val template: String,
    val direction: String,
    val metric: String,
    @ColumnInfo(name = "group_by") val groupBy: String,
    @ColumnInfo(name = "uses_entity") val usesEntity: Boolean,
    @ColumnInfo(name = "uses_category") val usesCategory: Boolean,
    @ColumnInfo(name = "limit_val") val limitVal: Int?,
    @ColumnInfo(name = "hit_count") val hitCount: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Long = System.currentTimeMillis()
)

@Dao
interface QueryPlanCacheDao {
    @Query("SELECT * FROM query_plan_cache WHERE template = :template LIMIT 1")
    suspend fun get(template: String): QueryPlanCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QueryPlanCacheEntity)

    @Query("UPDATE query_plan_cache SET hit_count = hit_count + 1, last_used_at = :now WHERE template = :template")
    suspend fun incrementHit(template: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM query_plan_cache")
    suspend fun count(): Int

    @Query("DELETE FROM query_plan_cache WHERE template IN (SELECT template FROM query_plan_cache ORDER BY last_used_at ASC LIMIT :n)")
    suspend fun evictOldest(n: Int)

    @Query("DELETE FROM query_plan_cache")
    suspend fun clearAll()
}

// ─────────────────────────────────────────────────────────────
// Room-backed implementation with in-memory LRU read-through
// ─────────────────────────────────────────────────────────────

private const val MAX_ROOM_ROWS = 500
private const val MEM_CACHE_SIZE = 200

class RoomPlanCacheImpl(private val dao: QueryPlanCacheDao) : PlanCache {

    // Access-ordered LinkedHashMap acts as LRU. Synchronized because coroutines
    // from different calls may touch it concurrently.
    private val memCache: MutableMap<String, PlanSkeleton> =
        Collections.synchronizedMap(object : LinkedHashMap<String, PlanSkeleton>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, PlanSkeleton>?) = size > MEM_CACHE_SIZE
        })

    override suspend fun get(template: String): PlanSkeleton? {
        memCache[template]?.let { return it }
        val entity = dao.get(template) ?: return null
        dao.incrementHit(template)
        val skeleton = entity.toSkeleton()
        memCache[template] = skeleton
        return skeleton
    }

    override suspend fun put(template: String, skeleton: PlanSkeleton) {
        memCache[template] = skeleton
        val now = System.currentTimeMillis()
        val existing = dao.get(template)
        dao.upsert(
            QueryPlanCacheEntity(
                template = template,
                direction = skeleton.direction.name,
                metric = skeleton.metric.name,
                groupBy = skeleton.groupBy.name,
                usesEntity = skeleton.usesEntity,
                usesCategory = skeleton.usesCategory,
                limitVal = skeleton.limit,
                hitCount = (existing?.hitCount ?: 0) + 1,
                createdAt = existing?.createdAt ?: now,
                lastUsedAt = now
            )
        )
        if (dao.count() > MAX_ROOM_ROWS) {
            dao.evictOldest(MAX_ROOM_ROWS / 2)
        }
    }

    override suspend fun size(): Int = dao.count()

    override suspend fun clear() {
        memCache.clear()
        dao.clearAll()
    }

    private fun QueryPlanCacheEntity.toSkeleton() = PlanSkeleton(
        direction = Direction.valueOf(direction),
        metric = Metric.valueOf(metric),
        groupBy = GroupBy.valueOf(groupBy),
        usesEntity = usesEntity,
        usesCategory = usesCategory,
        limit = limitVal
    )
}

// ─────────────────────────────────────────────────────────────
// In-memory fake — for tests only, never used in production
// ─────────────────────────────────────────────────────────────

class InMemoryPlanCache : PlanCache {
    private val map = mutableMapOf<String, PlanSkeleton>()

    override suspend fun get(template: String) = map[template]
    override suspend fun put(template: String, skeleton: PlanSkeleton) { map[template] = skeleton }
    override suspend fun size() = map.size
    override suspend fun clear() = map.clear()
}
