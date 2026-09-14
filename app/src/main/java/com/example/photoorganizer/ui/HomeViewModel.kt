package com.example.photoorganizer.ui

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaStoreRepository
import com.example.photoorganizer.data.MediaType
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
    /** API 30+ 待启动的系统删除确认 IntentSender。 */
    val pendingDelete: IntentSender? = null,
) {
    val current: MediaAsset? get() = queueItems.getOrNull(currentIndex)
    val remaining: Int get() = (queueItems.size - currentIndex).coerceAtLeast(0)
}

class HomeViewModel(
    app: Application,
) : AndroidViewModel(app) {
    private val repo = MediaStoreRepository(app.contentResolver)
    private val dao = AppDatabase.getDatabase(app).mediaStatusDao()
    private val coordinator = DeleteCoordinator(app.contentResolver)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.observeAll().collect { list ->
                _uiState.update { s -> recompute(s.copy(statuses = list)) }
            }
        }
    }

    fun scan() {
        _uiState.update {
            it.copy(isScanning = true, error = null, currentIndex = 0, processedCount = 0, freedBytes = 0)
        }
        viewModelScope.launch {
            runCatching { repo.loadAll() }
                .onSuccess { assets ->
                    // 扫描时打乱一次，形成「随机整理」流；会话内顺序保持稳定，避免卡片跳动。
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val sender =
                coordinator.createDeleteRequest(listOf(asset.uri))?.intentSender
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

    /** 用户在系统弹窗取消删除。 */
    fun clearPendingDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

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
            // 「未整理」队列中，处理完的项会被移出队列，下一张自动补位，因此不推进索引。
            val advance = if (s.queueType == QueueType.UNPROCESSED) 0 else 1
            val freed = if (status == MediaStatus.DELETE) asset.size else 0L
            s.copy(
                freedBytes = s.freedBytes + freed,
                currentIndex = (s.currentIndex + advance).coerceAtMost(s.queueItems.size),
                processedCount = s.processedCount + 1,
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
