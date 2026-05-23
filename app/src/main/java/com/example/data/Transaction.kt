package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "party_name") val partyName: String,
    val amount: Double,
    @ColumnInfo(name = "transaction_type") val transactionType: String,
    val particular: String? = null,
    @ColumnInfo(name = "raw_transcript") val rawTranscript: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
