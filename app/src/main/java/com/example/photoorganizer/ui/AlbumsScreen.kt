package com.example.photoorganizer.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.local.AlbumEntity

/** 应用内相册页：负责查看、重命名、删除相册，以及从相册中批量移除媒体。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen() {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var name by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val openAlbum = state.albums.firstOrNull { it.id == state.openAlbumId }
    val openItems =
        openAlbum
            ?.let { album -> state.openAlbumMediaIds.mapNotNull { id -> state.allAssets.firstOrNull { it.id == id } } }
            .orEmpty()

    LaunchedEffect(openAlbum?.id, openItems.map { it.id }) {
        selectedIds = selectedIds.intersect(openItems.map { it.id }.toSet())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(openAlbum?.name ?: "相册") },
                navigationIcon = {
                    if (openAlbum != null) {
                        TextButton(onClick = { vm.closeAlbum() }) { Text("返回") }
                    }
                },
                actions = {
                    if (openAlbum != null) {
                        IconButton(onClick = { renameTarget = openAlbum }) {
                            Icon(Icons.Default.Edit, contentDescription = "重命名")
                        }
                        IconButton(onClick = { deleteTarget = openAlbum }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "删除相册")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (openAlbum == null) {
            AlbumList(
                state = state,
                draftName = name,
                onDraftChange = { name = it },
                onCreate = {
                    vm.createAlbum(name)
                    name = ""
                },
                onOpen = { vm.openAlbum(it) },
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
                modifier = Modifier.padding(padding),
            )
        } else {
            AlbumDetail(
                album = openAlbum,
                items = openItems,
                selectedIds = selectedIds,
                onToggle = { id ->
                    selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                },
                onSelectAll = {
                    selectedIds = if (selectedIds.size == openItems.size) emptySet() else openItems.map { it.id }.toSet()
                },
                onRemoveSelected = {
                    vm.removeFromAlbum(openAlbum.id, selectedIds)
                    selectedIds = emptySet()
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    renameTarget?.let { album ->
        RenameAlbumDialog(
            album = album,
            onConfirm = { newName ->
                vm.renameAlbum(album.id, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { album ->
        DeleteAlbumDialog(
            album = album,
            count = state.albumCounts[album.id] ?: 0,
            onConfirm = {
                vm.deleteAlbum(album.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun AlbumList(
    state: HomeUiState,
    draftName: String,
    onDraftChange: (String) -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (AlbumEntity) -> Unit,
    onDelete: (AlbumEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("新建相册", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = onDraftChange,
                        label = { Text("相册名称") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = onCreate, enabled = draftName.trim().isNotEmpty()) {
                        Text("新建")
                    }
                }
            }
        }

        if (state.albums.isEmpty()) {
            EmptyAlbums()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.albums, key = { it.id }) { album ->
                    val cover = state.albumMediaIds[album.id]?.firstNotNullOfOrNull { id ->
                        state.allAssets.firstOrNull { it.id == id }
                    }
                    AlbumRow(
                        album = album,
                        cover = cover,
                        count = state.albumCounts[album.id] ?: 0,
                        lastAddedAt = state.albumLastAddedAt[album.id] ?: 0L,
                        onOpen = { onOpen(album.id) },
                        onRename = { onRename(album) },
                        onDelete = { onDelete(album) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(
    album: AlbumEntity,
    cover: MediaAsset?,
    count: Int,
    lastAddedAt: Long,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlbumCover(cover = cover, modifier = Modifier.size(72.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(album.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$count 项", style = MaterialTheme.typography.bodySmall)
                Text(
                    if (lastAddedAt > 0) "最近添加 ${formatDate(lastAddedAt)}" else "暂无内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "重命名")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "删除相册")
            }
        }
    }
}

@Composable
private fun AlbumCover(
    cover: MediaAsset?,
    modifier: Modifier = Modifier,
) {
    if (cover == null) {
        Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PhotoAlbum, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AsyncImage(
            model = cover.uri,
            contentDescription = cover.displayName,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun AlbumDetail(
    album: AlbumEntity,
    items: List<MediaAsset>,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onRemoveSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${items.size} 项", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (selectedIds.isEmpty()) "点选照片可批量移出相册" else "已选 ${selectedIds.size} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onSelectAll, enabled = items.isNotEmpty()) {
                    Text(if (selectedIds.size == items.size && items.isNotEmpty()) "全不选" else "全选")
                }
                OutlinedButton(onClick = onRemoveSelected, enabled = selectedIds.isNotEmpty()) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("移出")
                }
            }
        }

        if (items.isEmpty()) {
            EmptyAlbum(album.name, Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.id }) { asset ->
                    AlbumMediaTile(
                        asset = asset,
                        selected = asset.id in selectedIds,
                        onToggle = { onToggle(asset.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumMediaTile(
    asset: MediaAsset,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.clickable(onClick = onToggle),
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
private fun EmptyAlbums() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("还没有相册", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "在整理页加入相册，或者先创建一个常用分类。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyAlbum(
    albumName: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("「$albumName」暂无内容", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "去整理页把照片加入这个相册。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RenameAlbumDialog(
    album: AlbumEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(album.id) { mutableStateOf(album.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名相册") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("相册名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.trim().isNotEmpty()) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun DeleteAlbumDialog(
    album: AlbumEntity,
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除相册？") },
        text = {
            Text("只删除「${album.name}」这个 App 内相册和其中 $count 条归类记录，不会删除照片文件。")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("删除相册")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
