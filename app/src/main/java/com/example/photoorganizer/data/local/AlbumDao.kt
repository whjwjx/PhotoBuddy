package com.example.photoorganizer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: AlbumItemEntity)

    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun observeAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT albumId, COUNT(*) AS count FROM album_items GROUP BY albumId")
    fun observeCounts(): Flow<List<AlbumCount>>

    @Query("SELECT * FROM album_items WHERE albumId = :albumId ORDER BY addedAt DESC")
    fun observeItems(albumId: Long): Flow<List<AlbumItemEntity>>

    @Query("SELECT * FROM album_items WHERE albumId = :albumId ORDER BY addedAt DESC")
    suspend fun getItems(albumId: Long): List<AlbumItemEntity>

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long)

    @Query("DELETE FROM album_items WHERE albumId = :albumId AND mediaId = :mediaId")
    suspend fun removeItem(
        albumId: Long,
        mediaId: Long,
    )
}
