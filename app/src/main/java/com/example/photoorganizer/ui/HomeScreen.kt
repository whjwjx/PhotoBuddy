package com.example.photoorganizer.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photoorganizer.R
import com.example.photoorganizer.data.local.UserActionLogEntity
import com.example.photoorganizer.domain.MediaQueue
import com.example.photoorganizer.domain.QueueType
import com.example.photoorganizer.domain.StatsService

/** Slidebox 风格首页：把大相册拆成短队列，主入口始终是继续整理。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartOrganize: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAlbums: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    var showAllQueues by remember { mutableStateOf(false) }
    val stats = remember(state.assets) { StatsService.compute(state.assets) }
    val homeQueues = remember(state.queues) { buildHomeQueues(state.queues) }
    val visibleQueues =
        remember(homeQueues, showAllQueues) {
            if (showAllQueues) homeQueues else homeQueues.take(4)
        }
    val primaryQueue =
        remember(state.queues) {
            state.queues.firstOrNull { it.type == QueueType.RANDOM && it.items.isNotEmpty() }
                ?: state.queues.firstOrNull { it.type == QueueType.UNPROCESSED && it.items.isNotEmpty() }
        }
    val organizedProgress =
        remember(stats.total, state.organizedCount) {
            if (stats.total == 0) 0f else (state.organizedCount.toFloat() / stats.total).coerceIn(0f, 1f)
        }
    val organizedPercent = remember(organizedProgress) { formatPercent(organizedProgress) }

    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) vm.onRestoreApproved() else vm.clearPendingRestore()
        }
    LaunchedEffect(state.pendingRestore) {
        state.pendingRestore?.let { sender ->
            restoreLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { vm.scan(full = true) }, enabled = !state.isScanning) {
                        Text(if (state.isScanning) "扫描中" else "重新扫描")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${state.unprocessedCount} 张未整理",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "从一小组开始，几分钟也能往前推进。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { organizedProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "整理率 $organizedPercent · 已整理 ${state.organizedCount} / ${stats.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Button(
                        onClick = {
                            primaryQueue?.let { vm.selectQueue(it) }
                            onStartOrganize()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = primaryQueue != null,
                    ) {
                        Text(primaryQueue?.let { "继续 ${it.displayName}" } ?: "暂无待整理")
                    }
                    if (state.isScanning) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("已扫描 ${state.scanProgress} 项", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            state.error?.let {
                Text(
                    "错误：$it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.partialAccess) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("当前是部分照片访问", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "系统只允许本应用访问你勾选的部分照片，扫描结果会偏少。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = {
                            context.startActivity(
                                android.content
                                    .Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    },
                            )
                        }) {
                            Text("去系统设置修改")
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatPill("总数", "${stats.total}", Modifier.weight(1f))
                StatPill("今日", "${state.daily.count}/${state.settings.dailyGoal}", Modifier.weight(1f))
                StatPill("整理率", organizedPercent, Modifier.weight(1f))
            }

            HomeEntryGrid(
                primaryQueue = primaryQueue,
                onThisDayQueue = homeQueues.firstOrNull { it.type == QueueType.ON_THIS_DAY },
                monthQueue = homeQueues.firstOrNull { it.type == QueueType.MONTH },
                albumQueue = homeQueues.firstOrNull { it.type == QueueType.ALBUM },
                trashCount = state.trashCount,
                onPickQueue = { queue ->
                    vm.selectQueue(queue)
                    onStartOrganize()
                },
                onOpenAlbums = onOpenAlbums,
                onOpenTrash = onOpenTrash,
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("短队列", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (homeQueues.size > 4 && !showAllQueues) {
                            "优先展示最值得继续的 ${visibleQueues.size} 组"
                        } else {
                            "按照片场景继续整理"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (homeQueues.size > 4) {
                    TextButton(onClick = { showAllQueues = !showAllQueues }) {
                        Text(if (showAllQueues) "收起" else "全部")
                    }
                }
            }
            QueueGrid(
                queues = visibleQueues,
                onPick = { queue ->
                    vm.selectQueue(queue)
                    onStartOrganize()
                },
            )

            if (state.recentLogs.isNotEmpty()) {
                RecentActivityCard(logs = state.recentLogs.take(5))
            }

            if (state.deletedLogs.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("系统最近删除", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "这些是已经通过系统确认删除的项目，可尝试从系统回收站恢复。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        state.deletedLogs.take(3).forEach { log ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    log.mediaName,
                                    modifier = Modifier.fillMaxWidth(0.68f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                TextButton(onClick = { vm.restore(log) }) { Text("恢复") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeEntryGrid(
    primaryQueue: MediaQueue?,
    onThisDayQueue: MediaQueue?,
    monthQueue: MediaQueue?,
    albumQueue: MediaQueue?,
    trashCount: Int,
    onPickQueue: (MediaQueue) -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HomeEntryCard(
                label = "Continue",
                title = "继续整理",
                detail = continueEntryDetail(primaryQueue),
                icon = Icons.Default.AutoAwesome,
                enabled = primaryQueue?.items?.isNotEmpty() == true,
                modifier = Modifier.weight(1f),
                onClick = { primaryQueue?.let(onPickQueue) },
            )
            HomeEntryCard(
                label = "On This Day",
                title = "往年今日",
                detail = queueEntryDetail(onThisDayQueue, "今天暂无待整理回忆"),
                icon = Icons.Default.Today,
                enabled = onThisDayQueue?.items?.isNotEmpty() == true,
                modifier = Modifier.weight(1f),
                onClick = { onThisDayQueue?.let(onPickQueue) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HomeEntryCard(
                label = "Monthly",
                title = "按月份整理",
                detail = queueEntryDetail(monthQueue, "没有待整理月份", fallbackTitle = "未知月份"),
                icon = Icons.Default.Today,
                enabled = monthQueue?.items?.isNotEmpty() == true,
                modifier = Modifier.weight(1f),
                onClick = { monthQueue?.let(onPickQueue) },
            )
            HomeEntryCard(
                label = "Albums",
                title = "按相册整理",
                detail =
                    if (albumQueue?.items?.isNotEmpty() == true) {
                        queueEntryDetail(albumQueue, "管理本地映射", fallbackTitle = "未知相册")
                    } else {
                        "管理本地映射"
                    },
                icon = Icons.Default.PhotoAlbum,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (albumQueue?.items?.isNotEmpty() == true) {
                        onPickQueue(albumQueue)
                    } else {
                        onOpenAlbums()
                    }
                },
            )
        }
        HomeEntryCard(
            label = "Trash",
            title = "待删除复核",
            detail = if (trashCount > 0) "$trashCount 项待确认" else "当前为空",
            icon = Icons.Default.DeleteOutline,
            enabled = trashCount > 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenTrash,
        )
    }
}

@Composable
private fun HomeEntryCard(
    label: String,
    title: String,
    detail: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (enabled) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint =
                        if (enabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun queueEntryDetail(
    queue: MediaQueue?,
    emptyText: String = "暂无待整理",
    fallbackTitle: String? = null,
): String =
    if (queue?.items?.isNotEmpty() == true) {
        fallbackTitle?.let { title ->
            "${queue.title.ifBlank { title }} · ${queue.items.size} 项待整理"
        } ?: "${queue.items.size} 项待整理"
    } else {
        emptyText
    }

private fun continueEntryDetail(queue: MediaQueue?): String =
    if (queue?.items?.isNotEmpty() == true) {
        "${queue.displayName} · ${queue.items.size} 项待整理"
    } else {
        "暂无未整理"
    }

@Composable
private fun RecentActivityCard(logs: List<UserActionLogEntity>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("最近整理", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "刚刚处理过的照片会留在这里，方便回看整理路径。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QueueGrid(
    queues: List<MediaQueue>,
    onPick: (MediaQueue) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        queues.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { queue ->
                    QueueCard(
                        queue = queue,
                        icon =
                            when (queue.type) {
                                QueueType.RANDOM -> Icons.Default.AutoAwesome
                                QueueType.UNPROCESSED -> Icons.Default.PhotoLibrary
                                QueueType.SIMILAR -> Icons.Default.ImageSearch
                                QueueType.SCREENSHOT -> Icons.Default.ImageSearch
                                QueueType.LARGE_VIDEO -> Icons.Default.Movie
                                QueueType.RECENT_30 -> Icons.Default.Today
                                QueueType.ON_THIS_DAY -> Icons.Default.Today
                                QueueType.ALBUM -> Icons.Default.PhotoAlbum
                                QueueType.LATER -> Icons.Default.Schedule
                                QueueType.FAVORITE -> Icons.Default.AutoAwesome
                                else -> Icons.Default.PhotoLibrary
                            },
                        modifier = Modifier.weight(1f),
                        onClick = { onPick(queue) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun buildHomeQueues(queues: List<MediaQueue>): List<MediaQueue> {
    val priority =
        listOf(
            QueueType.RANDOM,
            QueueType.ON_THIS_DAY,
            QueueType.UNPROCESSED,
            QueueType.LATER,
            QueueType.SIMILAR,
            QueueType.SCREENSHOT,
            QueueType.LARGE_VIDEO,
            QueueType.RECENT_30,
            QueueType.FAVORITE,
        )
    val preferred = priority.mapNotNull { type -> queues.firstOrNull { it.type == type } }
    val albumQueues = queues.filter { it.type == QueueType.ALBUM && it.items.isNotEmpty() }
    val monthQueues = queues.filter { it.type == QueueType.MONTH && it.items.isNotEmpty() }
    return (preferred.filter { it.items.isNotEmpty() } + albumQueues + monthQueues + preferred)
        .distinctBy { it.type to it.title }
}

@Composable
private fun QueueCard(
    queue: MediaQueue,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(enabled = queue.items.isNotEmpty(), onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (queue.items.isNotEmpty()) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null)
                Text(
                    queueBadge(queue),
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(100.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    queue.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    queueHelper(queue),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                queueMeta(queue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun queueBadge(queue: MediaQueue): String =
    if (queue.items.isEmpty()) {
        "已完成"
    } else {
        when (queue.type) {
            QueueType.RANDOM -> "推荐"
            QueueType.UNPROCESSED -> "全部"
            QueueType.SIMILAR -> "对比"
            QueueType.SCREENSHOT -> "清理"
            QueueType.LARGE_VIDEO -> "空间"
            QueueType.RECENT_30 -> "新增"
            QueueType.ON_THIS_DAY -> "回忆"
            QueueType.ALBUM -> "相册"
            QueueType.LATER -> "回看"
            QueueType.FAVORITE -> "收藏"
            QueueType.MONTH -> "回顾"
        }
    }

private fun queueHelper(queue: MediaQueue): String =
    if (queue.items.isEmpty()) {
        when (queue.type) {
            QueueType.ON_THIS_DAY -> "今天没有待整理回忆"
            QueueType.MONTH -> "没有待整理月份"
            else -> "这个队列已清空"
        }
    } else {
        when (queue.type) {
            QueueType.RANDOM -> "从一组轻量判断开始"
            QueueType.UNPROCESSED -> "完整未整理列表"
            QueueType.SIMILAR -> "对比相近照片"
            QueueType.SCREENSHOT -> "快速清理截图"
            QueueType.LARGE_VIDEO -> "优先查看大文件"
            QueueType.RECENT_30 -> "整理最近新增"
            QueueType.ON_THIS_DAY -> "回看往年今天"
            QueueType.ALBUM -> "按相册继续整理"
            QueueType.LATER -> "继续处理稍后照片"
            QueueType.FAVORITE -> "回看收藏照片"
            QueueType.MONTH -> "按月份回看"
        }
    }

private fun queueMeta(queue: MediaQueue): String =
    if (queue.items.isNotEmpty()) {
        "${queue.items.size} 项 · 合计 ${formatBytes(queue.estimatedSavingBytes)}"
    } else {
        "没有待处理照片"
    }
