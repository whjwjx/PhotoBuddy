package com.example.photoorganizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photoorganizer.R
import com.example.photoorganizer.data.local.MediaStatus
import com.example.photoorganizer.domain.QueueType
import com.example.photoorganizer.domain.StatsService

/** 首页：相册概览、整理进度、快速入口（PRD 六·首页）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onStartOrganize: () -> Unit) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
        ) {
            Button(onClick = { vm.scan() }, enabled = !state.isScanning) {
                Text(if (state.isScanning) "扫描中… 已扫描 ${state.scanProgress} 项" else "扫描相册")
            }
            state.error?.let {
                Text(
                    "错误: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("今日整理任务", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = state.daily.percent(state.settings.dailyGoal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("已整理 ${state.daily.count} / ${state.settings.dailyGoal} 张")
                    if (state.dailyDone) {
                        Text("今日目标已完成，明天继续！")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            val stats = remember(state.assets) { StatsService.compute(state.assets) }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("相册概览", style = MaterialTheme.typography.titleMedium)
                    Text("共 ${stats.total} 项 · 图片 ${stats.images} · 视频 ${stats.videos}")
                    Text(
                        "占用 ${formatBytes(stats.totalBytes)} · 截图 ${stats.screenshotCount} · 大视频 ${stats.largeVideoCount}",
                    )
                }
            }

            // 仅在「疑似部分访问」且确实扫不到任何内容时提示，避免误报打扰用户
            if (state.partialAccess && state.allAssets.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("当前是「部分照片访问」", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "系统只允许本 App 访问你勾选的部分照片，所以扫描结果会偏少。" +
                                "可在系统设置里把照片权限改为「全部允许」。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(
                                android.content
                                    .Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                    .apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    },
                            )
                        }) { Text("去系统设置修改") }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("整理进度", style = MaterialTheme.typography.titleMedium)
                    Text("今日已处理 ${state.processedCount} 项")
                    Text("累计释放 ${formatBytes(state.freedBytes)}")
                    Text("当前队列待整理 ${state.remaining} 项")
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("最近处理记录", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (state.recentLogs.isEmpty()) {
                        Text(
                            "还没有处理记录，去刷几张试试。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        state.recentLogs.take(5).forEach { log ->
                            val label =
                                MediaStatus.values().firstOrNull { it.value == log.action }?.label
                                    ?: log.action
                            Text(
                                "${log.mediaName} · $label · ${log.source} · ${formatDate(log.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onStartOrganize, modifier = Modifier.fillMaxWidth()) {
                Text("开始刷照片")
            }

            Spacer(Modifier.height(12.dp))
            Text("快速入口", style = MaterialTheme.typography.labelLarge)
            val quickTypes = listOf(QueueType.SCREENSHOT, QueueType.LARGE_VIDEO, QueueType.UNPROCESSED)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.queues.filter { it.type in quickTypes }.forEach { q ->
                    Button(onClick = {
                        vm.selectQueue(q)
                        onStartOrganize()
                    }) {
                        Text("${q.type.label} (${q.items.size})")
                    }
                }
            }
        }
    }
}
