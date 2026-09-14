package com.example.photoorganizer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photoorganizer.domain.MediaQueue
import com.example.photoorganizer.domain.QueueType
import com.example.photoorganizer.domain.StatsService

/** 统计页：把整理进度、空间收益和下一步队列集中到一个轻量视图。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onPickQueue: (MediaQueue) -> Unit,
    onOpenTrash: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val stats = remember(state.assets) { StatsService.compute(state.assets) }
    val organizedProgress =
        remember(stats.total, state.organizedCount) {
            if (stats.total == 0) 0f else (state.organizedCount.toFloat() / stats.total).coerceIn(0f, 1f)
        }
    val queueProgressItems =
        remember(state.queues) {
            listOf(
                QueueType.RANDOM,
                QueueType.SIMILAR,
                QueueType.SCREENSHOT,
                QueueType.LARGE_VIDEO,
                QueueType.RECENT_30,
            )
                .mapNotNull { type -> state.queues.firstOrNull { it.type == type } }
        }

    Scaffold(topBar = { TopAppBar(title = { Text("统计") }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("整理进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "已整理 ${state.organizedCount} / ${stats.total}，还有 ${state.unprocessedCount} 张未整理。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { organizedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    icon = Icons.Default.Done,
                    label = "今日",
                    value = "${state.daily.count}/${state.settings.dailyGoal}",
                    helper = if (state.dailyDone) "今日目标已完成" else "轻整理目标",
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    icon = Icons.Default.DeleteOutline,
                    label = "预计释放",
                    value = formatBytes(state.trashBytes),
                    helper = "${state.trashCount} 项待复核",
                    modifier = Modifier.weight(1f),
                    onClick = if (state.trashCount > 0) onOpenTrash else null,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatMetricCard(
                    icon = Icons.Default.AutoAwesome,
                    label = "已释放",
                    value = formatBytes(state.freedBytes),
                    helper = "系统确认删除后统计",
                    modifier = Modifier.weight(1f),
                )
                StatMetricCard(
                    icon = Icons.Default.Notifications,
                    label = "提醒",
                    value = if (state.settings.reminderEnabled) "已开启" else "未开启",
                    helper = "温和每日维护",
                    modifier = Modifier.weight(1f),
                )
            }

            Text("队列进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            queueProgressItems.forEach { queue ->
                QueueProgressRow(
                    queue = queue,
                    total = stats.total,
                    onClick = {
                        if (queue.items.isNotEmpty()) {
                            vm.selectQueue(queue)
                            onPickQueue(queue)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    helper: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier =
            if (onClick == null) {
                modifier
            } else {
                modifier.clickable(onClick = onClick)
            },
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QueueProgressRow(
    queue: MediaQueue,
    total: Int,
    onClick: () -> Unit,
) {
    val progress = if (total == 0) 0f else ((total - queue.items.size).toFloat() / total).coerceIn(0f, 1f)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = queue.items.isNotEmpty(), onClick = onClick),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(
                        queue.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (queue.items.isNotEmpty()) {
                            "${queue.items.size} 项 · 预计 ${formatBytes(queue.estimatedSavingBytes)}"
                        } else {
                            "这个队列已整理完"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (queue.items.isNotEmpty()) "继续" else "完成", style = MaterialTheme.typography.labelLarge)
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
