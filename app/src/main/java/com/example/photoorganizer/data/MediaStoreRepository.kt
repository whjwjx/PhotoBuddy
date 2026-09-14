package com.example.photoorganizer.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
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

    /** 全量或按时间增量查询（sinceSec=0 表示查全部）。 */
    suspend fun loadSince(sinceSec: Long = 0L): List<MediaAsset> = withContext(Dispatchers.IO) {
        buildList {
            addAll(query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaType.IMAGE, sinceSec))
            addAll(query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaType.VIDEO, sinceSec))
        }
    }

    /**
     * 分批产出媒体（PRD 4.1：扫描期间先展示已扫描部分）。
     * 每积累 batchSize 条就回调一次，调用方可即时入库，让界面边扫边显示。
     */
    suspend fun loadSinceBatched(
        sinceSec: Long = 0L,
        batchSize: Int = 300,
        onBatch: suspend (List<MediaAsset>, Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        var total = 0
        val buffer = mutableListOf<MediaAsset>()
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI to MediaType.IMAGE,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to MediaType.VIDEO,
        ).forEach { (base, type) ->
            val (selection, args) = selectionFor(sinceSec)
            runCatching {
                resolver
                    .query(base, projectionFor(type), selection, args, SORT)
                    ?.use { cursor ->
                        val cols = Columns(cursor, type)
                        while (cursor.moveToNext()) {
                            buffer += cols.read(cursor, base, type)
                            if (buffer.size >= batchSize) {
                                total += buffer.size
                                onBatch(buffer.toList(), total)
                                buffer.clear()
                            }
                        }
                    }
            }
        }
        if (buffer.isNotEmpty()) {
            total += buffer.size
            onBatch(buffer.toList(), total)
        }
    }

    /** 只查 ID，用于比对出「已被删除」的媒体。 */
    suspend fun loadAllIds(): Set<Long> = withContext(Dispatchers.IO) {
        val ids = mutableSetOf<Long>()
        listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        ).forEach { base ->
            runCatching {
                resolver
                    .query(base, arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                    ?.use { cursor ->
                        val idx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        while (cursor.moveToNext()) ids += cursor.getLong(idx)
                    }
            }
        }
        ids
    }

    private suspend fun query(
        baseUri: Uri,
        type: MediaType,
        sinceSec: Long,
    ): List<MediaAsset> = withContext(Dispatchers.IO) {
        val (selection, args) = selectionFor(sinceSec)
        val out = mutableListOf<MediaAsset>()
        runCatching {
            resolver
                .query(baseUri, projectionFor(type), selection, args, SORT)
                ?.use { cursor ->
                    val cols = Columns(cursor, type)
                    while (cursor.moveToNext()) out += cols.read(cursor, baseUri, type)
                }
        }
        out
    }

    private fun selectionFor(sinceSec: Long): Pair<String?, Array<String>?> =
        if (sinceSec > 0) {
            "${MediaStore.MediaColumns.DATE_ADDED} > ?" to arrayOf(sinceSec.toString())
        } else {
            null to null
        }

    private fun projectionFor(type: MediaType): Array<String> =
        buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.BUCKET_ID)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.IS_FAVORITE)
            }
            if (type == MediaType.VIDEO) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()

    /** 游标列索引缓存 + 行解析。 */
    private class Columns(
        cursor: Cursor,
        type: MediaType,
    ) {
        private val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        private val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        private val mime = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        private val size = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        private val w = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        private val h = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        private val taken = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        private val added = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        private val bucketId = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
        private val bucketName = cursor.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        private val fav =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
            } else {
                -1
            }
        private val duration =
            if (type == MediaType.VIDEO) cursor.getColumnIndex(MediaStore.Video.Media.DURATION) else -1

        fun read(
            cursor: Cursor,
            baseUri: Uri,
            type: MediaType,
        ): MediaAsset {
            val idValue = cursor.getLong(id)
            return MediaAsset(
                id = idValue,
                uri = ContentUris.withAppendedId(baseUri, idValue),
                displayName = cursor.getString(name) ?: "",
                mimeType = if (mime >= 0) cursor.getString(mime) ?: "" else "",
                size = if (size >= 0) cursor.getLong(size) else 0L,
                width = if (w >= 0) cursor.getInt(w) else 0,
                height = if (h >= 0) cursor.getInt(h) else 0,
                durationMs = if (duration >= 0) cursor.getLong(duration) else 0L,
                capturedAt = if (taken >= 0) cursor.getLong(taken) else 0L,
                bucketId = if (bucketId >= 0) cursor.getString(bucketId) ?: "" else "",
                bucketName = if (bucketName >= 0) cursor.getString(bucketName) ?: "" else "",
                isFavorite = if (fav >= 0) cursor.getInt(fav) != 0 else false,
                mediaType = type,
                dateAdded = if (added >= 0) cursor.getLong(added) else 0L,
            )
        }
    }

    private companion object {
        const val SORT = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"
    }
}
