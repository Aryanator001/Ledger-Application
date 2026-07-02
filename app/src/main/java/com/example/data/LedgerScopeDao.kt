package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerScopeDao {
    @Query("SELECT * FROM ledger_scopes WHERE profileId = :profileId AND isDeleted = 0")
    fun getScopesForProfile(profileId: String): Flow<List<LedgerScope>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScope(scope: LedgerScope)
}
