package com.example.photoorganizer.ui

import android.app.Activity
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
    var previewAsset by remember { mutableStateOf<MediaAsset?>(null) }
    val items =
        when (sort) {
            TrashSort.NEWEST -> state.trashItems.sortedByDescending { it.capturedAt }
            TrashSort.LARGEST -> state.trashItems.sortedByDescending { it.size }
            TrashSort.OLDEST -> state.trashItems.sortedBy { if (it.capturedAt > 0) it.capturedAt else Long.MAX_VALUE }
        }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    LaunchedEffect(items.map { it.id }) {
        selectedIds = items.map { it.id }.toSet()
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
                    if (items.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedIds =
                                    if (selectedIds.size == items.size) emptySet() else items.map { it.id }.toSet()
                            },
                        ) {
                            Text(if (selectedIds.size == items.size) "全不选" else "全选")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (items.isNotEmpty()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "已选 ${selectedIds.size} 项 · 预计释放 ${formatBytes(items.sumSelectedBytes(selectedIds))}",
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
                        Text("${items.size} 项等待确认", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "确认前不会删除。支持系统回收站时，会先移入最近删除。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { asset ->
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

private fun List<MediaAsset>.sumSelectedBytes(selectedIds: Set<Long>): Long =
    filter { it.id in selectedIds }.sumOf { it.size }
