package com.example.photoorganizer.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.photoorganizer.data.MediaAsset

private enum class TrashSort(val label: String) {
    NEWEST("最新"),
    LARGEST("最大"),
    OLDEST("最早"),
}

/** 待删除复核页：真正系统删除前的最后缓冲层。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onExit: () -> Unit) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var sort by remember { mutableStateOf(TrashSort.NEWEST) }
    var sourceFilter by remember { mutableStateOf<String?>(null) }
    var previewAsset by remember { mutableStateOf<MediaAsset?>(null) }
    val items =
        when (sort) {
            TrashSort.NEWEST -> state.trashItems.sortedByDescending { it.capturedAt }
            TrashSort.LARGEST -> state.trashItems.sortedByDescending { it.size }
            TrashSort.OLDEST -> state.trashItems.sortedBy { if (it.capturedAt > 0) it.capturedAt else Long.MAX_VALUE }
        }
    val sourceOptions =
        remember(items) {
            items
                .groupBy { it.sourceName() }
                .map { (name, sourceItems) -> name to sourceItems.size }
                .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        }
    val visibleItems =
        remember(items, sourceFilter) {
            if (sourceFilter == null) {
                items
            } else {
                items.filter { it.sourceName() == sourceFilter }
            }
        }
    val visibleIds = remember(visibleItems) { visibleItems.map { it.id }.toSet() }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    LaunchedEffect(visibleIds) {
        selectedIds = visibleIds
    }

    val deleteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) vm.onDeleteApproved() else vm.clearPendingDelete()
        }
    LaunchedEffect(state.pendingDelete) {
        state.pendingDelete?.let { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待删除复核") },
                navigationIcon = {
                    TextButton(onClick = onExit) { Text("返回") }
                },
                actions = {
                    if (visibleItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedIds =
                                    if (visibleIds.all { it in selectedIds }) emptySet() else visibleIds
                            },
                        ) {
                            Text(if (visibleIds.all { it in selectedIds }) "全不选" else "全选")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (visibleItems.isNotEmpty()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "已选 ${selectedIds.size} 项 · 预计释放 ${formatBytes(visibleItems.sumSelectedBytes(selectedIds))}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                vm.restoreFromTrash(selectedIds)
                                selectedIds = emptySet()
                            },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("恢复")
                        }
                        Button(
                            onClick = { vm.requestDeleteTrash(selectedIds) },
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("确认删除")
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyTrash(Modifier.padding(padding))
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp),
            ) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (sourceFilter == null) {
                                "${items.size} 项等待确认"
                            } else {
                                "${visibleItems.size} 项来自 $sourceFilter"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            deletePolicyText(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SourceFilterRow(
                    options = sourceOptions,
                    selected = sourceFilter,
                    onSelect = { sourceFilter = it },
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TrashSort.entries.forEach { option ->
                        OutlinedButton(
                            onClick = { sort = option },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (sort == option) "${option.label}优先" else option.label)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (visibleItems.isEmpty()) {
                    EmptyFilteredTrash(Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visibleItems, key = { it.id }) { asset ->
                            TrashTile(
                                asset = asset,
                                selected = asset.id in selectedIds,
                                onOpen = { previewAsset = asset },
                                onToggle = {
                                    selectedIds =
                                        if (asset.id in selectedIds) {
                                            selectedIds - asset.id
                                        } else {
                                            selectedIds + asset.id
                                        }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    previewAsset?.let { asset ->
        TrashPreviewDialog(
            asset = asset,
            onRestore = {
                vm.restoreFromTrash(setOf(asset.id))
                selectedIds = selectedIds - asset.id
                previewAsset = null
            },
            onDelete = {
                vm.requestDeleteTrash(setOf(asset.id))
                previewAsset = null
            },
            onDismiss = { previewAsset = null },
        )
    }
}

@Composable
private fun SourceFilterRow(
    options: List<Pair<String, Int>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "按来源相册复核",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                FilterChipButton(
                    text = "全部 · ${options.sumOf { it.second }}",
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
            }
            lazyItems(options, key = { it.first }) { (name, count) ->
                FilterChipButton(
                    text = "$name · $count",
                    selected = selected == name,
                    onClick = { onSelect(name) },
                )
            }
        }
    }
}

@Composable
private fun TrashTile(
    asset: MediaAsset,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box {
            Column {
                AsyncImage(
                    model = asset.uri,
                    contentDescription = asset.displayName,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    asset.displayName,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun TrashPreviewDialog(
    asset: MediaAsset,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(asset.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(
                    model = asset.uri,
                    contentDescription = asset.displayName,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    buildString {
                        append(formatBytes(asset.size))
                        if (asset.capturedAt > 0) append(" · ${formatDate(asset.capturedAt)}")
                        if (asset.bucketName.isNotEmpty()) append(" · ${asset.bucketName}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDelete) {
                Text("确认删除")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRestore) { Text("恢复") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun EmptyTrash(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "待删除是空的",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "整理页上滑的照片会先放在这里，确认后才交给系统删除。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyFilteredTrash(modifier: Modifier = Modifier) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "这个来源没有待删除照片",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "换一个来源相册，或回到全部继续复核。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

private fun List<MediaAsset>.sumSelectedBytes(selectedIds: Set<Long>): Long =
    filter { it.id in selectedIds }.sumOf { it.size }

private fun MediaAsset.sourceName(): String =
    bucketName.ifBlank { "未知来源" }

private fun deletePolicyText(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        "确认前不会删除。确认后会请求系统移入最近删除，可在系统相册中恢复；若系统拒绝，本页会保留待删除状态。"
    } else {
        "确认前不会删除。当前系统可能不支持最近删除，确认后可能从设备永久删除。"
    }
