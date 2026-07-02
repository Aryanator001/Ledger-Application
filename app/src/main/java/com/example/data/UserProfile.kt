package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val businessName: String? = null,
    val phoneNumber: String? = null,
    val businessType: String? = null,  // Freelancer / Sole Proprietor / Partnership / Pvt Ltd / LLP
    val address: String? = null,
    val website: String? = null,
    val gstNumber: String? = null,
    val panNumber: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
