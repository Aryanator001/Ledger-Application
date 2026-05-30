package com.example.analytics

import com.example.data.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Maintains write-through invariants on the analytics cache.
 *
 * Strategy:
 *   - INSERT: for each affected key, increment value + count in place
 *     (if cache row exists and its bucket is current). If bucket
 *     rolled over, delete the entry — next read will repopulate.
 *     If no cache row exists, skip (don't proactively populate).
 *   - DELETE: decrement.
 *   - UPDATE: decrement old + increment new.
 *
 * Runs on its own SupervisorJob scope so failures NEVER block the
 * underlying transaction write.
 */
class AnalyticsCacheUpdater(
    private val analyticsCacheDao: AnalyticsCacheDao,
    private val keyResolver: AnalyticsKeyResolver,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {

    private val maxEntityCacheSize = 20

    fun onTransactionInserted(transaction: Transaction) {
        scope.launch {
            keyResolver.keysAffectedBy(transaction).forEach { affected ->
                applyDelta(affected, transactionId = transaction.id, deltaCount = 1)
            }
            evictExcessEntityEntriesIfNeeded()
        }
    }

    fun onTransactionDeleted(transaction: Transaction) {
        scope.launch {
            keyResolver.keysAffectedBy(transaction).forEach { affected ->
                applyDelta(
                    affected.copy(signedDelta = -affected.signedDelta),
                    transactionId = transaction.id,
                    deltaCount = -1
                )
            }
        }
    }

    fun onTransactionUpdated(old: Transaction, new: Transaction) {
        onTransactionDeleted(old)
        onTransactionInserted(new)
    }

    private suspend fun applyDelta(affected: AffectedKey, transactionId: String, deltaCount: Int) {
        val existing = analyticsCacheDao.get(affected.key) ?: return

        // Bucket rollover check
        if (existing.bucketStartMs != affected.bucketStartMs) {
            analyticsCacheDao.delete(affected.key)
            return
        }

        val newIds = when {
            deltaCount > 0 -> if (existing.transactionIds.isBlank()) transactionId
                              else "${existing.transactionIds},$transactionId"
            deltaCount < 0 -> existing.transactionIds
                .split(",")
                .filter { it.isNotBlank() && it != transactionId }
                .joinToString(",")
            else -> existing.transactionIds
        }

        analyticsCacheDao.update(existing.copy(
            totalAmount = existing.totalAmount + affected.signedDelta,
            count = (existing.count + deltaCount).coerceAtLeast(0),
            transactionIds = newIds,
            lastUpdated = System.currentTimeMillis()
        ))
    }

    private suspend fun evictExcessEntityEntriesIfNeeded() {
        val count = analyticsCacheDao.entityCacheCount()
        if (count <= maxEntityCacheSize) return
        val excess = count - maxEntityCacheSize
        analyticsCacheDao.oldestEntityEntries(excess).forEach {
            analyticsCacheDao.delete(it.key)
        }
    }
}
