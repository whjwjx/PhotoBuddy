package com.example.photoorganizer.domain

import android.app.PendingIntent
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.photoorganizer.data.MediaAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 删除协调器（PRD 8.2.1 DeleteCoordinator / 4.5）。
 *
 * API 30+：对非本 App 拥有的媒体，直接 ContentResolver.delete 会抛
 * RecoverableSecurityException，必须走 MediaStore.createDeleteRequest()
 * 由系统弹窗确认，用户允许后系统自动移入「最近删除」。符合 PRD
 * 「删除必须走系统确认或系统回收站能力，不做静默删除」。
 *
 * API 29-：直接删除（需 WRITE_EXTERNAL_STORAGE 权限）。
 */
class DeleteCoordinator(
    private val resolver: ContentResolver,
) {
    /** API 30+：创建系统删除确认请求，返回 PendingIntent 供 UI 启动；失败返回 null。 */
    fun createDeleteRequest(uris: List<Uri>): PendingIntent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching { MediaStore.createDeleteRequest(resolver, uris) }.getOrNull()
    }

    /** API 29-：直接删除。任何异常都视为失败，不向上抛。 */
    suspend fun deleteToTrash(asset: MediaAsset): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { resolver.delete(asset.uri, null, null) > 0 }.getOrDefault(false)
        }
}
