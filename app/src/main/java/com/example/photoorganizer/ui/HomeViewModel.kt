package com.example.photoorganizer.ui

import android.app.Application
import android.content.IntentSender
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaStoreRepository
import com.example.photoorganizer.data.MediaType
import com.example.photoorganizer.data.OrganizeSettings
import com.example.photoorganizer.data.SettingsRepository
import com.example.photoorganizer.data.local.AlbumEntity
import com.example.photoorganizer.data.local.AlbumItemEntity
import com.example.photoorganizer.data.local.AppDatabase
import com.example.photoorganizer.data.local.MediaStatus
import com.example.photoorganizer.data.local.MediaStatusEntity
import com.example.photoorganizer.domain.DeleteCoordinator
import com.example.photoorganizer.domain.MediaQueue
import com.example.photoorganizer.domain.QueueEngine
import com.example.photoorganizer.domain.QueueType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DAY_MS = 24L * 60 * 60 * 1000

data class HomeUiState(
    val isScanning: Boolean = false,
    /** 扫描到的全部媒体（未过滤）。 */
    val allAssets: List<MediaAsset> = emptyList(),
    /** 按相册/类型过滤后的工作集，统计基于此。 */
    val assets: List<MediaAsset> = emptyList(),
    val statuses: List<MediaStatusEntity> = emptyList(),
    val queues: List<MediaQueue> = emptyList(),
    val queueType: QueueType = QueueType.RANDOM,
    val queueTitle: String = "",
    /** 当前队列的卡片列表，卡片流在其上推进。 */
    val queueItems: List<MediaAsset> = emptyList(),
    val filterType: MediaType? = null,
    val filterBucket: String? = null,
    val currentIndex: Int = 0,
    val processedCount: Int = 0,
    val freedBytes: Long = 0,
    val error: String? = null,
    /** API 30+ 待启动的系统删除确认 IntentSender（单个）。 */
    val pendingDelete: IntentSender? = null,
    // --- P3 ---
    val settings: OrganizeSettings = OrganizeSettings(),
    val albums: List<AlbumEntity> = emptyList(),
    val albumCounts: Map<Long, Int> = emptyMap(),
    val openAlbumId: Long? = null,
    val openAlbumMediaIds: List<Long> = emptyList(),
    val showBatch: Boolean = false,
    /** 批量确认候选（已按安全策略剔除高风险项）。 */
    val batchCandidates: List<MediaAsset> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val batchIdsInFlight: Set<Long> = emptySet(),
    val pendingBatchDelete: IntentSender? = null,
) {
    val current: MediaAsset? get() = queueItems.getOrNull(currentIndex)
    val remaining: Int get() = (queueItems.size - currentIndex).coerceAtLeast(0)

    /** 安全策略命中的高风险项（收藏 / 最近拍摄），默认不进入批量删除。 */
    val highRiskCount: Int get() = queueItems.count { isProtected(it) }

    fun isProtected(asset: MediaAsset): Boolean =
        (settings.protectFavorite && asset.isFavorite) ||
            (
                settings.protectRecentDays > 0 &&
                    asset.capturedAt > 0 &&
                    asset.capturedAt > System.currentTimeMillis() - settings.protectRecentDays * DAY_MS
            )

    val selectedBytes: Long get() = batchCandidates.filter { it.id in selectedIds }.sumOf { it.size }
}

class HomeViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = MediaStoreRepository(app.contentResolver)
    private val dao = AppDatabase.getDatabase(app).mediaStatusDao()
    private val albumDao = AppDatabase.getDatabase(app).albumDao()
    private val coordinator = DeleteCoordinator(app.contentResolver)
    private val settingsRepo = SettingsRepository(app)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { list -> _uiState.update { s -> recompute(s.copy(statuses = list)) } }
        }
        viewModelScope.launch {
            albumDao.observeAlbums().collect { list -> _uiState.update { it.copy(albums = list) } }
        }
        viewModelScope.launch {
            albumDao.observeCounts().collect { counts ->
                _uiState.update { it.copy(albumCounts = counts.associate { c -> c.albumId to c.count }) }
            }
        }
        viewModelScope.launch {
            settingsRepo.settings.collect { s -> _uiState.update { it.copy(settings = s) } }
        }
    }

    fun scan() {
        _uiState.update {
            it.copy(isScanning = true, error = null, currentIndex = 0, processedCount = 0, freedBytes = 0)
        }
        viewModelScope.launch {
            runCatching { repo.loadAll() }
                .onSuccess { assets ->
                    val shuffled = assets.shuffled()
                    _uiState.update { s ->
                        recompute(s.copy(isScanning = false, allAssets = shuffled, currentIndex = 0))
                    }
                }.onFailure { e ->
                    _uiState.update { it.copy(isScanning = false, error = e.message) }
                }
        }
    }

    /** 按媒体类型和相册筛选整理范围（PRD 5.1）。 */
    fun setFilter(
        type: MediaType?,
        bucket: String?,
    ) {
        _uiState.update { s ->
            recompute(s.copy(filterType = type, filterBucket = bucket, currentIndex = 0))
        }
    }

    fun selectQueue(queue: MediaQueue) {
        _uiState.update { s ->
            recompute(
                s.copy(
                    queueType = queue.type,
                    queueTitle = queue.title,
                    currentIndex = 0,
                ),
            )
        }
    }

    /** 用户对当前卡片的决策（PRD 4.2 / 4.5）。删除走二次确认，见 [requestDelete]。 */
    fun act(status: MediaStatus) {
        if (status == MediaStatus.DELETE) {
            requestDelete()
            return
        }
        val asset = _uiState.value.current ?: return
        viewModelScope.launch { finishAction(asset, status) }
    }

    /**
     * 删除入口（App 自己的确认弹窗之后调用）。
     * API 30+：发起系统删除确认（MediaStore.createDeleteRequest），由系统完成删除；
     * API 29-：直接删除。
     */
    fun requestDelete() {
        val asset = _uiState.value.current ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = coordinator.createDeleteRequest(listOf(asset.uri))?.intentSender
            if (sender == null) {
                _uiState.update { it.copy(error = "无法发起系统删除确认") }
            } else {
                _uiState.update { it.copy(pendingDelete = sender) }
            }
        } else {
            viewModelScope.launch {
                val ok = coordinator.deleteToTrash(asset)
                if (!ok) {
                    _uiState.update { it.copy(error = "删除失败：${asset.displayName}") }
                    return@launch
                }
                finishAction(asset, MediaStatus.DELETE)
            }
        }
    }

    /** 系统删除确认弹窗用户点「允许」后回调（API 30+，系统已完成删除并移入最近删除）。 */
    fun onDeleteApproved() {
        val asset = _uiState.value.current ?: return
        viewModelScope.launch { finishAction(asset, MediaStatus.DELETE) }
    }

    fun clearPendingDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    // ---------------- P3：应用内相册 ----------------

    fun createAlbum(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            albumDao.insertAlbum(AlbumEntity(name = trimmed, createdAt = System.currentTimeMillis()))
        }
    }

    /** 把当前卡片加入指定相册（PRD 4.2 加入相册）。 */
    fun addCurrentToAlbum(albumId: Long) {
        val asset = _uiState.value.current ?: return
        viewModelScope.launch {
            albumDao.addItem(
                AlbumItemEntity(albumId = albumId, mediaId = asset.id, addedAt = System.currentTimeMillis()),
            )
        }
    }

    fun openAlbum(albumId: Long) {
        viewModelScope.launch {
            val ids = albumDao.getItems(albumId).map { it.mediaId }
            _uiState.update { it.copy(openAlbumId = albumId, openAlbumMediaIds = ids) }
        }
    }

    fun closeAlbum() {
        _uiState.update { it.copy(openAlbumId = null, openAlbumMediaIds = emptyList()) }
    }

    fun removeFromAlbum(
        albumId: Long,
        mediaId: Long,
    ) {
        viewModelScope.launch {
            albumDao.removeItem(albumId, mediaId)
            if (_uiState.value.openAlbumId == albumId) openAlbum(albumId)
        }
    }

    // ---------------- P3：批量确认 ----------------

    fun enterBatch() {
        val s = _uiState.value
        val candidates = s.queueItems.filter { !s.isProtected(it) }
        _uiState.update {
            it.copy(
                showBatch = true,
                batchCandidates = candidates,
                selectedIds = candidates.map { a -> a.id }.toSet(),
            )
        }
    }

    fun exitBatch() {
        _uiState.update { it.copy(showBatch = false, selectedIds = emptySet(), batchCandidates = emptyList()) }
    }

    fun toggleSelect(id: Long) {
        _uiState.update { s ->
            val next = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id
            s.copy(selectedIds = next)
        }
    }

    fun selectAllBatch(on: Boolean) {
        _uiState.update { s ->
            s.copy(selectedIds = if (on) s.batchCandidates.map { it.id }.toSet() else emptySet())
        }
    }

    /** 发起批量删除：按 [OrganizeSettings.batchChunkSize] 分批，避免系统 URI 数量上限。 */
    fun confirmBatch() {
        val s = _uiState.value
        val chunk = s.settings.batchChunkSize.coerceAtLeast(1)
        val ids = s.selectedIds.toList().take(chunk)
        if (ids.isEmpty()) return
        val byId = s.allAssets.associateBy { it.id }
        val uris = ids.mapNotNull { byId[it]?.uri }
        if (uris.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender = coordinator.createDeleteRequest(uris)?.intentSender
            if (sender == null) {
                _uiState.update { it.copy(error = "无法发起系统删除确认") }
            } else {
                _uiState.update { it.copy(pendingBatchDelete = sender, batchIdsInFlight = ids.toSet()) }
            }
        } else {
            viewModelScope.launch {
                val okIds = ids.filter { id -> byId[id]?.let { coordinator.deleteToTrash(it) } == true }
                finishBatch(okIds.toSet())
            }
        }
    }

    fun onBatchApproved() {
        val ids = _uiState.value.batchIdsInFlight
        if (ids.isEmpty()) return
        viewModelScope.launch { finishBatch(ids) }
    }

    fun clearPendingBatch() {
        _uiState.update { it.copy(pendingBatchDelete = null, batchIdsInFlight = emptySet()) }
    }

    // ---------------- P3：设置 ----------------

    fun setProtectFavorite(v: Boolean) {
        viewModelScope.launch { settingsRepo.setProtectFavorite(v) }
    }

    fun setProtectRecentDays(v: Int) {
        viewModelScope.launch { settingsRepo.setProtectRecentDays(v) }
    }

    fun setBatchChunk(v: Int) {
        viewModelScope.launch { settingsRepo.setBatchChunk(v) }
    }

    // ---------------- 内部 ----------------

    /** 依据「全部媒体 + 筛选条件 + 已处理集合」重算工作集、队列和当前卡片列表。 */
    private fun recompute(s: HomeUiState): HomeUiState {
        val filtered =
            s.allAssets.filter { asset ->
                (s.filterType == null || asset.mediaType == s.filterType) &&
                    (s.filterBucket == null || asset.bucketId == s.filterBucket)
            }
        val processedIds = s.statuses.map { it.localAssetId }.toSet()
        val queues = QueueEngine.build(filtered, processedIds)
        val picked =
            queues.firstOrNull { it.type == s.queueType && it.title == s.queueTitle }
                ?: queues.firstOrNull { it.type == s.queueType }
                ?: queues.firstOrNull { it.type == QueueType.RANDOM }
        val items = picked?.items.orEmpty()
        return s.copy(
            assets = filtered,
            queues = queues,
            queueTitle = picked?.title.orEmpty(),
            queueItems = items,
            currentIndex = s.currentIndex.coerceIn(0, items.size),
        )
    }

    private suspend fun finishAction(
        asset: MediaAsset,
        status: MediaStatus,
    ) {
        dao.upsert(toEntity(asset, status))
        _uiState.update { s ->
            val isDelete = status == MediaStatus.DELETE
            // 删除后立即移出内存列表，避免已进回收站的照片仍出现在卡片流和统计里。
            val base =
                if (isDelete) s.copy(allAssets = s.allAssets.filter { it.id != asset.id }) else s
            // 「未整理」队列处理完会被移出、删除项也会被移出，下一张自动补位，因此不推进索引。
            val advance = if (base.queueType == QueueType.UNPROCESSED || isDelete) 0 else 1
            recompute(
                base.copy(
                    freedBytes = base.freedBytes + if (isDelete) asset.size else 0L,
                    currentIndex = (base.currentIndex + advance).coerceAtMost(base.queueItems.size),
                    processedCount = base.processedCount + 1,
                ),
            )
        }
    }

    private suspend fun finishBatch(ids: Set<Long>) {
        val byId = _uiState.value.allAssets.associateBy { it.id }
        var freed = 0L
        ids.forEach { id ->
            byId[id]?.let { asset ->
                dao.upsert(toEntity(asset, MediaStatus.DELETE))
                freed += asset.size
            }
        }
        _uiState.update { s ->
            recompute(
                s.copy(
                    allAssets = s.allAssets.filter { it.id !in ids },
                    freedBytes = s.freedBytes + freed,
                    processedCount = s.processedCount + ids.size,
                    selectedIds = s.selectedIds - ids,
                batchIdsInFlight = emptySet(),
                pendingBatchDelete = null,
                showBatch = false,
                currentIndex = 0,
                ),
            )
        }
    }

    private fun toEntity(
        asset: MediaAsset,
        status: MediaStatus,
    ) = MediaStatusEntity(
        localAssetId = asset.id,
        mediaType = asset.mediaType.name,
        status = status.value,
        updatedAt = System.currentTimeMillis(),
    )
}
