package com.example.photoorganizer.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaType
import com.example.photoorganizer.data.local.AlbumEntity
import com.example.photoorganizer.data.local.MediaStatus
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

/** Slidebox 式单卡整理流：当前照片做完一个决策后自动推进到下一张。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onExit: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var showAddAlbum by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val asset = state.current
        if (asset == null) {
            EmptyQueue(
                title = if (state.queueItems.isEmpty()) "这个队列没有内容" else "这个队列刷完啦",
                onExit = onExit,
                onQueue = { showQueue = true },
            )
        } else {
            FeedPage(
                asset = asset,
                onKeep = { vm.act(MediaStatus.KEEP) },
                onLater = { vm.act(MediaStatus.LATER) },
                onTrash = { vm.act(MediaStatus.TRASH) },
                onFavorite = { vm.act(MediaStatus.FAVORITE) },
                onDragFeedback = { x, y ->
                    dragX = x
                    dragY = y
                },
            )

            TopBar(
                title = state.queueType.label + if (state.queueTitle.isNotEmpty()) " · ${state.queueTitle}" else "",
                remaining = state.remaining,
                trashCount = state.trashCount,
                canUndo = state.undo != null,
                onExit = onExit,
                onUndo = { vm.undoLast() },
                onOpenTrash = onOpenTrash,
                onQueue = { showQueue = true },
            )

            GestureHints(Modifier.align(Alignment.Center))
            SwipeFeedback(
                dragX = dragX,
                dragY = dragY,
                modifier = Modifier.align(Alignment.Center),
            )

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AssetCaption(asset = asset)
                ActionBar(
                    onTrash = { vm.act(MediaStatus.TRASH) },
                    onKeep = { vm.act(MediaStatus.KEEP) },
                    onLater = { vm.act(MediaStatus.LATER) },
                    onFavorite = { vm.act(MediaStatus.FAVORITE) },
                )
                AlbumQuickBar(
                    state = state,
                    onPick = { vm.addCurrentToAlbum(it) },
                    onMore = { showAddAlbum = true },
                )
            }

            UndoBanner(
                visible = state.undo != null,
                onUndo = { vm.undoLast() },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 238.dp),
            )
        }
    }

    if (showAddAlbum && state.current != null) {
        AlbumPickerSheet(
            albums = state.albums,
            counts = state.albumCounts,
            onCreateAndPick = {
                vm.createAlbumAndAddCurrent(it)
                showAddAlbum = false
            },
            onPick = { albumId ->
                vm.addCurrentToAlbum(albumId)
                showAddAlbum = false
            },
            onDismiss = { showAddAlbum = false },
        )
    }

    if (showQueue) {
        QueueFilterDialog(
            state = state,
            albums = emptyList(),
            onFilterType = { vm.setFilter(it, state.filterBucket) },
            onFilterBucket = { vm.setFilter(state.filterType, it) },
            onSelectQueue = {
                vm.selectQueue(it)
                showQueue = false
            },
            onDismiss = { showQueue = false },
        )
    }
}

@Composable
private fun FeedPage(
    asset: MediaAsset,
    onKeep: () -> Unit,
    onLater: () -> Unit,
    onTrash: () -> Unit,
    onFavorite: () -> Unit,
    onDragFeedback: (Float, Float) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(asset.id) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        totalX += amount.x
                        totalY += amount.y
                        onDragFeedback(totalX, totalY)
                    },
                    onDragEnd = {
                        val horizontal = abs(totalX) > abs(totalY)
                        when {
                            horizontal && totalX > 120f -> onKeep()
                            horizontal && totalX < -120f -> onLater()
                            !horizontal && totalY < -120f -> onTrash()
                            !horizontal && totalY > 120f -> onFavorite()
                        }
                        onDragFeedback(0f, 0f)
                    },
                    onDragCancel = {
                        onDragFeedback(0f, 0f)
                    },
                )
            },
    ) {
        if (asset.mediaType == MediaType.VIDEO) {
            VideoPage(uri = asset.uri, active = true, modifier = Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = asset.uri,
                contentDescription = asset.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun SwipeFeedback(
    dragX: Float,
    dragY: Float,
    modifier: Modifier = Modifier,
) {
    val horizontal = abs(dragX) > abs(dragY)
    val active = abs(dragX) > 36f || abs(dragY) > 36f
    if (!active) return
    val label =
        when {
            horizontal && dragX > 0 -> "保留"
            horizontal -> "稍后"
            dragY < 0 -> "加入待删除"
            else -> "收藏"
        }
    val color =
        when (label) {
            "加入待删除" -> Color(0xFFE53935)
            "收藏" -> Color(0xFFFFC107)
            "保留" -> Color(0xFF43A047)
            else -> Color(0xFF42A5F5)
        }
    val alpha = min(0.86f, (maxOf(abs(dragX), abs(dragY)) / 180f).coerceAtLeast(0.28f))
    Box(
        modifier =
            modifier
                .background(color.copy(alpha = alpha), RoundedCornerShape(8.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    remaining: Int,
    trashCount: Int,
    canUndo: Boolean,
    onExit: () -> Unit,
    onUndo: () -> Unit,
    onOpenTrash: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onExit) { Text("关闭", color = Color.White) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("剩余 $remaining", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onUndo, enabled = canUndo) { Text("撤销", color = Color.White) }
        TextButton(onClick = onOpenTrash) { Text("待删 $trashCount", color = Color.White) }
        IconButton(onClick = onQueue) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "队列", tint = Color.White)
        }
    }
}

@Composable
private fun UndoBanner(
    visible: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.74f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已处理当前照片",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUndo) {
            Text("撤销", color = Color.White)
        }
    }
}

@Composable
private fun GestureHints(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(160.dp)) {
        Text("下拉收藏", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(120.dp)) {
            Text("稍后", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelMedium)
            Text("保留", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelMedium)
        }
        Text("上滑待删除", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AssetCaption(asset: MediaAsset) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(asset.displayName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            buildString {
                append(formatBytes(asset.size))
                if (asset.capturedAt > 0) append(" · ${formatDate(asset.capturedAt)}")
                if (asset.bucketName.isNotEmpty()) append(" · ${asset.bucketName}")
            },
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionBar(
    onTrash: () -> Unit,
    onKeep: () -> Unit,
    onLater: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedAction(Icons.Default.DeleteOutline, "待删除", onTrash)
        FeedAction(Icons.Default.Schedule, "稍后", onLater)
        FeedAction(Icons.Default.Check, "保留", onKeep)
        FeedAction(Icons.Default.Star, "收藏", onFavorite)
    }
}

@Composable
private fun AlbumQuickBar(
    state: HomeUiState,
    onPick: (Long) -> Unit,
    onMore: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.54f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(state.albums.take(6)) { album ->
                AlbumChip(
                    text = album.name,
                    onClick = { onPick(album.id) },
                )
            }
            item {
                AlbumChip(
                    text = if (state.albums.isEmpty()) "新建相册" else "更多相册",
                    onClick = onMore,
                )
            }
        }
    }
}

@Composable
private fun AlbumChip(
    text: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = Color.White.copy(alpha = 0.16f),
                labelColor = Color.White,
            ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.34f)),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AlbumPickerSheet(
    albums: List<AlbumEntity>,
    counts: Map<Long, Int>,
    onCreateAndPick: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filtered =
        albums.filter {
            query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("加入相册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索或新建相册") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onCreateAndPick(query) },
                enabled = query.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("新建并加入「${query.trim().ifEmpty { "相册" }}」")
            }
            Text("已有相册", style = MaterialTheme.typography.labelLarge)
            if (filtered.isEmpty()) {
                Text(
                    if (albums.isEmpty()) {
                        "还没有相册，可以先新建一个。"
                    } else {
                        "没有匹配的相册。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.id }) { album ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPick(album.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(album.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${counts[album.id] ?: 0} 项",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text("加入", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EmptyQueue(
    title: String,
    onExit: () -> Unit,
    onQueue: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "换一个短队列，或者回首页看看待删除。",
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onQueue) { Text("选择队列") }
        TextButton(onClick = onExit) { Text("回首页", color = Color.White) }
    }
}

@Composable
private fun FeedAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VideoPage(
    uri: Uri,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player =
        remember(uri) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = false
            }
        }

    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }

    fun toggle() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        playing = player.isPlaying
    }

    LaunchedEffect(active) {
        if (!active) {
            player.pause()
            playing = false
            return@LaunchedEffect
        }
        player.play()
        playing = true
        while (active) {
            if (!scrubbing) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it > 0 } ?: 0L
            }
            playing = player.isPlaying
            delay(300)
        }
    }
    DisposableEffect(uri) { onDispose { player.release() } }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { toggle() },
        )

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 168.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { toggle() }, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Color.White,
                )
            }
            Slider(
                value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = { v ->
                    scrubbing = true
                    position = (v * duration).toLong()
                },
                onValueChangeFinished = {
                    player.seekTo(position)
                    scrubbing = false
                },
                modifier = Modifier.weight(1f).height(20.dp),
            )
            Text(
                "${formatDuration(position)} / ${formatDuration(duration)}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
    }
}
