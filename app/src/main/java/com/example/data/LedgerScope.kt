package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "ledger_scopes",
    foreignKeys = [
        ForeignKey(
            entity = UserProfile::class,
            parentColumns = ["uid"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LedgerScope(
    @PrimaryKey val id: String,
    val profileId: String,
    val name: String,
    val type: String, // "PERSONAL" or "BUSINESS"
    val lastUpdated: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
