package com.example.photoorganizer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用内自定义相册（PRD 5.6 / 8.2.1）。
 * 说明：Android 上「加入系统相册」不是强一致能力，MVP 只做应用内分组。
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** 相册成员：仅以 MediaStore 的 mediaId 关联，不修改原文件。 */
@Entity(tableName = "album_items", primaryKeys = ["albumId", "mediaId"])
data class AlbumItemEntity(
    val albumId: Long,
    val mediaId: Long,
    val addedAt: Long,
)

/** 相册及其成员数量（Room 聚合查询结果）。 */
data class AlbumCount(
    val albumId: Long,
    val count: Int,
)
