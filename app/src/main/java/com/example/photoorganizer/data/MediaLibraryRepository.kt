package com.example.photoorganizer.data

import com.example.photoorganizer.data.local.MediaIndexDao
import com.example.photoorganizer.data.local.toAsset
import com.example.photoorganizer.data.local.toIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 媒体库：以 Room 索引为准，MediaStore 只用于同步（PRD 8.2.1 增量扫描）。
 *
 * 好处：App 启动直接从 Room 读，不必每次全量查 MediaStore；
 * 后台可只同步上次扫描之后的变化，也能发现「已被删除」的媒体。
 */
class MediaLibraryRepository(
    private val mediaStore: MediaStoreRepository,
    private val indexDao: MediaIndexDao,
    private val settings: SettingsRepository,
) {
    fun observeAssets(): Flow<List<MediaAsset>> =
        indexDao.observeAll().map { list -> list.map { it.toAsset() } }

    /**
     * 同步媒体库。
     * @param full true=全量重建（sinceSec=0 即查全部）；false=只同步上次扫描之后新增的部分。
     * @param onProgress 每入库一批回调一次已扫描条数，用于界面边扫边显示（PRD 4.1）。
     * @return 本次扫描到的总条数。
     */
    suspend fun sync(
        full: Boolean = false,
        onProgress: ((scanned: Int) -> Unit)? = null,
    ): Int {
        val sinceSec = if (full) 0L else settings.getLastScanMs() / 1000
        if (full) {
            indexDao.clear()
        }
        var total = 0
        mediaStore.loadSinceBatched(sinceSec) { batch, count ->
            indexDao.upsertAll(batch.map { it.toIndex() })
            total = count
            onProgress?.invoke(count)
        }

        // 删除检测：索引里有、但 MediaStore 已查不到的（含被移入系统最近删除的）
        val currentIds = mediaStore.loadAllIds()
        val removed = indexDao.allIds().toSet() - currentIds
        if (removed.isNotEmpty()) indexDao.deleteByIds(removed.toList())

        settings.setLastScanMs(System.currentTimeMillis())
        return total
    }
}
