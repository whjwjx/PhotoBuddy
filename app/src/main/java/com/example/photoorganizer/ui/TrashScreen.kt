package com.example.photoorganizer.ui

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.local.MediaStatus

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
    val allVisibleSelected = visibleItems.isNotEmpty() && visibleIds.all { it in selectedIds }
    val totalBytes = items.sumOf { it.size }
    val visibleBytes = visibleItems.sumOf { it.size }
    val selectedBytes = visibleItems.sumSelectedBytes(selectedIds)

    LaunchedEffect(visibleIds) {
        selectedIds = selectedIds.intersect(visibleIds)
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
            CompactTopAppBar(
                title = { Text("待删除复核") },
                navigationIcon = {
                    TextButton(onClick = onExit) { Text("返回") }
                },
                actions = {
                    if (visibleItems.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedIds =
                                    if (allVisibleSelected) emptySet() else visibleIds
                            },
                        ) {
                            Text(if (allVisibleSelected) "全不选" else "全选")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (selectedIds.isNotEmpty()) {
                TrashActionBar(
                    selectedCount = selectedIds.size,
                    selectedBytes = selectedBytes,
                    onRestore = {
                        vm.restoreFromTrash(selectedIds)
                        selectedIds = emptySet()
                    },
                    onDelete = {
                        val ids = selectedIds
                        vm.requestDeleteTrash(ids)
                        selectedIds = emptySet()
                    },
                )
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
                TrashReviewHeader(
                    title =
                        if (sourceFilter == null) {
                            "待删除复核"
                        } else {
                            sourceFilter.orEmpty()
                        },
                    totalCount = items.size,
                    totalBytes = totalBytes,
                    visibleCount = visibleItems.size,
                    visibleBytes = visibleBytes,
                    selectedCount = selectedIds.size,
                    selectedBytes = selectedBytes,
                    filtered = sourceFilter != null,
                    onRestoreAll = {
                        vm.restoreFromTrash(visibleIds)
                        selectedIds = emptySet()
                    },
                )
                state.error?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                message,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { vm.clearError() }) {
                                Text("知道了")
                            }
                        }
                    }
                }
                val pendingCount = state.trashDeleteIdsInFlight.size
                if (pendingCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    TrashStatusCard(
                        title = "等待系统处理 $pendingCount 项",
                        body = "系统确认完成后会重新扫描并校验结果，未成功移除的照片会继续留在这里。",
                        inProgress = true,
                    )
                } else {
                    state.feedbackMessage?.let { message ->
                        Spacer(Modifier.height(10.dp))
                        TrashStatusCard(
                            title = message,
                            body = "你可以继续复核待删除照片，或返回整理页继续刷卡。",
                            onDismiss = { vm.clearFeedback() },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SourceFilterRow(
                    options = sourceOptions,
                    selected = sourceFilter,
                    onSelect = { sourceFilter = it },
                )
                Spacer(Modifier.height(8.dp))
                TrashSortRow(sort = sort, onSelect = { sort = it })
                Spacer(Modifier.height(8.dp))
                if (visibleItems.isEmpty()) {
                    EmptyFilteredTrash(Modifier.fillMaxSize())
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
            onKeep = {
                vm.restoreFromTrash(setOf(asset.id), MediaStatus.KEEP)
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
private fun TrashStatusCard(
    title: String,
    body: String,
    inProgress: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (inProgress) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            onDismiss?.let {
                TextButton(onClick = it) {
                    Text("知道了")
                }
            }
        }
    }
}

@Composable
private fun SourceFilterRow(
    options: List<Pair<String, Int>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
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

@Composable
private fun TrashSortRow(
    sort: TrashSort,
    onSelect: (TrashSort) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f), RoundedCornerShape(8.dp))
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        TrashSort.entries.forEach { option ->
            val selected = sort == option
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(
                            if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { onSelect(option) }
                        .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (selected) "${option.label}优先" else option.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TrashActionBar(
    selectedCount: Int,
    selectedBytes: Long,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "已选 $selectedCount 项待复核",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "预计释放 ${formatBytes(selectedBytes)}，系统确认后才计入已释放",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("恢复", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "移入最近删除" else "永久删除",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrashTile(
    asset: MediaAsset,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Card(
        modifier =
            Modifier
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = onOpen,
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        },
                    shape = shape,
                ),
        shape = shape,
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.92f)) {
            Column(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = asset.uri,
                    contentDescription = asset.displayName,
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
            if (selected) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)))
            }
            SelectionBadge(
                selected = selected,
                onClick = onToggle,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
    }
}

@Composable
private fun SelectionBadge(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(28.dp)
                .background(Color.White.copy(alpha = 0.92f), CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = if (selected) "取消选择" else "选择",
            tint =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun TrashPreviewDialog(
    asset: MediaAsset,
    onRestore: () -> Unit,
    onKeep: () -> Unit,
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
                Text(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "移入最近删除" else "永久删除")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRestore) { Text("恢复到未整理") }
                TextButton(onClick = onKeep) { Text("标记保留") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@Composable
private fun TrashReviewHeader(
    title: String,
    totalCount: Int,
    totalBytes: Long,
    visibleCount: Int,
    visibleBytes: Long,
    selectedCount: Int,
    selectedBytes: Long,
    filtered: Boolean,
    onRestoreAll: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "点图选择，长按预览；确认前不会删除",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onRestoreAll, enabled = visibleCount > 0) {
                    Text(if (filtered) "恢复本来源" else "全部恢复")
                }
            }
            Text(
                trashCompactSummary(
                    filtered = filtered,
                    visibleCount = visibleCount,
                    totalCount = totalCount,
                    visibleBytes = visibleBytes,
                    totalBytes = totalBytes,
                    selectedCount = selectedCount,
                    selectedBytes = selectedBytes,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun trashCompactSummary(
    filtered: Boolean,
    visibleCount: Int,
    totalCount: Int,
    visibleBytes: Long,
    totalBytes: Long,
    selectedCount: Int,
    selectedBytes: Long,
): String {
    val scopeText = if (filtered) "当前来源 $visibleCount 项" else "全部待删 $totalCount 项"
    val bytes = if (filtered) visibleBytes else totalBytes
    val deleteMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "最近删除" else "永久删除"
    val selectedText =
        if (selectedCount > 0) {
            "已选 $selectedCount 项 ${formatBytes(selectedBytes)}"
        } else {
            "点照片选择"
        }
    return "$scopeText · 待释放 ${formatBytes(bytes)} · $selectedText · $deleteMode"
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
