package com.example.photoorganizer.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地整理状态（PRD 5.3 / 9.1）。与媒体文件本身解耦，
 * 仅以 MediaStore 的 localAssetId 关联。status 取值见 [MediaStatus]。
 */
@Entity(tableName = "media_status")
data class MediaStatusEntity(
    @PrimaryKey val localAssetId: Long,
    val mediaType: String,
    val status: String,
    val updatedAt: Long,
)

/** 用户对单张媒体的整理决策（PRD 4.2）。 */
enum class MediaStatus(val value: String, val label: String) {
    KEEP("keep", "保留"),
    DELETE("delete", "删除"),
    LATER("later", "稍后"),
    PERMANENT("permanent", "永久保留"),
}
