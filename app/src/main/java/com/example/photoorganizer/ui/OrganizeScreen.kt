package com.example.photoorganizer.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.photoorganizer.data.MediaType
import com.example.photoorganizer.data.local.AlbumEntity
import com.example.photoorganizer.data.local.MediaStatus
import com.example.photoorganizer.domain.StatsService

/** 整理页：筛选 + 队列 + 卡片流 + 加入相册 + 批量确认（PRD 六·整理 / 5.5 / 5.6）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen() {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddAlbum by remember { mutableStateOf(false) }

    // 单个删除：系统删除确认弹窗
    val deleteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) vm.onDeleteApproved() else vm.clearPendingDelete()
        }
    LaunchedEffect(state.pendingDelete) {
        state.pendingDelete?.let { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    // 批量删除：系统删除确认弹窗
    val batchLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) vm.onBatchApproved() else vm.clearPendingBatch()
        }
    LaunchedEffect(state.pendingBatchDelete) {
        state.pendingBatchDelete?.let { sender ->
            batchLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("整理") }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
        ) {
            Button(onClick = { vm.scan() }, enabled = !state.isScanning) {
                Text(if (state.isScanning) "扫描中…" else "重新扫描")
            }
            state.error?.let {
                Text(
                    "错误: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (state.allAssets.isEmpty()) {
                Text("还没有扫描到媒体，请回首页扫描相册。", Modifier.padding(top = 16.dp))
                return@Column
            }

            val stats = remember(state.assets) { StatsService.compute(state.assets) }
            Spacer(Modifier.height(8.dp))
            Text(
                "共 ${stats.total} 项 · 图片 ${stats.images} · 视频 ${stats.videos} · 占用 ${formatBytes(stats.totalBytes)}",
            )
            Text(
                "已处理 ${state.processedCount} · 待整理 ${state.remaining} · 已释放 ${formatBytes(state.freedBytes)}",
                modifier = Modifier.padding(top = 4.dp),
            )

            // 筛选：媒体类型
            Spacer(Modifier.height(12.dp))
            Text("筛选 · 类型", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipButton("全部", state.filterType == null) { vm.setFilter(null, state.filterBucket) }
                FilterChipButton("图片", state.filterType == MediaType.IMAGE) {
                    vm.setFilter(MediaType.IMAGE, state.filterBucket)
                }
                FilterChipButton("视频", state.filterType == MediaType.VIDEO) {
                    vm.setFilter(MediaType.VIDEO, state.filterBucket)
                }
            }

            // 筛选：相册
            val albums =
                remember(state.allAssets) {
                    state.allAssets.map { it.bucketId to it.bucketName }.distinct()
                }
            Spacer(Modifier.height(8.dp))
            Text("筛选 · 相册", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChipButton("全部相册", state.filterBucket == null) {
                        vm.setFilter(state.filterType, null)
                    }
                }
                items(albums) { (id, name) ->
                    FilterChipButton(name.ifBlank { "未命名相册" }, state.filterBucket == id) {
                        vm.setFilter(state.filterType, id)
                    }
                }
            }

            // 整理队列
            Spacer(Modifier.height(12.dp))
            Text("整理队列", style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.queues) { q ->
                    FilterChipButton(
                        "${q.displayName} (${q.items.size})",
                        q.type == state.queueType && q.title == state.queueTitle,
                    ) { vm.selectQueue(q) }
                }
            }

            // 批量确认入口
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.enterBatch() },
                enabled = state.queueItems.isNotEmpty(),
            ) { Text("批量确认删除") }

            Spacer(Modifier.height(12.dp))
            val current = state.current
            if (current == null) {
                Text(
                    "本队列已整理完成！累计释放 ${formatBytes(state.freedBytes)}。",
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        AsyncImage(
                            model = current.uri,
                            contentDescription = current.displayName,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(current.displayName, style = MaterialTheme.typography.titleMedium)
                        val meta =
                            buildString {
                                append("${current.width}x${current.height} · ${formatBytes(current.size)}")
                                if (current.capturedAt > 0) append(" · ${formatDate(current.capturedAt)}")
                            }
                        Text(meta, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Button(onClick = { vm.act(MediaStatus.KEEP) }) { Text("保留") }
                    Button(
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        onClick = { showDeleteConfirm = true },
                    ) { Text("删除") }
                    Button(onClick = { vm.act(MediaStatus.LATER) }) { Text("稍后") }
                    Button(onClick = { vm.act(MediaStatus.PERMANENT) }) { Text("永久保留") }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showAddAlbum = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("加入相册")
                }
            }
        }
    }

    // 删除二次确认（PRD 4.5）
    if (showDeleteConfirm && state.current != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = {
                Text(
                    "将删除《${state.current!!.displayName}》并移入系统最近删除（可在相册回收站恢复）。" +
                        "此操作不能被本 App 撤销。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    vm.requestDelete()
                }) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showAddAlbum && state.current != null) {
        AddToAlbumDialog(
            albums = state.albums,
            counts = state.albumCounts,
            onCreate = { vm.createAlbum(it) },
            onPick = { albumId ->
                vm.addCurrentToAlbum(albumId)
                showAddAlbum = false
            },
            onDismiss = { showAddAlbum = false },
        )
    }

    if (state.showBatch) {
        BatchConfirmDialog(
            state = state,
            onToggle = { vm.toggleSelect(it) },
            onSelectAll = { vm.selectAllBatch(it) },
            onConfirm = { vm.confirmBatch() },
            onDismiss = { vm.exitBatch() },
        )
    }
}

@Composable
private fun AddToAlbumDialog(
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
private fun BatchConfirmDialog(
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
