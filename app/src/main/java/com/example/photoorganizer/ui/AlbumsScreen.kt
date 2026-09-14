package com.example.photoorganizer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var mappingTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var mergeTarget by remember { mutableStateOf<AlbumEntity?>(null) }
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
                        IconButton(onClick = { mergeTarget = openAlbum }) {
                            Icon(Icons.Default.PhotoAlbum, contentDescription = "合并相册")
                        }
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
                query = query,
                onDraftChange = { name = it },
                onQueryChange = { query = it },
                onCreate = {
                    vm.createAlbum(name)
                    name = ""
                },
                onOpen = { vm.openAlbum(it) },
                onRename = { renameTarget = it },
                onDelete = { deleteTarget = it },
                onMerge = { mergeTarget = it },
                onShowMapping = { mappingTarget = it },
                onTogglePin = { albumId, pinned -> vm.setAlbumPinned(albumId, pinned) },
                onToggleHidden = { albumId, hidden -> vm.setAlbumHidden(albumId, hidden) },
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

    mappingTarget?.let { album ->
        AlbumMappingDialog(
            album = album,
            onDismiss = { mappingTarget = null },
        )
    }

    mergeTarget?.let { album ->
        MergeAlbumDialog(
            album = album,
            albums = state.albums,
            count = state.albumCounts[album.id] ?: 0,
            onConfirm = { targetAlbumId ->
                vm.mergeAlbum(album.id, targetAlbumId)
                mergeTarget = null
            },
            onDismiss = { mergeTarget = null },
        )
    }
}

@Composable
private fun AlbumList(
    state: HomeUiState,
    draftName: String,
    query: String,
    onDraftChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onCreate: () -> Unit,
    onOpen: (Long) -> Unit,
    onRename: (AlbumEntity) -> Unit,
    onDelete: (AlbumEntity) -> Unit,
    onMerge: (AlbumEntity) -> Unit,
    onShowMapping: (AlbumEntity) -> Unit,
    onTogglePin: (Long, Boolean) -> Unit,
    onToggleHidden: (Long, Boolean) -> Unit,
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
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("App 内标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "这些相册用于整理流快速归类，不会移动系统相册里的文件；置顶和最近使用会影响整理页快捷区。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

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

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("搜索相册") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val sortedAlbums =
            remember(state.albums, state.albumCounts, state.albumLastAddedAt, state.pinnedAlbumIds, state.hiddenAlbumIds) {
                sortAlbumsForManagement(
                    albums = state.albums,
                    counts = state.albumCounts,
                    lastAddedAt = state.albumLastAddedAt,
                    pinnedAlbumIds = state.pinnedAlbumIds,
                    hiddenAlbumIds = state.hiddenAlbumIds,
                )
            }
        val visibleAlbums =
            sortedAlbums.filter { album ->
                query.isBlank() || album.name.contains(query.trim(), ignoreCase = true)
            }

        if (state.albums.isEmpty()) {
            EmptyAlbums()
        } else if (visibleAlbums.isEmpty()) {
            EmptyAlbumSearch(query = query)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleAlbums, key = { it.id }) { album ->
                    val cover = state.albumMediaIds[album.id]?.firstNotNullOfOrNull { id ->
                        state.allAssets.firstOrNull { it.id == id }
                    }
                    AlbumRow(
                        album = album,
                        cover = cover,
                        count = state.albumCounts[album.id] ?: 0,
                        lastAddedAt = state.albumLastAddedAt[album.id] ?: 0L,
                        pinned = album.id in state.pinnedAlbumIds,
                        hidden = album.id in state.hiddenAlbumIds,
                        onOpen = { onOpen(album.id) },
                        onRename = { onRename(album) },
                        onDelete = { onDelete(album) },
                        onMerge = { onMerge(album) },
                        onShowMapping = { onShowMapping(album) },
                        onTogglePin = { onTogglePin(album.id, album.id !in state.pinnedAlbumIds) },
                        onToggleHidden = { onToggleHidden(album.id, album.id !in state.hiddenAlbumIds) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumRow(
    album: AlbumEntity,
    cover: MediaAsset?,
    count: Int,
    lastAddedAt: Long,
    pinned: Boolean,
    hidden: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit,
    onShowMapping: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = { menuExpanded = true },
                ),
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
                Text(
                    buildString {
                        append("$count 项")
                        if (pinned) append(" · 已置顶")
                        if (hidden) append(" · 已隐藏于快捷区")
                        append(" · App 内标签")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (lastAddedAt > 0) "最近添加 ${formatDate(lastAddedAt)}" else "暂无内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "管理相册")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (pinned) "取消置顶" else "置顶到整理页") },
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (hidden) "显示在整理页" else "从整理页隐藏") },
                        onClick = {
                            menuExpanded = false
                            onToggleHidden()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("合并到…") },
                        onClick = {
                            menuExpanded = false
                            onMerge()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("映射说明") },
                        onClick = {
                            menuExpanded = false
                            onShowMapping()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除相册") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
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
                        if (selectedIds.isEmpty()) {
                            "App 内标签，移出不会删除原照片"
                        } else {
                            "已选 ${selectedIds.size} 项"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (items.isNotEmpty()) {
                    TextButton(onClick = onSelectAll) {
                        Text(if (selectedIds.size == items.size) "全不选" else "全选")
                    }
                }
                if (items.isNotEmpty()) {
                    OutlinedButton(onClick = onRemoveSelected, enabled = selectedIds.isNotEmpty()) {
                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("移出")
                    }
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
private fun EmptyAlbumSearch(query: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("没有找到「$query」", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "可以换个关键词，或者用上方输入框新建相册。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
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

private fun sortAlbumsForManagement(
    albums: List<AlbumEntity>,
    counts: Map<Long, Int>,
    lastAddedAt: Map<Long, Long>,
    pinnedAlbumIds: Set<Long>,
    hiddenAlbumIds: Set<Long>,
): List<AlbumEntity> =
    albums.sortedWith(
        compareByDescending<AlbumEntity> { if (it.id in pinnedAlbumIds) 1 else 0 }
            .thenBy { if (it.id in hiddenAlbumIds) 1 else 0 }
            .thenByDescending { lastAddedAt[it.id] ?: 0L }
            .thenByDescending { counts[it.id] ?: 0 }
            .thenByDescending { it.createdAt }
            .thenBy { it.name },
    )

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
private fun MergeAlbumDialog(
    album: AlbumEntity,
    albums: List<AlbumEntity>,
    count: Int,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val targetAlbums = albums.filter { it.id != album.id }.sortedBy { it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("合并「${album.name}」") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "会把 $count 条 App 内归类记录合并到目标相册，然后删除「${album.name}」。不会移动或删除系统照片文件。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (targetAlbums.isEmpty()) {
                    Text(
                        "还没有其它相册可合并。请先新建一个目标相册。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(targetAlbums, key = { it.id }) { target ->
                            TextButton(
                                onClick = { onConfirm(target.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "合并到「${target.name}」",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun AlbumMappingDialog(
    album: AlbumEntity,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("相册映射说明") },
        text = {
            Text(
                "「${album.name}」目前是 App 内标签。加入、移出或删除这个相册，只会改变本 App 的归类记录，" +
                    "不会移动、复制或删除系统相册里的照片文件。置顶只影响整理页底部快捷区。",
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("知道了")
            }
        },
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
