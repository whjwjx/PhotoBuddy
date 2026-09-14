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

    /**
     * 本 App 删除、仍可恢复的项（App 内「最近删除」）。
     * 厂商相册的私有「最近删除」第三方 App 无法写入，所以这里自己维护一份。
     */
    @Query("SELECT * FROM user_action_log WHERE action = 'delete' ORDER BY createdAt DESC LIMIT :limit")
    fun observeDeleted(limit: Int): Flow<List<UserActionLogEntity>>

    /** 恢复后把该条动作标记为 restore，使其从「可恢复」列表移除，同时保留操作轨迹。 */
    @Query("UPDATE user_action_log SET action = :action WHERE id = :id")
    suspend fun updateAction(
        id: Long,
        action: String,
    )
}
