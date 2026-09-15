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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.local.MediaStatus
import com.example.photoorganizer.data.local.UserActionLogEntity
import com.example.photoorganizer.domain.LibraryStats
import com.example.photoorganizer.domain.MediaQueue
import com.example.photoorganizer.domain.QueueType
import com.example.photoorganizer.domain.StatsService
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val RECENT_QUEUE_DAYS = 30L
private val BLOCKED_QUEUE_STATUSES = setOf(MediaStatus.TRASH.value, MediaStatus.DELETE.value)

private data class QueueProgressItem(
    val queue: MediaQueue,
    val totalCount: Int?,
) {
    val remainingCount: Int get() = queue.items.size
    val progress: Float
        get() {
            val total = totalCount ?: return if (remainingCount == 0) 1f else 0f
            if (total <= 0) return 1f
            return ((total - remainingCount).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }
}

/** 统计页：把整理进度、空间收益和下一步队列集中到一个轻量视图。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onPickQueue: (MediaQueue) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var showTodayLogs by remember { mutableStateOf(false) }
    val stats = remember(state.assets) { StatsService.compute(state.assets) }
    val todayLogs =
        remember(state.recentLogs, state.daily.date) {
            state.recentLogs
                .filter { log -> state.daily.date.isBlank() || formatDate(log.createdAt).startsWith(state.daily.date) }
                .take(6)
        }
    val organizedProgress =
        remember(stats.total, state.organizedCount) {
            if (stats.total == 0) 0f else (state.organizedCount.toFloat() / stats.total).coerceIn(0f, 1f)
        }
    val organizedPercent = remember(organizedProgress) { formatPercent(organizedProgress) }
    val todayGoal = state.settings.dailyGoal.coerceAtLeast(0)
    val todayRemaining = (todayGoal - state.daily.count).coerceAtLeast(0)
    val dailyProgress = state.daily.percent(todayGoal)
    val queueProgressItems =
        remember(
            state.queues,
            state.assets,
            state.statuses,
            stats.screenshotCount,
            stats.largeVideoCount,
            stats.total,
        ) {
            val fixedQueues =
                listOf(
                    QueueType.RANDOM,
                    QueueType.LATER,
                    QueueType.SIMILAR,
                    QueueType.ON_THIS_DAY,
                    QueueType.ALBUM,
                    QueueType.SCREENSHOT,
                    QueueType.LARGE_VIDEO,
                    QueueType.RECENT_30,
                )
                    .mapNotNull { type -> state.queues.firstOrNull { it.type == type } }
            val monthQueues =
                state.queues
                    .filter { it.type == QueueType.MONTH && it.items.isNotEmpty() }
                    .take(3)
            (fixedQueues + monthQueues)
                .map { queue ->
                    QueueProgressItem(
                        queue = queue,
                        totalCount = queueProgressTotal(queue, state, stats),
                    )
                }
        }
    val recommendedQueue = queueProgressItems.firstOrNull { it.queue.items.isNotEmpty() }?.queue

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
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "今日已整理 ${state.daily.count} 张",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "预计释放 ${formatBytes(state.trashBytes)} · ${state.trashCount} 项待复核",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (state.dailyDone) {
                            "今日目标已完成，可以轻松收工。"
                        } else {
                            "今天已整理 ${state.daily.count} 张，还差 $todayRemaining 张到今日目标。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { dailyProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "整理率 $organizedPercent · 已整理 ${state.organizedCount} / ${stats.total}，还有 ${state.unprocessedCount} 张未整理。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { organizedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    recommendedQueue?.let { queue ->
                        Text(
                            "下一步建议：${queue.displayName} · ${queue.items.size} 项",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showTodayLogs = !showTodayLogs }) {
                            Text(if (showTodayLogs) "收起今日记录" else "今日记录")
                        }
                        if (state.trashCount > 0) {
                            TextButton(onClick = onOpenTrash) {
                                Text("复核待删除")
                            }
                        }
                    }
                }
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
                    icon = Icons.Default.DeleteOutline,
                    label = "待释放",
                    value = formatBytes(state.trashBytes),
                    helper = "${state.trashCount} 项待复核",
                    modifier = Modifier.weight(1f),
                    onClick = if (state.trashCount > 0) onOpenTrash else null,
                )
            }
            StatMetricCard(
                icon = Icons.Default.Notifications,
                label = "整理提醒",
                value = if (state.settings.reminderEnabled) "已开启" else "未开启",
                helper =
                    if (state.settings.reminderEnabled) {
                        "每 ${state.settings.reminderIntervalDays} 天 · " +
                            "${state.settings.quietStartHour}:00-${state.settings.quietEndHour}:00 静默"
                    } else {
                        "点此设置轻提醒"
                    },
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenSettings,
            )

            if (showTodayLogs) {
                TodayActivityCard(
                    logs = todayLogs,
                    undoAvailable = state.undo != null,
                    onUndo = { vm.undoLast() },
                )
            }

            Text("继续整理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            queueProgressItems.forEach { item ->
                QueueProgressRow(
                    item = item,
                    onClick = {
                        val queue = item.queue
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
private fun TodayActivityCard(
    logs: List<UserActionLogEntity>,
    undoAvailable: Boolean,
    onUndo: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("今日记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (logs.isEmpty()) {
                            "今天还没有整理动作。"
                        } else if (undoAvailable) {
                            "按时间倒序显示，最新一步可以撤销。"
                        } else {
                            "最近 ${logs.size} 次动作仅用于回看。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (undoAvailable) {
                    TextButton(onClick = onUndo) {
                        Text("撤销最近一步")
                    }
                }
            }
            logs.forEach { log ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        actionLabel(log),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            log.mediaName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${log.source} · ${formatDate(log.createdAt)}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
    item: QueueProgressItem,
    onClick: () -> Unit,
) {
    val queue = item.queue
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
                            queueProgressText(item)
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
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun queueProgressText(item: QueueProgressItem): String {
    val total = item.totalCount
    val prefix =
        if (total != null && total > 0) {
            "剩余 ${item.remainingCount} / $total 项"
        } else {
            "剩余 ${item.remainingCount} 项"
        }
    return "$prefix · 合计 ${formatBytes(item.queue.estimatedSavingBytes)}"
}

private fun queueProgressTotal(
    queue: MediaQueue,
    state: HomeUiState,
    stats: LibraryStats,
): Int? =
    when (queue.type) {
        QueueType.RANDOM,
        QueueType.UNPROCESSED -> stats.total
        QueueType.LATER -> null
        QueueType.SCREENSHOT -> stats.screenshotCount
        QueueType.LARGE_VIDEO -> stats.largeVideoCount
        QueueType.RECENT_30 -> {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(RECENT_QUEUE_DAYS)
            state.assets.count { it.capturedAt > 0 && it.capturedAt >= cutoff }
        }
        QueueType.ON_THIS_DAY -> {
            val today = Calendar.getInstance()
            state.assets.count { asset ->
                val ms =
                    when {
                        asset.capturedAt > 0L -> asset.capturedAt
                        asset.dateAdded > 0L -> asset.dateAdded * 1000L
                        else -> 0L
                    }
                if (ms <= 0L) {
                    false
                } else {
                    val captured = Calendar.getInstance().apply { timeInMillis = ms }
                    captured.get(Calendar.YEAR) < today.get(Calendar.YEAR) &&
                        captured.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
                        captured.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
                }
            }
        }
        QueueType.SIMILAR -> null
        QueueType.ALBUM -> albumQueueTotal(queue, state)
        QueueType.MONTH -> monthQueueTotal(queue, state)
        QueueType.FAVORITE ->
            state.assets.count { asset ->
                asset.isFavorite || state.statusById[asset.id] == MediaStatus.FAVORITE.value
            }
    }

private fun albumQueueTotal(
    queue: MediaQueue,
    state: HomeUiState,
): Int =
    state.assets.count { asset -> asset.bucketName.ifBlank { "未知相册" } == queue.title }

private fun monthQueueTotal(
    queue: MediaQueue,
    state: HomeUiState,
): Int =
    state.assets.count { asset ->
        state.statusById[asset.id] !in BLOCKED_QUEUE_STATUSES &&
            monthKey(asset) == queue.title
    }.coerceAtLeast(queue.items.size)

private fun monthKey(asset: MediaAsset): String {
    val ms = asset.capturedAt
    if (ms <= 0L) return "未知时间"
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(Locale.getDefault(), "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}
