package com.example.photoorganizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.photoorganizer.data.local.AlbumEntity

@Composable
internal fun FilterChipButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
            ),
    ) { Text(text) }
}

/** 加入应用内相册（PRD 4.2）。 */
@Composable
internal fun AddToAlbumDialog(
    albums: List<AlbumEntity>,
    counts: Map<Long, Int>,
    onCreate: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入相册") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新建相册名称") },
                    singleLine = true,
                )
                Button(onClick = {
                    onCreate(name)
                    name = ""
                }) { Text("新建相册") }
                Spacer(Modifier.height(8.dp))
                if (albums.isEmpty()) {
                    Text("还没有相册，先新建一个。")
                } else {
                    Text("选择已有相册：")
                    LazyColumn(Modifier.height(200.dp)) {
                        items(albums) { a ->
                            TextButton(onClick = { onPick(a.id) }) {
                                Text("${a.name} (${counts[a.id] ?: 0})")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 批量确认：显示总数量、预计释放空间、高风险项数量（PRD 5.6）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchConfirmDialog(
    state: HomeUiState,
    onToggle: (Long) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val items = state.batchCandidates
    val selected = state.selectedIds
    val selectedBytes = items.filter { it.id in selected }.sumOf { it.size }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量确认删除") },
        text = {
            Column {
                Text("已选 ${selected.size} 项 · 预计释放 ${formatBytes(selectedBytes)}")
                Text(
                    "已按安全策略排除高风险 ${state.highRiskCount} 项；本批最多处理 ${state.settings.batchChunkSize} 项。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSelectAll(true) }) { Text("全选") }
                    Button(onClick = { onSelectAll(false) }) { Text("全不选") }
                }
                LazyColumn(Modifier.height(280.dp)) {
                    items(items) { a ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = a.id in selected, onCheckedChange = { onToggle(a.id) })
                            AsyncImage(
                                model = a.uri,
                                contentDescription = a.displayName,
                                modifier = Modifier.size(48.dp),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.size(8.dp))
                            Column {
                                Text(a.displayName, style = MaterialTheme.typography.bodySmall)
                                Text(formatBytes(a.size), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = selected.isNotEmpty()) {
                Text("确认删除 ${selected.size} 项")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 队列与筛选选择（PRD 5.1 / 4.3）。 */
@Composable
internal fun QueueFilterDialog(
    state: HomeUiState,
    albums: List<Pair<String, String>>,
    onFilterType: (com.example.photoorganizer.data.MediaType?) -> Unit,
    onFilterBucket: (String?) -> Unit,
    onSelectQueue: (com.example.photoorganizer.domain.MediaQueue) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择队列") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("当前队列", style = MaterialTheme.typography.labelSmall)
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
                Text("队列", style = MaterialTheme.typography.labelLarge)
                LazyColumn(Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.queues) { q ->
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
                                    if (selected) "当前" else "进入",
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
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
