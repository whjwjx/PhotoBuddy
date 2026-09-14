package com.example.photoorganizer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户操作日志（PRD 9.5 UserActionLog / 11.4「操作可追踪」）。
 * 记录每一次整理决策的来源、前后状态，便于追溯与「最近处理记录」展示。
 */
@Entity(tableName = "user_action_log")
data class UserActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: String = "local",
    val mediaId: Long,
    val mediaName: String,
    /** IMAGE / VIDEO，恢复时需要用它拼出正确的 MediaStore URI。 */
    val mediaType: String = "IMAGE",
    val action: String,
    /** 来源队列，例如「随机整理」或「按月份 · 2026-09」。 */
    val source: String,
    val beforeState: String,
    val afterState: String,
    /** 删除动作释放的字节数，其余动作为 0。 */
    val freedBytes: Long = 0L,
    val createdAt: Long,
)
