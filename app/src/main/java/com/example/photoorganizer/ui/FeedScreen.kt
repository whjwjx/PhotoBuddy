package com.example.photoorganizer.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaType
import com.example.photoorganizer.data.local.MediaStatus

/**
 * 沉浸式的「刷照片」流（借鉴 Slidebox 刷卡整理 + 抖音式全屏沉浸体验）。
 *
 * 交互约定：
 * - 上下滑动：切换照片/视频（抖音式）
 * - 右滑：保留；左滑：稍后处理（Slidebox 式手势决策，但避开与竖向切换冲突）
 * - 删除：必须点按钮走二次确认 + 系统删除确认（PRD 4.5 安全优先，绝不允许误触删除）
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(onExit: () -> Unit) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    // 沉浸式：进入时隐藏系统栏，退出时恢复
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddAlbum by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val deleteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) vm.onDeleteApproved() else vm.clearPendingDelete()
        }
    LaunchedEffect(state.pendingDelete) {
        state.pendingDelete?.let { sender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    val batchLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) vm.onBatchApproved() else vm.clearPendingBatch()
        }
    LaunchedEffect(state.pendingBatchDelete) {
        state.pendingBatchDelete?.let { sender ->
            batchLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    val items = state.queueItems
    val pagerState =
        rememberPagerState(initialPage = state.currentIndex.coerceAtMost(items.size)) { items.size }

    LaunchedEffect(pagerState.currentPage) { vm.setIndex(pagerState.currentPage) }
    LaunchedEffect(state.currentIndex) {
        // 越界保护：队列刷完时 currentIndex == items.size，此时不能滚动
        if (state.currentIndex < items.size && pagerState.currentPage != state.currentIndex) {
            pagerState.animateScrollToPage(state.currentIndex)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (items.isEmpty()) {
            Text(
                "这个队列没有内容，换一个队列或回首页重新扫描。",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else {
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val asset = items.getOrNull(page)
                if (asset != null) {
                    FeedPage(
                        asset = asset,
                        active = page == pagerState.currentPage,
                        onKeep = { vm.act(MediaStatus.KEEP) },
                        onLater = { vm.act(MediaStatus.LATER) },
                    )
                }
            }
        }

        // 队列刷完：给出完成态与下一步入口，而不是一片空白
        if (items.isNotEmpty() && state.current == null) {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "这个队列刷完啦！",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "累计释放 ${formatBytes(state.freedBytes)}",
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { state.queues.firstOrNull()?.let { vm.selectQueue(it) } }) {
                    Text("换一个队列")
                }
                TextButton(onClick = onExit) { Text("回首页", color = Color.White) }
            }
        }

        // 顶部浮层：返回、队列/筛选、批量
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onExit) { Text("‹ 首页", color = Color.White) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showQueue = true }) { Text("队列/筛选", color = Color.White) }
            TextButton(onClick = { vm.enterBatch() }) { Text("批量", color = Color.White) }
        }

        val asset = state.current
        if (asset != null) {
            // 右侧竖排操作栏（抖音式）
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FeedAction(Icons.Default.Favorite, "保留") { vm.act(MediaStatus.KEEP) }
                FeedAction(Icons.Default.Delete, "删除") { showDeleteConfirm = true }
                FeedAction(Icons.Default.Schedule, "稍后") { vm.act(MediaStatus.LATER) }
                FeedAction(Icons.Default.Lock, "永久保留") { vm.act(MediaStatus.PERMANENT) }
                FeedAction(Icons.Default.AddToPhotos, "加入相册") { showAddAlbum = true }
            }

            // 底部信息条（文件名 / 时间 / 大小 / 来源队列 / 进度）
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    asset.displayName,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(formatBytes(asset.size))
                        if (asset.capturedAt > 0) append(" · ${formatDate(asset.capturedAt)}")
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "来源：${state.queueType.label}" +
                        (if (state.queueTitle.isNotEmpty()) " · ${state.queueTitle}" else "") +
                        " · ${state.currentIndex + 1}/${items.size}" +
                        " · 已释放 ${formatBytes(state.freedBytes)}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "右滑保留 · 左滑稍后",
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                )
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
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } },
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

    if (showQueue) {
        QueueFilterDialog(
            state = state,
            albums = emptyList(),
            onFilterType = { vm.setFilter(it, state.filterBucket) },
            onFilterBucket = { vm.setFilter(state.filterType, it) },
            onSelectQueue = { vm.selectQueue(it) },
            onDismiss = { showQueue = false },
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

/** 单页内容：图片或视频；视频仅在成为当前页时自动播放。 */
@Composable
private fun FeedPage(
    asset: MediaAsset,
    active: Boolean,
    onKeep: () -> Unit,
    onLater: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(asset.id) {
                var dragTotal = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onHorizontalDrag = { _, amount -> dragTotal += amount },
                    onDragEnd = {
                        when {
                            dragTotal > 120f -> onKeep()
                            dragTotal < -120f -> onLater()
                        }
                    },
                )
            },
    ) {
        if (asset.mediaType == MediaType.VIDEO) {
            VideoPage(uri = asset.uri, active = active, modifier = Modifier.fillMaxSize())
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

/**
 * 视频页：Media3/ExoPlayer 自动播放 + 播放控制（PRD 5.5 / 8.2）。
 * - 成为当前页自动播放，离开自动暂停
 * - 点击画面暂停/继续
 * - 底部进度条可拖动 seek，显示当前时间/总时长
 */
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

        // 点击画面切换播放/暂停
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { toggle() },
        )

        // 底部播放控制条：抬高 104dp 避开底部信息文字，同时不贴屏幕边缘（避免触发系统手势）
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, bottom = 104.dp)
                .background(
                    Color.Black.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.small,
                ).padding(horizontal = 4.dp),
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
                // 限制高度，默认 48dp 会把控制条撑得过高
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

@Composable
private fun FeedAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // 整块（图标+文字）都可点击，避免用户点到文字没反应
    Column(
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}
