package com.example.photoorganizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.photoorganizer.data.MediaType
import com.example.photoorganizer.domain.MediaQueue

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
                .thenByDescending { it.items.size }
                .thenBy { it.displayName },
        )
    val currentTotal = state.queueItems.size
    val currentDone = (currentTotal - state.remaining).coerceIn(0, currentTotal)
    val currentProgress = if (currentTotal == 0) 1f else currentDone.toFloat() / currentTotal
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("选择队列", style = MaterialTheme.typography.titleLarge)
            Text(
                "切换短任务，当前整理进度会保留。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("当前短队列", style = MaterialTheme.typography.labelSmall)
                    Text(state.queueSource, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "已整理 $currentDone / $currentTotal · 剩余 ${state.remaining} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { currentProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    )
                }
            }
            QueueSheetSectionTitle("媒体类型")
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
            QueueSheetSectionTitle("短队列", "${sortedQueues.count { it.items.isNotEmpty() }} 个可继续")
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sortedQueues) { q ->
                    val selected = q.type == state.queueType && q.title == state.queueTitle
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = q.items.isNotEmpty()) { onSelectQueue(q) },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    q.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    queueRowMeta(q, selected),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (selected) "当前" else if (q.items.isEmpty()) "完成" else "继续",
                                style = MaterialTheme.typography.labelLarge,
                                color =
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
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

private fun queueRowMeta(
    queue: MediaQueue,
    selected: Boolean,
): String =
    buildString {
        if (queue.items.isEmpty()) {
            append("暂无可整理内容")
        } else {
            append("${queue.items.size} 项")
            if (queue.estimatedSavingBytes > 0L) {
                append(" · 预计可释放 ${formatBytes(queue.estimatedSavingBytes)}")
            }
        }
        if (selected) {
            append(" · 正在整理")
        }
    }
