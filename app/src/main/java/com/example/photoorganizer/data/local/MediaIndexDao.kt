package com.example.photoorganizer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaIndexDao {
    @Query("SELECT * FROM media_index")
    fun observeAll(): Flow<List<MediaIndexEntity>>

    @Query("SELECT mediaId FROM media_index")
    suspend fun allIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<MediaIndexEntity>)

    @Query("DELETE FROM media_index WHERE mediaId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM media_index")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM media_index")
    suspend fun count(): Int
}
