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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    onFilterType: (com.example.photoorganizer.data.MediaType?) -> Unit,
    onSelectQueue: (com.example.photoorganizer.domain.MediaQueue) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortedQueues =
        state.queues.sortedWith(
            compareByDescending<com.example.photoorganizer.domain.MediaQueue> { it.items.isNotEmpty() }
                .thenBy { if (it.type == state.queueType && it.title == state.queueTitle) 0 else 1 }
                .thenByDescending { it.items.size }
                .thenBy { it.displayName },
        )
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
                "切换短任务，不离开当前整理流。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("当前", style = MaterialTheme.typography.labelSmall)
                    Text(state.queueSource, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "剩余 ${state.remaining} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text("媒体类型", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChipButton("全部", state.filterType == null) { onFilterType(null) }
                FilterChipButton("图片", state.filterType == com.example.photoorganizer.data.MediaType.IMAGE) {
                    onFilterType(com.example.photoorganizer.data.MediaType.IMAGE)
                }
                FilterChipButton("视频", state.filterType == com.example.photoorganizer.data.MediaType.VIDEO) {
                    onFilterType(com.example.photoorganizer.data.MediaType.VIDEO)
                }
            }
            Text("推荐队列", style = MaterialTheme.typography.labelLarge)
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
                                    if (q.items.isEmpty()) {
                                        "暂无可整理内容"
                                    } else {
                                        "${q.items.size} 项 · ${formatBytes(q.estimatedSavingBytes)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                if (selected) "当前" else if (q.items.isEmpty()) "完成" else "进入",
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
