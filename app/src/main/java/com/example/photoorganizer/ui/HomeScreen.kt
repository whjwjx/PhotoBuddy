package com.example.photoorganizer.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.photoorganizer.R
import com.example.photoorganizer.data.MediaType
import com.example.photoorganizer.data.local.MediaStatus
import com.example.photoorganizer.domain.StatsService
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val permissions = remember { requiredPermissions() }
    var granted by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            granted = result.values.all { it }
        }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // API 30+：系统删除确认弹窗（MediaStore.createDeleteRequest），用户允许后系统自动移入最近删除
    val deleteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                vm.onDeleteApproved()
            } else {
                vm.clearPendingDelete()
            }
        }
    LaunchedEffect(state.pendingDelete) {
        state.pendingDelete?.let { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            if (!granted) {
                Button(onClick = { launcher.launch(permissions.toTypedArray()) }) {
                    Text("授权读取相册")
                }
                Text("需要相册读取权限才能扫描本地照片和视频。", Modifier.padding(top = 8.dp))
                return@Column
            }

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

            if (state.allAssets.isEmpty()) {
                Text("还没有扫描到媒体，点上方按钮开始。", Modifier.padding(top = 16.dp))
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

            // 筛选：媒体类型（PRD 5.1 按媒体类型筛选）
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

            // 筛选：相册（PRD 5.1 按相册筛选）
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

            // 整理队列（PRD 4.3）
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
                            modifier = Modifier.fillMaxWidth().height(260.dp),
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
            }
        }
    }

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
}

@Composable
private fun FilterChipButton(
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

private fun requiredPermissions(): List<String> =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        // API 29-：直接删除需要写权限
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P -> listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun formatBytes(bytes: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        bytes >= 1024L * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024 * 1024))} GB"
        bytes >= 1024L * 1024 -> "${df.format(bytes / (1024.0 * 1024))} MB"
        bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}

private fun formatDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
