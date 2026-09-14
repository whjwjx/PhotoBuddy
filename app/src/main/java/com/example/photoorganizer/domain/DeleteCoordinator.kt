package com.example.photoorganizer.domain

import android.content.ContentResolver
import android.os.Build
import android.provider.MediaStore
import com.example.photoorganizer.data.MediaAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 删除协调器（PRD 8.2.1 DeleteCoordinator / 4.5）。
 *
 * 重要：删除前必须由 UI 做二次确认，本类只负责执行系统删除。
 * Android 10（API 29）起，对非 owner 的媒体调用 ContentResolver.delete 会进入系统
 * 「最近删除 / 回收站」，符合 PRD「删除必须走系统回收站能力，不做静默删除」。
 */
class DeleteCoordinator(
    private val resolver: ContentResolver,
) {
    suspend fun deleteToTrash(asset: MediaAsset): Boolean =
        withContext(Dispatchers.IO) {
            resolver.delete(asset.uri, null, null) > 0
        }
}
