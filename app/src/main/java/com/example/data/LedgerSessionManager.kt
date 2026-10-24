package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LedgerSessionManager(private val database: AppDatabase, private val preferences: UserPreferences) {
    private val mutex = Mutex()

    suspend fun activateUser(userId: String): String? = mutex.withLock {
        val previousDefault = preferences.defaultAccountId.first()
        val previousScope = preferences.activeLedgerScopeId.first()
            .takeIf { preferences.activeUserId.first() == userId }
        // Clear before any database work; failures must not leave another ledger selected.
        preferences.setActiveLedger(null, null)
        val scopeId = database.withTransaction {
            val profile = database.userProfileDao().getUserProfile(userId)
            if (profile == null || profile.isDeleted) return@withTransaction null
            val scopes = database.ledgerScopeDao()
            val scope = previousScope?.let { scopes.getScopeForProfile(it, userId) }
                ?: scopes.getScopesForProfile(userId).first().firstOrNull()
                ?: return@withTransaction null
            DatabaseStitcher(database).stitchUnassignedData(userId, scope.id)
            DefaultAccounts.ensure(database.accountDao(), scope.id)
            scope.id
        }
        val defaultAccount = if (scopeId != null && scopeId == previousScope && previousDefault != null) {
            DefaultAccounts.resolve(database.accountDao(), previousDefault, scopeId)?.id
        } else null
        preferences.setActiveLedger(userId, scopeId, defaultAccount)
        scopeId
    }

    suspend fun clear() = mutex.withLock { preferences.setActiveLedger(null, null) }
}
