package com.example.photoorganizer.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaType

/**
 * 本地媒体索引（PRD 5.3：本地保存媒体 ID、类型、拍摄时间、大小、宽高、时长、相册、收藏）。
 * 有了索引后，App 启动可直接从 Room 读取，不必每次全量查 MediaStore，
 * 也才能做「增量扫描」——只同步上次扫描之后新增或删除的媒体。
 */
@Entity(tableName = "media_index")
data class MediaIndexEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val capturedAt: Long,
    val bucketId: String,
    val bucketName: String,
    val isFavorite: Boolean,
    val mediaType: String,
    val dateAdded: Long,
)

fun MediaIndexEntity.toAsset(): MediaAsset =
    MediaAsset(
        id = mediaId,
        uri = Uri.parse(uri),
        displayName = displayName,
        mimeType = mimeType,
        size = size,
        width = width,
        height = height,
        durationMs = durationMs,
        capturedAt = capturedAt,
        bucketId = bucketId,
        bucketName = bucketName,
        isFavorite = isFavorite,
        mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.IMAGE),
        dateAdded = dateAdded,
    )

fun MediaAsset.toIndex(): MediaIndexEntity =
    MediaIndexEntity(
        mediaId = id,
        uri = uri.toString(),
        displayName = displayName,
        mimeType = mimeType,
        size = size,
        width = width,
        height = height,
        durationMs = durationMs,
        capturedAt = capturedAt,
        bucketId = bucketId,
        bucketName = bucketName,
        isFavorite = isFavorite,
        mediaType = mediaType.name,
        dateAdded = dateAdded,
    )
