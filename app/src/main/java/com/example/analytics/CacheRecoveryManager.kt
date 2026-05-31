package com.example.analytics

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages one-time recovery operations on the analytics cache.
 *
 * Pattern: a version number constant defines the "required" recovery
 * state. A SharedPreferences flag tracks the last applied version.
 * On launch, if applied < required, the recovery runs and bumps the flag.
 *
 * Why this pattern: future cache bugs (data corruption, key schema
 * changes) can be fixed by bumping the version constant and adding
 * a new recovery step in runIfNeeded(). Users get the fix
 * transparently on next launch without manual intervention.
 *
 * Current recovery state:
 *   Version 1: Wipe the entire analytics_cache table.
 *              Reason: Stage 2 deployment poisoned cache rows with
 *              count=0 when queries ran before any transactions existed.
 */
class CacheRecoveryManager(
    private val context: Context,
    private val analyticsCacheDao: AnalyticsCacheDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    companion object {
        private const val PREFS_NAME = "cache_recovery_prefs"
        private const val KEY_APPLIED_VERSION = "applied_recovery_version"
        private const val REQUIRED_VERSION = 1
        private const val TAG = "CacheRecovery"
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun runIfNeeded() {
        val applied = prefs.getInt(KEY_APPLIED_VERSION, 0)
        if (applied >= REQUIRED_VERSION) {
            Log.d(TAG, "Cache recovery up to date (applied=$applied, required=$REQUIRED_VERSION)")
            return
        }

        Log.i(TAG, "Running cache recovery: $applied → $REQUIRED_VERSION")
        scope.launch {
            try {
                runRecoverySteps(fromVersion = applied)
                prefs.edit().putInt(KEY_APPLIED_VERSION, REQUIRED_VERSION).apply()
                Log.i(TAG, "Cache recovery complete, flag bumped to $REQUIRED_VERSION")
            } catch (e: Exception) {
                Log.e(TAG, "Cache recovery failed — flag NOT bumped, will retry next launch", e)
            }
        }
    }

    private suspend fun runRecoverySteps(fromVersion: Int) {
        // Step 0 → 1: Flush all cache entries to clear B2 poisoning.
        if (fromVersion < 1) {
            analyticsCacheDao.clearAll()
            Log.i(TAG, "Step 0→1: cleared all analytics_cache rows")
        }

        // Future recovery steps go here. Pattern:
        //   if (fromVersion < N) { ...do step N... }
        // Then bump REQUIRED_VERSION to N.
    }
}
