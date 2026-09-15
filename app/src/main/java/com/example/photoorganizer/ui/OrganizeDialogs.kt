package com.example.photoorganizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.photoorganizer.data.MediaType
import com.example.photoorganizer.domain.MediaQueue
import com.example.photoorganizer.domain.QueueType

@Composable
internal fun FilterChipButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(text) },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                labelColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
            ),
    )
}

/** 队列与筛选选择（PRD 5.1 / 4.3）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueFilterSheet(
    state: HomeUiState,
    onFilterType: (MediaType?) -> Unit,
    onFilterBucket: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onSelectQueue: (MediaQueue) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bucketOptions =
        state.allAssets
            .asSequence()
            .filter { state.filterType == null || it.mediaType == state.filterType }
            .groupBy { it.bucketId.ifBlank { it.bucketName.ifBlank { "unknown" } } }
            .map { (bucketId, assets) ->
                BucketFilterOption(
                    id = bucketId,
                    name = assets.firstOrNull()?.bucketName?.ifBlank { "未知相册" } ?: "未知相册",
                    count = assets.size,
                )
            }
            .sortedWith(compareByDescending<BucketFilterOption> { it.count }.thenBy { it.name })
    val sortedQueues =
        state.queues.sortedWith(
            compareByDescending<MediaQueue> { it.items.isNotEmpty() }
                .thenBy { if (it.type == state.queueType && it.title == state.queueTitle) 0 else 1 }
                .thenBy { queuePriority(it) }
                .thenByDescending { it.items.size }
                .thenBy { it.displayName },
        )
    val currentTotal = state.processedCount + state.queueItems.size
    val currentDone = state.processedCount.coerceIn(0, currentTotal)
    val currentProgress = if (currentTotal == 0) 1f else currentDone.toFloat() / currentTotal
    val scopeLabel = queueScopeLabel(state)
    val hasActiveFilters = state.filterType != null || state.filterBucket != null
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("切换短队列", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "按当前范围继续整理，点队列立即进入照片流。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("当前", style = MaterialTheme.typography.labelSmall)
                            Text(
                                state.queueSource,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "剩余 ${state.remaining} 项",
                            modifier =
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(100.dp),
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Text(
                        "本次进度 $currentDone / $currentTotal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "范围：$scopeLabel",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (hasActiveFilters) {
                            TextButton(onClick = onClearFilters) {
                                Text("重置")
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { currentProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                    )
                }
            }
            QueueSheetSectionTitle("筛选范围")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChipButton("全部", state.filterType == null) { onFilterType(null) }
                FilterChipButton("图片", state.filterType == MediaType.IMAGE) {
                    onFilterType(MediaType.IMAGE)
                }
                FilterChipButton("视频", state.filterType == MediaType.VIDEO) {
                    onFilterType(MediaType.VIDEO)
                }
            }
            if (bucketOptions.isNotEmpty()) {
                QueueSheetSectionTitle("系统相册", "${bucketOptions.size} 个")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    item {
                        FilterChipButton(
                            text = "全部相册 · ${bucketOptions.sumOf { it.count }}",
                            selected = state.filterBucket == null,
                            onClick = { onFilterBucket(null) },
                        )
                    }
                    items(bucketOptions, key = { it.id }) { bucket ->
                        FilterChipButton(
                            text = "${bucket.name} · ${bucket.count}",
                            selected = state.filterBucket == bucket.id,
                            onClick = { onFilterBucket(bucket.id) },
                        )
                    }
                }
            }
            QueueSheetSectionTitle("继续整理", "${sortedQueues.count { it.items.isNotEmpty() }} 个可继续")
            LazyColumn(Modifier.height(332.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sortedQueues) { q ->
                    val selected = q.type == state.queueType && q.title == state.queueTitle
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = q.items.isNotEmpty()) { onSelectQueue(q) },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    when {
                                        selected -> MaterialTheme.colorScheme.primaryContainer
                                        q.items.isEmpty() -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                            ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    q.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    queueRowSubtitle(q),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                if (selected) "当前" else if (q.items.isEmpty()) "完成" else "继续",
                                modifier =
                                    Modifier
                                        .background(
                                            if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.secondaryContainer
                                            },
                                            RoundedCornerShape(100.dp),
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class BucketFilterOption(
    val id: String,
    val name: String,
    val count: Int,
)

private fun queueScopeLabel(state: HomeUiState): String =
    buildList {
        when (state.filterType) {
            MediaType.IMAGE -> add("图片")
            MediaType.VIDEO -> add("视频")
            null -> Unit
        }
        if (state.filterBucket != null) {
            val bucketName =
                state.allAssets
                    .firstOrNull { asset ->
                        val bucketKey = asset.bucketId.ifBlank { asset.bucketName.ifBlank { "unknown" } }
                        bucketKey == state.filterBucket
                    }
                    ?.bucketName
                    ?.ifBlank { null }
                    ?: "未知相册"
            add(bucketName)
        }
    }.joinToString(" · ").ifBlank { "全部照片和视频" }

@Composable
private fun QueueSheetSectionTitle(
    title: String,
    count: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        if (count != null) {
            Text(
                count,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun queueRowMeta(queue: MediaQueue): String =
    buildString {
        if (queue.items.isEmpty()) {
            append(emptyQueueMeta(queue))
        } else {
            append("${queue.items.size} 项")
            if (queue.estimatedSavingBytes > 0L) {
                append(" · ${formatBytes(queue.estimatedSavingBytes)}")
            }
        }
    }

private fun queueRowSubtitle(
    queue: MediaQueue,
): String {
    val meta = queueRowMeta(queue)
    return if (queue.items.isEmpty()) {
        meta
    } else {
        "${queueRowPurpose(queue)} · $meta"
    }
}

private fun queueRowPurpose(queue: MediaQueue): String =
    when (queue.type) {
        QueueType.RANDOM -> "快速进入下一张判断"
        QueueType.ON_THIS_DAY -> "回看往年今天的照片"
        QueueType.ALBUM -> "从某个系统相册继续整理"
        QueueType.UNPROCESSED -> "查看所有还没处理的内容"
        QueueType.LATER -> "继续之前暂放的照片"
        QueueType.SIMILAR -> "横向对比同组相近照片"
        QueueType.SCREENSHOT -> "集中清理截图"
        QueueType.LARGE_VIDEO -> "优先处理占空间的视频"
        QueueType.RECENT_30 -> "整理最近新增内容"
        QueueType.FAVORITE -> "回看系统或应用收藏"
        QueueType.MONTH -> "按月份慢慢回顾"
    }

private fun emptyQueueMeta(queue: MediaQueue): String =
    when (queue.type) {
        QueueType.ON_THIS_DAY -> "今天暂无待整理回忆"
        QueueType.MONTH -> "没有待整理月份"
        else -> "没有待处理照片"
    }

private fun queuePriority(queue: MediaQueue): Int =
    when (queue.type) {
        QueueType.RANDOM -> 0
        QueueType.ON_THIS_DAY -> 1
        QueueType.ALBUM -> 2
        QueueType.UNPROCESSED -> 3
        QueueType.LATER -> 4
        QueueType.SIMILAR -> 5
        QueueType.SCREENSHOT -> 6
        QueueType.LARGE_VIDEO -> 7
        QueueType.RECENT_30 -> 8
        QueueType.FAVORITE -> 9
        QueueType.MONTH -> 10
    }
