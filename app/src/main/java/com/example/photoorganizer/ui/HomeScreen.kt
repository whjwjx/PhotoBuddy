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
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.remember
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
import com.example.photoorganizer.domain.MediaQueue
import com.example.photoorganizer.domain.QueueType
import com.example.photoorganizer.domain.StatsService

/** Slidebox 风格首页：把大相册拆成短队列，主入口始终是继续整理。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartOrganize: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val stats = remember(state.assets) { StatsService.compute(state.assets) }

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
                        "一次只看一张。上滑待删除，下滑收藏，左右滑保留或稍后。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = {
                            state.queues.firstOrNull { it.type == QueueType.UNPROCESSED }?.let { vm.selectQueue(it) }
                            onStartOrganize()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.unprocessedCount > 0,
                    ) {
                        Text(if (state.unprocessedCount > 0) "继续整理" else "已经整理完")
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

            if (state.partialAccess && state.allAssets.isEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("当前是部分照片访问", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "系统只允许本 App 访问你勾选的部分照片，扫描结果会偏少。",
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
                StatPill("已处理", "${state.organizedCount}", Modifier.weight(1f))
            }

            TrashEntry(
                count = state.trashCount,
                bytes = state.trashBytes,
                onClick = onOpenTrash,
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("短队列", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "优先显示现在能继续整理的队列",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            QueueGrid(
                queues = state.queues,
                onPick = { queue ->
                    vm.selectQueue(queue)
                    onStartOrganize()
                },
            )

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
private fun TrashEntry(
    count: Int,
    bytes: Long,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = count > 0, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (count > 0) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null)
            }
            Column(Modifier.weight(1f)) {
                Text("待删除复核", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (count > 0) {
                        "$count 项 · 预计释放 ${formatBytes(bytes)}"
                    } else {
                        "上滑照片后会先放到这里"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(if (count > 0) "检查" else "暂无", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun QueueGrid(
    queues: List<MediaQueue>,
    onPick: (MediaQueue) -> Unit,
) {
    val priority =
        listOf(
            QueueType.RANDOM,
            QueueType.UNPROCESSED,
            QueueType.SIMILAR,
            QueueType.SCREENSHOT,
            QueueType.LARGE_VIDEO,
            QueueType.RECENT_30,
            QueueType.FAVORITE,
        )
    val preferred = priority.mapNotNull { type -> queues.firstOrNull { it.type == type } }
    val monthQueues = queues.filter { it.type == QueueType.MONTH && it.items.isNotEmpty() }
    val picked =
        (preferred.filter { it.items.isNotEmpty() } + monthQueues + preferred)
            .distinctBy { it.type to it.title }
            .take(4)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        picked.chunked(2).forEach { row ->
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
            Icon(icon, contentDescription = null)
            Text(
                queue.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (queue.items.isNotEmpty()) {
                    "${queue.items.size} 项 · ${formatBytes(queue.estimatedSavingBytes)}"
                } else {
                    "暂无可整理内容"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
