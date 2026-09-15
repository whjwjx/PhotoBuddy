package com.example.photoorganizer.ui

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoorganizer.data.DailyProgress
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaLibraryRepository
import com.example.photoorganizer.data.MediaStoreRepository
import com.example.photoorganizer.data.MediaType
import com.example.photoorganizer.data.OrganizeSettings
import com.example.photoorganizer.data.SettingsRepository
import com.example.photoorganizer.data.local.AlbumEntity
import com.example.photoorganizer.data.local.AlbumItemEntity
import com.example.photoorganizer.data.local.AppDatabase
import com.example.photoorganizer.data.local.MediaStatus
import com.example.photoorganizer.data.local.MediaStatusEntity
import com.example.photoorganizer.data.local.UserActionLogEntity
import com.example.photoorganizer.domain.DeleteCoordinator
import com.example.photoorganizer.domain.MediaQueue
import com.example.photoorganizer.domain.QueueEngine
import com.example.photoorganizer.domain.QueueType
import com.example.photoorganizer.worker.ScanWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val UNDO_VISIBLE_MS = 6_000L

data class UndoState(
    val mediaId: Long,
    val mediaName: String,
    val mediaType: String,
    val beforeStatus: String?,
    val albumId: Long? = null,
    val albumItemAdded: Boolean = false,
    val message: String,
    val createdAt: Long,
)

data class HomeUiState(
    val isScanning: Boolean = false,
    /** 本地媒体索引（来自 Room），App 启动即可用。 */
    val allAssets: List<MediaAsset> = emptyList(),
    /** 按相册/类型过滤后的工作集，统计基于此。 */
    val assets: List<MediaAsset> = emptyList(),
    val statuses: List<MediaStatusEntity> = emptyList(),
    val queues: List<MediaQueue> = emptyList(),
    val queueType: QueueType = QueueType.UNPROCESSED,
    val queueTitle: String = "",
    /** 当前队列的卡片列表，卡片流在其上推进。 */
    val queueItems: List<MediaAsset> = emptyList(),
    val filterType: MediaType? = null,
    val filterBucket: String? = null,
    val currentIndex: Int = 0,
    val processedCount: Int = 0,
    val freedBytes: Long = 0,
    val error: String? = null,
    /** API 30+ 待启动的系统删除确认 IntentSender（待删除页批量确认）。 */
    val pendingDelete: IntentSender? = null,
    val trashDeleteIdsInFlight: Set<Long> = emptySet(),
    val settings: OrganizeSettings = OrganizeSettings(),
    val albums: List<AlbumEntity> = emptyList(),
    val albumCounts: Map<Long, Int> = emptyMap(),
    val albumMediaIds: Map<Long, List<Long>> = emptyMap(),
    val albumLastAddedAt: Map<Long, Long> = emptyMap(),
    val pinnedAlbumIds: Set<Long> = emptySet(),
    val hiddenAlbumIds: Set<Long> = emptySet(),
    val openAlbumId: Long? = null,
    val openAlbumMediaIds: List<Long> = emptyList(),
    // --- P4：增量扫描 / 每日整理任务 ---
    val lastScanMs: Long = 0L,
    val lastSyncAdded: Int = 0,
    val daily: DailyProgress = DailyProgress("", 0),
    /** 扫描进度：已扫描条数，用于边扫边展示（PRD 4.1）。 */
    val scanProgress: Int = 0,
    /** 最近处理记录（PRD 六·首页 / 9.5 操作日志）。 */
    val recentLogs: List<UserActionLogEntity> = emptyList(),
    /** 是否为 Android 14+ 的「部分照片访问」模式。 */
    val partialAccess: Boolean = false,
    /** 本 App 删除、仍可恢复的项（App 内「最近删除」）。 */
    val deletedLogs: List<UserActionLogEntity> = emptyList(),
    val pendingRestore: IntentSender? = null,
    val restoringLogId: Long? = null,
    val undo: UndoState? = null,
    val feedbackMessage: String? = null,
) {
    val current: MediaAsset? get() = queueItems.getOrNull(currentIndex)
    val remaining: Int get() = (queueItems.size - currentIndex).coerceAtLeast(0)
    val statusById: Map<Long, String> get() = statuses.associate { it.localAssetId to it.status }
    val unprocessedCount: Int get() = allAssets.count { it.id !in statusById }
    val trashItems: List<MediaAsset>
        get() = allAssets.filter { statusById[it.id] == MediaStatus.TRASH.value }
    val trashCount: Int get() = trashItems.size
    val trashBytes: Long get() = trashItems.sumOf { it.size }
    val organizedCount: Int
        get() =
            statuses.count {
                it.status != MediaStatus.TRASH.value && it.status != MediaStatus.DELETE.value
            }

    /** 今日任务是否完成。 */
    val dailyDone: Boolean get() = daily.count >= settings.dailyGoal && settings.dailyGoal > 0

    /** 当前队列来源描述，写入操作日志（PRD 9.5 source）。 */
    val queueSource: String
        get() = queueType.label + (if (queueTitle.isNotEmpty()) " · $queueTitle" else "")
}

class HomeViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val db = AppDatabase.getDatabase(app)
    private val dao = db.mediaStatusDao()
    private val albumDao = db.albumDao()
    private val indexDao = db.mediaIndexDao()
    private val logDao = db.userActionLogDao()
    private val coordinator = DeleteCoordinator(app.contentResolver)
    private val settingsRepo = SettingsRepository(app)
    private val library =
        MediaLibraryRepository(
            MediaStoreRepository(app.contentResolver),
            indexDao,
            settingsRepo,
        )

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // 媒体索引：App 启动直接从 Room 读，不用等 MediaStore 全量查询
        viewModelScope.launch {
            library.observeAssets().collect { assets ->
                _uiState.update { s -> recompute(s.copy(allAssets = assets.sortedBy { shuffleKey(it.id) })) }
            }
        }
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
            albumDao.observeAllItems().collect { items ->
                _uiState.update {
                    it.copy(
                        albumMediaIds = items.groupBy { item -> item.albumId }.mapValues { entry ->
                            entry.value.map { item -> item.mediaId }
                        },
                        albumLastAddedAt = items.groupBy { item -> item.albumId }.mapValues { entry ->
                            entry.value.maxOfOrNull { item -> item.addedAt } ?: 0L
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepo.settings.collect { s -> _uiState.update { it.copy(settings = s) } }
        }
        viewModelScope.launch {
            settingsRepo.daily.collect { d -> _uiState.update { it.copy(daily = d) } }
        }
        viewModelScope.launch {
            settingsRepo.pinnedAlbumIds.collect { ids -> _uiState.update { it.copy(pinnedAlbumIds = ids) } }
        }
        viewModelScope.launch {
            settingsRepo.hiddenAlbumIds.collect { ids -> _uiState.update { it.copy(hiddenAlbumIds = ids) } }
        }
        viewModelScope.launch {
            logDao.observeRecent(20).collect { list -> _uiState.update { it.copy(recentLogs = list) } }
        }
        viewModelScope.launch {
            logDao.observeDeleted(20).collect { list -> _uiState.update { it.copy(deletedLogs = list) } }
        }

        // Android 14+「部分照片访问」检测：这种情况 App 只能看到用户勾选的少量照片，
        // 若不给提示，用户会误以为扫描坏了（PRD 8.2.1 要求覆盖该场景）。
        _uiState.update {
            it.copy(partialAccess = hasPartialMediaAccess(app))
        }

        // 后台增量扫描：首次为空则全量，否则增量
        viewModelScope.launch {
            ScanWorker.enqueuePeriodic(app)
            if (indexDao.count() == 0) {
                scan(full = true)
            } else {
                ScanWorker.enqueueOnce(app)
                _uiState.update { it.copy(lastScanMs = settingsRepo.getLastScanMs()) }
            }
        }
    }

    /**
     * 同步媒体库。
     * @param full true=全量重建；false=只同步上次扫描之后新增/删除的部分。
     */
    fun scan(full: Boolean = false) {
        _uiState.update {
            it.copy(
                isScanning = true,
                error = null,
                scanProgress = 0,
                currentIndex = 0,
                processedCount = 0,
                freedBytes = 0,
            )
        }
        viewModelScope.launch {
            runCatching {
                library.sync(full) { scanned -> _uiState.update { it.copy(scanProgress = scanned) } }
            }
                .onSuccess { added ->
                    _uiState.update { s ->
                        recompute(
                            s.copy(
                                isScanning = false,
                                currentIndex = 0,
                                lastScanMs = System.currentTimeMillis(),
                                lastSyncAdded = added,
                            ),
                        )
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

    /** 刷照片流滑动到某一页时同步索引，保证操作作用于当前可见的卡片。 */
    fun setIndex(index: Int) {
        _uiState.update { s ->
            val safeIndex = if (s.queueItems.isEmpty()) 0 else index.coerceIn(0, s.queueItems.lastIndex)
            if (s.currentIndex == safeIndex) s else s.copy(currentIndex = safeIndex)
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

    /** 用户对当前卡片的决策。删除在 Slidebox 模型里只进入 App 内待删除区。 */
    fun act(status: MediaStatus) {
        val nextStatus = if (status == MediaStatus.DELETE) MediaStatus.TRASH else status
        val asset = _uiState.value.current ?: return
        viewModelScope.launch { finishAction(asset, nextStatus, actionMessage(nextStatus)) }
    }

    /**
     * 兼容旧入口：主整理流里的删除只标记为待删除，不直接请求系统删除。
     */
    fun requestDelete() {
        act(MediaStatus.TRASH)
    }

    fun deferCurrentSimilarGroup() {
        val s = _uiState.value
        val current = s.current ?: return
        if (s.queueType != QueueType.SIMILAR) return
        val targets = s.queueItems.filter { QueueEngine.isSimilarGroupPeer(current, it) }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            targets.forEach { asset ->
                val before = dao.get(asset.id)?.status.orEmpty()
                dao.upsert(toEntity(asset, MediaStatus.LATER))
                logDao.insert(
                    UserActionLogEntity(
                        mediaId = asset.id,
                        mediaName = asset.displayName,
                        mediaType = asset.mediaType.name,
                        action = MediaStatus.LATER.value,
                        source = s.queueSource,
                        beforeState = before,
                        afterState = MediaStatus.LATER.value,
                        freedBytes = 0L,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
            settingsRepo.addProcessed(targets.size)
            _uiState.update { state ->
                recompute(
                    state.copy(
                        processedCount = state.processedCount + targets.size,
                        undo = null,
                    ),
                )
            }
        }
    }

    /** 待删除页发起真实删除：优先移入系统回收站，失败时才使用系统永久删除请求兜底。 */
    fun requestDeleteTrash(ids: Set<Long>) {
        val s = _uiState.value
        val selectedIds = ids.ifEmpty { s.trashItems.map { it.id }.toSet() }
        val byId = s.trashItems.associateBy { it.id }
        val assets = selectedIds.mapNotNull { byId[it] }
        if (assets.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = assets.map { it.uri }
            val sender =
                (coordinator.createTrashRequest(uris) ?: coordinator.createDeleteRequest(uris))
                    ?.intentSender
            if (sender == null) {
                _uiState.update { it.copy(error = "无法发起系统删除确认") }
            } else {
                _uiState.update {
                    it.copy(
                        pendingDelete = sender,
                        trashDeleteIdsInFlight = assets.map { asset -> asset.id }.toSet(),
                    )
                }
            }
        } else {
            viewModelScope.launch {
                val okIds = assets.filter { coordinator.deleteToTrash(it) }.map { it.id }.toSet()
                if (okIds.isEmpty()) {
                    _uiState.update { it.copy(error = "删除失败，系统未允许移除这些照片") }
                    return@launch
                }
                finishTrashDelete(okIds)
            }
        }
    }

    /** 系统删除确认弹窗用户点「允许」后回调（API 30+，系统已完成删除或移入最近删除）。 */
    fun onDeleteApproved() {
        val ids = _uiState.value.trashDeleteIdsInFlight
        if (ids.isEmpty()) return
        viewModelScope.launch { finishTrashDelete(ids) }
    }

    fun clearPendingDelete() {
        _uiState.update { it.copy(pendingDelete = null, trashDeleteIdsInFlight = emptySet()) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** 从待删除恢复到未整理队列。 */
    fun restoreFromTrash(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val byId = _uiState.value.trashItems.associateBy { it.id }
        viewModelScope.launch {
            dao.deleteByIds(ids.toList())
            ids.forEach { id ->
                byId[id]?.let { asset ->
                    logDao.insert(
                        UserActionLogEntity(
                            mediaId = asset.id,
                            mediaName = asset.displayName,
                            mediaType = asset.mediaType.name,
                            action = "restore",
                            source = "待删除",
                            beforeState = MediaStatus.TRASH.value,
                            afterState = "",
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            settingsRepo.addProcessed(-ids.size)
            _uiState.update { s -> recompute(s.copy(undo = null, feedbackMessage = null)) }
        }
    }

    fun undoLast() {
        val undo = _uiState.value.undo ?: return
        val asset = _uiState.value.allAssets.firstOrNull { it.id == undo.mediaId } ?: return
        viewModelScope.launch {
            val restoredStatus =
                undo.beforeStatus
                    ?.takeIf { it.isNotEmpty() }
                    ?.let {
                        MediaStatusEntity(
                            localAssetId = undo.mediaId,
                            mediaType = undo.mediaType,
                            status = it,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
            if (restoredStatus == null) {
                dao.deleteByIds(listOf(undo.mediaId))
            } else {
                dao.upsert(restoredStatus)
            }
            if (undo.albumId != null && undo.albumItemAdded) {
                albumDao.removeItem(undo.albumId, undo.mediaId)
            }
            logDao.insert(
                UserActionLogEntity(
                    mediaId = asset.id,
                    mediaName = asset.displayName,
                    mediaType = asset.mediaType.name,
                    action = "undo",
                    source = _uiState.value.queueSource,
                    beforeState = _uiState.value.statusById[asset.id].orEmpty(),
                    afterState = restoredStatus?.status.orEmpty(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            settingsRepo.addProcessed(-1)
            _uiState.update { s ->
                val statuses =
                    if (restoredStatus == null) {
                        s.statuses.filterNot { it.localAssetId == undo.mediaId }
                    } else {
                        s.statuses.filterNot { it.localAssetId == undo.mediaId } + restoredStatus
                    }
                val updated =
                    recompute(
                        s.copy(
                            statuses = statuses,
                            processedCount = (s.processedCount - 1).coerceAtLeast(0),
                            undo = null,
                            feedbackMessage = null,
                        ),
                    )
                val restoredIndex = updated.queueItems.indexOfFirst { it.id == undo.mediaId }
                if (restoredIndex >= 0) {
                    updated.copy(currentIndex = restoredIndex)
                } else {
                    updated
                }
            }
        }
    }

    // ---------------- 应用内相册 ----------------

    fun createAlbum(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            albumDao.insertAlbum(AlbumEntity(name = trimmed, createdAt = System.currentTimeMillis()))
        }
    }

    fun renameAlbum(
        albumId: Long,
        name: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            albumDao.renameAlbum(albumId, trimmed)
        }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            albumDao.deleteAlbumItems(albumId)
            albumDao.deleteAlbum(albumId)
            settingsRepo.setAlbumPinned(albumId, false)
            settingsRepo.setAlbumHidden(albumId, false)
            _uiState.update { s ->
                if (s.openAlbumId == albumId) {
                    s.copy(openAlbumId = null, openAlbumMediaIds = emptyList())
                } else {
                    s
                }
            }
        }
    }

    fun mergeAlbum(
        sourceAlbumId: Long,
        targetAlbumId: Long,
    ) {
        if (sourceAlbumId == targetAlbumId) return
        viewModelScope.launch {
            val sourceItems = albumDao.getItems(sourceAlbumId)
            sourceItems.forEach { item ->
                albumDao.addItem(
                    AlbumItemEntity(
                        albumId = targetAlbumId,
                        mediaId = item.mediaId,
                        addedAt = item.addedAt,
                    ),
                )
            }
            albumDao.deleteAlbumItems(sourceAlbumId)
            albumDao.deleteAlbum(sourceAlbumId)
            settingsRepo.setAlbumPinned(sourceAlbumId, false)
            settingsRepo.setAlbumHidden(sourceAlbumId, false)
            val targetIds = albumDao.getItems(targetAlbumId).map { it.mediaId }
            _uiState.update { s ->
                if (s.openAlbumId == sourceAlbumId) {
                    s.copy(openAlbumId = targetAlbumId, openAlbumMediaIds = targetIds)
                } else {
                    s
                }
            }
        }
    }

    fun setAlbumPinned(
        albumId: Long,
        pinned: Boolean,
    ) {
        viewModelScope.launch {
            settingsRepo.setAlbumPinned(albumId, pinned)
        }
    }

    fun setAlbumHidden(
        albumId: Long,
        hidden: Boolean,
    ) {
        viewModelScope.launch {
            settingsRepo.setAlbumHidden(albumId, hidden)
        }
    }

    /** 新建相册后立刻把当前卡片归入该相册，保持刷卡流不中断。 */
    fun createAlbumAndAddCurrent(name: String) {
        val asset = _uiState.value.current ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val albumId = albumDao.insertAlbum(AlbumEntity(name = trimmed, createdAt = System.currentTimeMillis()))
            albumDao.addItem(
                AlbumItemEntity(albumId = albumId, mediaId = asset.id, addedAt = System.currentTimeMillis()),
            )
            finishAction(
                asset = asset,
                status = MediaStatus.ALBUM,
                message = "已新建并加入「$trimmed」",
                undoAlbumId = albumId,
                undoAlbumItemAdded = true,
            )
        }
    }

    /** 把当前卡片加入相册。刷卡流只做 App 内归类，避免系统写入弹窗打断连续整理。 */
    fun addCurrentToAlbum(albumId: Long) {
        val asset = _uiState.value.current ?: return
        val albumName = _uiState.value.albums.firstOrNull { it.id == albumId }?.name ?: "相册"
        viewModelScope.launch {
            val existed = albumDao.itemCount(albumId, asset.id) > 0
            albumDao.addItem(
                AlbumItemEntity(albumId = albumId, mediaId = asset.id, addedAt = System.currentTimeMillis()),
            )
            finishAction(
                asset = asset,
                status = MediaStatus.ALBUM,
                message = "已加入「$albumName」",
                undoAlbumId = albumId,
                undoAlbumItemAdded = !existed,
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

    // ---------------- 恢复（App 内「最近删除」） ----------------

    /**
     * 从系统回收站恢复。
     * 厂商相册（如华为「最近删除」）是 App 私有实现，第三方无法写入；
     * 因此这里自己维护一份可恢复列表，用 createTrashRequest(trashed=false) 走系统恢复。
     */
    fun restore(log: UserActionLogEntity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            _uiState.update { it.copy(error = "该系统版本不支持从回收站恢复") }
            return
        }
        val uri =
            if (log.mediaType == MediaType.VIDEO.name) {
                ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, log.mediaId)
            } else {
                ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, log.mediaId)
            }
        val sender = coordinator.createRestoreRequest(listOf(uri))?.intentSender
        if (sender == null) {
            _uiState.update { it.copy(error = "无法恢复《${log.mediaName}》，可能已被彻底清除") }
        } else {
            _uiState.update { it.copy(pendingRestore = sender, restoringLogId = log.id) }
        }
    }

    /**
     * 系统恢复确认通过：重建索引并**实际校验**照片是否回来了。
     * 只有真的回到媒体库才标记为已恢复，否则明确告知用户（避免"假装恢复成功"）。
     */
    fun onRestoreApproved() {
        val id = _uiState.value.restoringLogId ?: return
        val log = _uiState.value.deletedLogs.firstOrNull { it.id == id }
        viewModelScope.launch {
            runCatching { library.sync(full = true) }
            val back = log != null && indexDao.allIds().contains(log.mediaId)
            if (back) {
                logDao.updateAction(id, "restore")
            } else {
                _uiState.update {
                    it.copy(error = "《${log?.mediaName ?: "该照片"}》未能恢复，可能已被系统彻底清除")
                }
            }
            _uiState.update { it.copy(pendingRestore = null, restoringLogId = null) }
        }
    }

    fun clearPendingRestore() {
        _uiState.update { it.copy(pendingRestore = null, restoringLogId = null) }
    }

    // ---------------- 设置 ----------------

    fun setDailyGoal(v: Int) {
        viewModelScope.launch { settingsRepo.setDailyGoal(v) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setReminderEnabled(enabled)
            if (enabled) {
                ScanWorker.enqueueOnce(getApplication())
            }
        }
    }

    fun setReminderIntervalDays(days: Int) {
        viewModelScope.launch { settingsRepo.setReminderIntervalDays(days) }
    }

    fun setQuietHours(
        startHour: Int,
        endHour: Int,
    ) {
        viewModelScope.launch { settingsRepo.setQuietHours(startHour, endHour) }
    }

    /** 测试入口：只重置 App 内状态，不会恢复已经被系统删除或移入回收站的真实文件。 */
    fun resetForTesting() {
        viewModelScope.launch {
            dao.clearAll()
            albumDao.clearItems()
            logDao.clearAll()
            settingsRepo.resetDaily()
            _uiState.update { s ->
                recompute(
                    s.copy(
                        currentIndex = 0,
                        processedCount = 0,
                        freedBytes = 0,
                        pendingDelete = null,
                        pendingRestore = null,
                        restoringLogId = null,
                        undo = null,
                        feedbackMessage = null,
                        error = null,
                    ),
                )
            }
        }
    }

    fun removeFromAlbum(
        albumId: Long,
        mediaIds: Set<Long>,
    ) {
        if (mediaIds.isEmpty()) return
        viewModelScope.launch {
            mediaIds.forEach { mediaId -> albumDao.removeItem(albumId, mediaId) }
            if (_uiState.value.openAlbumId == albumId) openAlbum(albumId)
        }
    }

    // ---------------- 内部 ----------------

    /** 稳定伪随机排序：同一 id 始终落在同一位置，新增照片不会打乱已有卡片顺序。 */
    private fun shuffleKey(id: Long): Long {
        var x = id * 6364136223846793005L + 1442695040888963407L
        x = x xor (x shr 32)
        return x
    }

    /** 依据「全部媒体 + 筛选条件 + 已处理集合」重算工作集、队列和当前卡片列表。 */
    private fun recompute(s: HomeUiState): HomeUiState {
        val filtered =
            s.allAssets.filter { asset ->
                (s.filterType == null || asset.mediaType == s.filterType) &&
                    (s.filterBucket == null || asset.bucketId == s.filterBucket)
            }
        val blockedStatuses = setOf(MediaStatus.TRASH.value, MediaStatus.DELETE.value)
        val statusById = s.statuses.associate { it.localAssetId to it.status }
        val activeAssets = filtered.filter { statusById[it.id] !in blockedStatuses }
        val processedIds = s.statuses
            .filter { it.status !in blockedStatuses }
            .map { it.localAssetId }
            .toSet()
        val queues = QueueEngine.build(activeAssets, processedIds)
        val picked =
            queues.firstOrNull { it.type == s.queueType && it.title == s.queueTitle }
                ?: queues.firstOrNull { it.type == s.queueType }
                ?: queues.firstOrNull { it.type == QueueType.UNPROCESSED }
                ?: queues.firstOrNull { it.type == QueueType.RANDOM }
        val items = picked?.items.orEmpty()
        return s.copy(
            assets = filtered,
            queues = queues,
            queueTitle = picked?.title.orEmpty(),
            queueItems = items,
            currentIndex = if (items.isEmpty()) 0 else s.currentIndex.coerceIn(0, items.lastIndex),
        )
    }

    private suspend fun finishAction(
        asset: MediaAsset,
        status: MediaStatus,
        message: String = actionMessage(status),
        logAction: String = status.value,
        undoAlbumId: Long? = null,
        undoAlbumItemAdded: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val before = dao.get(asset.id)?.status.orEmpty()
        val source = _uiState.value.queueSource
        val undo =
            UndoState(
                mediaId = asset.id,
                mediaName = asset.displayName,
                mediaType = asset.mediaType.name,
                beforeStatus = before.ifEmpty { null },
                albumId = undoAlbumId,
                albumItemAdded = undoAlbumItemAdded,
                message = message,
                createdAt = now,
            )
        _uiState.update { it.copy(undo = undo, feedbackMessage = message) }
        dao.upsert(toEntity(asset, status))
        _uiState.update { s ->
            recompute(
                s.copy(
                    currentIndex = s.currentIndex.coerceAtMost(s.queueItems.size),
                    processedCount = s.processedCount + 1,
                    undo = undo,
                    feedbackMessage = message,
                ),
            )
        }
        clearFeedbackAfterDelay(undo)
        logDao.insert(
            UserActionLogEntity(
                mediaId = asset.id,
                mediaName = asset.displayName,
                mediaType = asset.mediaType.name,
                action = logAction,
                source = source,
                beforeState = before,
                afterState = status.value,
                freedBytes = 0L,
                createdAt = now,
            ),
        )
        settingsRepo.addProcessed(1)
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    private fun clearFeedbackAfterDelay(undo: UndoState) {
        viewModelScope.launch {
            delay(UNDO_VISIBLE_MS)
            _uiState.update { state ->
                if (state.undo?.mediaId == undo.mediaId && state.undo?.createdAt == undo.createdAt) {
                    state.copy(undo = null, feedbackMessage = null)
                } else {
                    state
                }
            }
        }
    }

    private fun actionMessage(status: MediaStatus): String =
        when (status) {
            MediaStatus.TRASH -> "已加入待删除"
            MediaStatus.KEEP -> "已保留"
            MediaStatus.ALBUM -> "已归类"
            MediaStatus.LATER -> "已标记稍后"
            MediaStatus.FAVORITE -> "已收藏"
            MediaStatus.DELETE -> "已删除"
            MediaStatus.PERMANENT -> "已永久保留"
        }

    private suspend fun finishTrashDelete(ids: Set<Long>) {
        val byId = _uiState.value.allAssets.associateBy { it.id }
        var freed = 0L
        ids.forEach { id ->
            byId[id]?.let { asset ->
                val before = dao.get(asset.id)?.status.orEmpty()
                dao.upsert(toEntity(asset, MediaStatus.DELETE))
                logDao.insert(
                    UserActionLogEntity(
                        mediaId = asset.id,
                        mediaName = asset.displayName,
                        mediaType = asset.mediaType.name,
                        action = MediaStatus.DELETE.value,
                        source = "待删除",
                        beforeState = before,
                        afterState = MediaStatus.DELETE.value,
                        freedBytes = asset.size,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                freed += asset.size
            }
        }
        _uiState.update { s ->
            recompute(
                s.copy(
                    allAssets = s.allAssets.filter { it.id !in ids },
                    freedBytes = s.freedBytes + freed,
                    pendingDelete = null,
                    trashDeleteIdsInFlight = emptySet(),
                    undo = null,
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

    private fun hasPartialMediaAccess(app: Application): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val hasPartial =
            ContextCompat.checkSelfPermission(app, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
                PackageManager.PERMISSION_GRANTED
        val hasFullImages =
            ContextCompat.checkSelfPermission(app, Manifest.permission.READ_MEDIA_IMAGES) ==
                PackageManager.PERMISSION_GRANTED
        val hasFullVideo =
            ContextCompat.checkSelfPermission(app, Manifest.permission.READ_MEDIA_VIDEO) ==
                PackageManager.PERMISSION_GRANTED
        return hasPartial && !(hasFullImages && hasFullVideo)
    }
}
