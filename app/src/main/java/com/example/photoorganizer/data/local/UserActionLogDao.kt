package com.example.photoorganizer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserActionLogDao {
    @Insert
    suspend fun insert(log: UserActionLogEntity)

    /** 最近处理记录（PRD 六·首页）。 */
    @Query("SELECT * FROM user_action_log ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<UserActionLogEntity>>

    /** 单个媒体的操作历史。 */
    @Query("SELECT * FROM user_action_log WHERE mediaId = :mediaId ORDER BY createdAt DESC")
    fun observeForMedia(mediaId: Long): Flow<List<UserActionLogEntity>>
}
