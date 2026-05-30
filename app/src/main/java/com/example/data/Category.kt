package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "categories",
    indices = [Index(value = ["type"])]
)
data class Category(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String,
    @ColumnInfo(name = "color_hex") val colorHex: String,
    val type: String,        // "INCOME" | "EXPENSE" | "BOTH"
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "is_system") val isSystem: Boolean = false,
    val order: Int = 0
)
