package com.example.photoorganizer.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 读取系统媒体库（PRD 8.2.1 MediaStoreRepository）。
 * 优先使用 MediaStore ID + content URI，不直接操作原始文件路径。
 */
class MediaStoreRepository(private val resolver: ContentResolver) {

    suspend fun loadAll(): List<MediaAsset> = withContext(Dispatchers.IO) {
        buildList {
            addAll(query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE))
            addAll(query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO))
        }
    }

    private fun query(baseUri: Uri, type: MediaType): List<MediaAsset> {
        val projection =
            buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.MIME_TYPE)
                add(MediaStore.MediaColumns.SIZE)
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
                add(MediaStore.MediaColumns.DATE_TAKEN)
                add(MediaStore.MediaColumns.BUCKET_ID)
                add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.MediaColumns.IS_FAVORITE)
                }
            }

        val out = mutableListOf<MediaAsset>()
        resolver
            .query(
                baseUri,
                projection.toTypedArray(),
                null,
                null,
                "${MediaStore.MediaColumns.DATE_TAKEN} DESC",
            ).use { cursor ->
                if (cursor == null) return@use
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val wIdx = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val hIdx = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val takenIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val bIdIdx = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
                val bNameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val favIdx =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                    } else {
                        -1
                    }
                val durIdx =
                    if (type == MediaType.VIDEO) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    out +=
                        MediaAsset(
                            id = id,
                            uri = ContentUris.withAppendedId(baseUri, id),
                            displayName = cursor.getString(nameIdx) ?: "",
                            mimeType = cursor.getString(mimeIdx) ?: "",
                            size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L,
                            width = if (wIdx >= 0) cursor.getInt(wIdx) else 0,
                            height = if (hIdx >= 0) cursor.getInt(hIdx) else 0,
                            durationMs = if (durIdx >= 0) cursor.getLong(durIdx) else 0L,
                            capturedAt = if (takenIdx >= 0) cursor.getLong(takenIdx) else 0L,
                            bucketId = if (bIdIdx >= 0) cursor.getString(bIdIdx) ?: "" else "",
                            bucketName = if (bNameIdx >= 0) cursor.getString(bNameIdx) ?: "" else "",
                            isFavorite = if (favIdx >= 0) cursor.getInt(favIdx) != 0 else false,
                            mediaType = type,
                        )
                }
            }
        return out
    }
}
