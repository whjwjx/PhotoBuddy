package com.example.photoorganizer.data

import android.net.Uri

/** 媒体类型（PRD 5.3 media_type）。 */
enum class MediaType { IMAGE, VIDEO }

/**
 * 本地媒体资产模型，字段对齐 PRD 5.3 / 9.1 MediaAsset。
 * MVP 阶段不写入文件本身，只用 MediaStore ID + content URI 引用。
 */
data class MediaAsset(
    val id: Long,
    val uri: Uri,
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
    val mediaType: MediaType,
    /** MediaStore DATE_ADDED（秒），用于增量扫描判断新增。 */
    val dateAdded: Long = 0L,
) {
    /** 截图判定（文件名/相册名含 screenshot 或中文「截图」）。供队列与统计复用。 */
    fun isScreenshot(): Boolean =
        mediaType == MediaType.IMAGE &&
            (
                displayName.contains("screenshot", ignoreCase = true) ||
                    displayName.contains("截图") ||
                    bucketName.contains("screenshot", ignoreCase = true)
            )
}
