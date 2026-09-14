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

    /** 全部整理状态，用于「未整理」队列计算已处理集合。 */
    @Query("SELECT * FROM media_status")
    fun observeAll(): Flow<List<MediaStatusEntity>>

    @Query("SELECT COUNT(*) FROM media_status WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    /** 某媒体的当前整理状态，用于操作日志记录 before_state。 */
    @Query("SELECT * FROM media_status WHERE localAssetId = :id")
    suspend fun get(id: Long): MediaStatusEntity?

    /** 撤销到未整理状态，或从待删除恢复到整理队列。 */
    @Query("DELETE FROM media_status WHERE localAssetId IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 测试用：清空 App 内整理状态，让媒体重新回到未整理队列。 */
    @Query("DELETE FROM media_status")
    suspend fun clearAll()
}
