package com.example.photoorganizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
