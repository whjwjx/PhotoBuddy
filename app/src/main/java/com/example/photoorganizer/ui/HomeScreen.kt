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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photoorganizer.R
import com.example.photoorganizer.domain.QueueType
import com.example.photoorganizer.domain.StatsService

/** 首页：相册概览、整理进度、快速入口（PRD 六·首页）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onStartOrganize: () -> Unit) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()

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
                Text(if (state.isScanning) "扫描中…" else "扫描相册")
            }
            state.error?.let {
                Text(
                    "错误: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
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

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("整理进度", style = MaterialTheme.typography.titleMedium)
                    Text("今日已处理 ${state.processedCount} 项")
                    Text("累计释放 ${formatBytes(state.freedBytes)}")
                    Text("当前队列待整理 ${state.remaining} 项")
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = onStartOrganize, modifier = Modifier.fillMaxWidth()) {
                Text("开始整理")
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
