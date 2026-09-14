package com.example.photoorganizer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaStatusDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaStatusEntity)

    @Query("SELECT * FROM media_status WHERE status = :status")
    fun observeByStatus(status: String): Flow<List<MediaStatusEntity>>

    @Query("SELECT COUNT(*) FROM media_status WHERE status = :status")
    suspend fun countByStatus(status: String): Int
}
