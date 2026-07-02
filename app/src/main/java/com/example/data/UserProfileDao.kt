package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE uid = :uid")
    suspend fun getUserProfile(uid: String): UserProfile?

    @Query("SELECT * FROM user_profiles WHERE uid = :uid")
    fun observeUserProfile(uid: String): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)
}
