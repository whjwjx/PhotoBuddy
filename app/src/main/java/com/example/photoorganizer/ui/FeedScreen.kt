package com.example.photoorganizer.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.example.photoorganizer.domain.MediaQueue
import com.example.photoorganizer.domain.QueueEngine
import com.example.photoorganizer.domain.QueueType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val SWIPE_ACTION_THRESHOLD = 120f
private const val SWIPE_HINT_THRESHOLD = 36f

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
    var showExitReview by remember { mutableStateOf(false) }
    var albumActionTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    val requestExit = {
        if (state.trashCount > 0) {
            showExitReview = true
        } else {
            onExit()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val asset = state.current
        if (asset == null) {
            val nextQueue = remember(state.queues, state.queueType, state.queueTitle) { suggestedNextQueue(state) }
            EmptyQueue(
                title = if (state.undo != null) "这个队列刷完啦" else "这个队列没有内容",
                nextQueue = nextQueue,
                onNextQueue = {
                    nextQueue?.let { vm.selectQueue(it) }
                },
                onExit = requestExit,
                onQueue = { showQueue = true },
            )
            UndoBanner(
                visible = state.undo != null,
                message = state.undo?.message.orEmpty(),
                onUndo = { vm.undoLast() },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
            )
        } else {
            FeedPage(
                asset = asset,
                dragX = dragX,
                dragY = dragY,
                onKeep = { vm.act(MediaStatus.KEEP) },
                onLater = { vm.act(MediaStatus.LATER) },
                onTrash = { vm.act(MediaStatus.TRASH) },
                onFavorite = { vm.act(MediaStatus.FAVORITE) },
                onDragFeedback = { x, y ->
                    dragX = x
                    dragY = y
                },
            )
            EdgeTapNavigation(
                currentIndex = state.currentIndex,
                total = state.queueItems.size,
                onPrevious = { vm.setIndex(state.currentIndex - 1) },
                onNext = { vm.setIndex(state.currentIndex + 1) },
            )

            TopBar(
                title = state.queueType.label + if (state.queueTitle.isNotEmpty()) " · ${state.queueTitle}" else "",
                currentPosition = state.currentIndex + 1,
                total = state.queueItems.size,
                remaining = state.remaining,
                trashCount = state.trashCount,
                undoAvailable = state.undo != null,
                onExit = requestExit,
                onUndo = { vm.undoLast() },
                onOpenTrash = onOpenTrash,
                onQueue = { showQueue = true },
            )

            GestureHints(
                visible = abs(dragX) <= SWIPE_HINT_THRESHOLD && abs(dragY) <= SWIPE_HINT_THRESHOLD,
                modifier = Modifier.align(Alignment.Center),
            )
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
                if (state.queueType == QueueType.SIMILAR && state.queueItems.size > 1) {
                    val similarCandidates =
                        remember(state.queueItems, state.current?.id) {
                            similarCandidatesForCurrent(state.queueItems, state.current)
                        }
                    if (similarCandidates.size > 1) {
                        SimilarComparisonStrip(
                            candidates = similarCandidates,
                            currentIndex = state.currentIndex,
                            onPick = { vm.setIndex(it) },
                            onLaterAll = { vm.deferCurrentSimilarGroup() },
                        )
                    }
                }
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
                    onManage = { albumActionTarget = it },
                    onMore = { showAddAlbum = true },
                )
            }

            UndoBanner(
                visible = state.undo != null,
                message = state.undo?.message.orEmpty(),
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
            lastAddedAt = state.albumLastAddedAt,
            pinnedAlbumIds = state.pinnedAlbumIds,
            hiddenAlbumIds = state.hiddenAlbumIds,
            onCreateAndPick = {
                vm.createAlbumAndAddCurrent(it)
                showAddAlbum = false
            },
            onPick = { albumId ->
                vm.addCurrentToAlbum(albumId)
                showAddAlbum = false
            },
            onManage = { albumActionTarget = it },
            onDismiss = { showAddAlbum = false },
        )
    }

    albumActionTarget?.let { album ->
        AlbumActionSheet(
            album = album,
            count = state.albumCounts[album.id] ?: 0,
            pinned = album.id in state.pinnedAlbumIds,
            hidden = album.id in state.hiddenAlbumIds,
            onTogglePin = { vm.setAlbumPinned(album.id, album.id !in state.pinnedAlbumIds) },
            onToggleHidden = { vm.setAlbumHidden(album.id, album.id !in state.hiddenAlbumIds) },
            onDismiss = { albumActionTarget = null },
        )
    }

    if (showExitReview) {
        ExitReviewDialog(
            trashCount = state.trashCount,
            trashBytes = state.trashBytes,
            onReview = {
                showExitReview = false
                onOpenTrash()
            },
            onKeepOrganizing = { showExitReview = false },
            onExitAnyway = {
                showExitReview = false
                onExit()
            },
        )
    }

    if (showQueue) {
        QueueFilterSheet(
            state = state,
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
private fun ExitReviewDialog(
    trashCount: Int,
    trashBytes: Long,
    onReview: () -> Unit,
    onKeepOrganizing: () -> Unit,
    onExitAnyway: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepOrganizing,
        title = { Text("还有待删除未复核") },
        text = {
            Text(
                "$trashCount 张照片正在待删除里，预计可释放 ${formatBytes(trashBytes)}。确认前不会删除，" +
                    "你可以现在去复核，也可以稍后再处理。",
            )
        },
        confirmButton = {
            Button(onClick = onReview) {
                Text("去复核")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onKeepOrganizing) {
                    Text("继续整理")
                }
                TextButton(onClick = onExitAnyway) {
                    Text("仍然关闭")
                }
            }
        },
    )
}

@Composable
private fun EdgeTapNavigation(
    currentIndex: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 120.dp, bottom = 268.dp),
    ) {
        EdgeTapZone(
            enabled = currentIndex > 0,
            contentDescription = "上一张",
            modifier = Modifier.align(Alignment.CenterStart),
            onClick = onPrevious,
        )
        EdgeTapZone(
            enabled = currentIndex + 1 < total,
            contentDescription = "下一张",
            modifier = Modifier.align(Alignment.CenterEnd),
            onClick = onNext,
        )
    }
}

@Composable
private fun EdgeTapZone(
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxHeight()
            .width(86.dp)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
private fun FeedPage(
    asset: MediaAsset,
    dragX: Float,
    dragY: Float,
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
                            horizontal && totalX > SWIPE_ACTION_THRESHOLD -> onKeep()
                            horizontal && totalX < -SWIPE_ACTION_THRESHOLD -> onLater()
                            !horizontal && totalY < -SWIPE_ACTION_THRESHOLD -> onTrash()
                            !horizontal && totalY > SWIPE_ACTION_THRESHOLD -> onFavorite()
                        }
                        onDragFeedback(0f, 0f)
                    },
                    onDragCancel = {
                        onDragFeedback(0f, 0f)
                    },
                )
            },
    ) {
        val photoOffset =
            IntOffset(
                x = (dragX * 0.12f).roundToInt(),
                y = (dragY * 0.12f).roundToInt(),
            )
        val mediaModifier =
            Modifier
                .fillMaxSize()
                .offset { photoOffset }
        if (asset.mediaType == MediaType.VIDEO) {
            VideoPage(uri = asset.uri, active = true, modifier = mediaModifier)
        } else {
            AsyncImage(
                model = asset.uri,
                contentDescription = asset.displayName,
                modifier = mediaModifier,
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private data class SimilarCandidate(
    val index: Int,
    val asset: MediaAsset,
)

private data class DragDecision(
    val label: String,
    val helper: String,
    val color: Color,
)

@Composable
private fun SimilarComparisonStrip(
    candidates: List<SimilarCandidate>,
    currentIndex: Int,
    onPick: (Int) -> Unit,
    onLaterAll: () -> Unit,
) {
    val selectedPosition = candidates.indexOfFirst { it.index == currentIndex }.takeIf { it >= 0 } ?: 0
    val current = candidates.getOrNull(selectedPosition)?.asset
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.54f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "相似组 ${selectedPosition + 1}/${candidates.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "点缩略图切换候选，选出要保留或待删除的照片。",
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                current?.let { asset ->
                    Text(
                        similarMeta(asset),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    similarGroupMeta(candidates.map { it.asset }),
                    color = Color.White.copy(alpha = 0.66f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onLaterAll) {
                Text("全部稍后", color = Color.White)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(candidates, key = { it.asset.id }) { candidate ->
                val item = candidate.asset
                val selected = candidate.index == currentIndex
                Box(
                    modifier =
                        Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = if (selected) 0.24f else 0.12f))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.28f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { onPick(candidate.index) }
                            .padding(2.dp),
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = item.displayName,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

private fun similarCandidatesForCurrent(
    items: List<MediaAsset>,
    current: MediaAsset?,
): List<SimilarCandidate> {
    if (current == null) return emptyList()
    return items
        .mapIndexed { index, asset -> SimilarCandidate(index, asset) }
        .filter { QueueEngine.isSimilarGroupPeer(current, it.asset) }
}

@Composable
private fun SwipeFeedback(
    dragX: Float,
    dragY: Float,
    modifier: Modifier = Modifier,
) {
    val horizontal = abs(dragX) > abs(dragY)
    val distance = maxOf(abs(dragX), abs(dragY))
    val active = distance > SWIPE_HINT_THRESHOLD
    if (!active) return
    val armed = distance >= SWIPE_ACTION_THRESHOLD
    val label =
        when {
            horizontal && dragX > 0 -> "保留"
            horizontal -> "稍后"
            dragY < 0 -> "加入待删除"
            else -> "收藏"
        }
    val helper = if (armed) "松手执行" else "继续拖动"
    val color =
        when (label) {
            "加入待删除" -> Color(0xFFE53935)
            "收藏" -> Color(0xFFFFC107)
            "保留" -> Color(0xFF43A047)
            else -> Color(0xFF42A5F5)
        }
    val decision = DragDecision(label = label, helper = helper, color = color)
    val alpha = min(0.9f, (distance / 180f).coerceAtLeast(0.28f))
    Box(
        modifier =
            modifier
                .background(decision.color.copy(alpha = alpha), RoundedCornerShape(8.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                decision.label,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                decision.helper,
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    currentPosition: Int,
    total: Int,
    remaining: Int,
    trashCount: Int,
    undoAvailable: Boolean,
    onExit: () -> Unit,
    onUndo: () -> Unit,
    onOpenTrash: () -> Unit,
    onQueue: () -> Unit,
) {
    val progress =
        if (total <= 0) {
            0f
        } else {
            currentPosition.coerceIn(1, total).toFloat() / total.toFloat()
        }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedTopPill(text = "关闭", onClick = onExit)
        Column(
            Modifier
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
            Text(
                "${currentPosition.coerceAtMost(total)} / $total · 剩余 $remaining",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        FeedTopPill(
            text = if (trashCount > 0) "待删 $trashCount" else "待删",
            onClick = onOpenTrash,
            highlighted = trashCount > 0,
        )
        if (undoAvailable) {
            FeedTopPill(text = "撤销", onClick = onUndo)
        }
        IconButton(
            onClick = onQueue,
            modifier = Modifier.background(Color.Black.copy(alpha = 0.42f), CircleShape),
        ) {
            Icon(Icons.Default.MoreHoriz, contentDescription = "队列", tint = Color.White)
        }
    }
}

@Composable
private fun FeedTopPill(
    text: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    val background =
        if (highlighted) {
            Color(0xFFFF453A).copy(alpha = 0.28f)
        } else {
            Color.Black.copy(alpha = 0.42f)
        }
    TextButton(
        onClick = onClick,
        modifier = Modifier.background(background, RoundedCornerShape(8.dp)),
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UndoBanner(
    visible: Boolean,
    message: String,
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
            message.ifBlank { "已完成" },
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
private fun GestureHints(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(120.dp),
    ) {
        Text("下滑收藏", color = Color.White.copy(alpha = 0.45f), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(96.dp)) {
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
        FeedAction(
            icon = Icons.Default.DeleteOutline,
            label = "待删除",
            helper = "上滑",
            accent = Color(0xFFFF453A),
            modifier = Modifier.weight(1f),
            onClick = onTrash,
        )
        FeedAction(
            icon = Icons.Default.Schedule,
            label = "稍后",
            helper = "左滑",
            accent = Color(0xFF8E8E93),
            modifier = Modifier.weight(1f),
            onClick = onLater,
        )
        FeedAction(
            icon = Icons.Default.Check,
            label = "保留",
            helper = "右滑",
            accent = Color(0xFF34C759),
            modifier = Modifier.weight(1f),
            onClick = onKeep,
        )
        FeedAction(
            icon = Icons.Default.Star,
            label = "收藏",
            helper = "下滑",
            accent = Color(0xFFFFCC00),
            modifier = Modifier.weight(1f),
            onClick = onFavorite,
        )
    }
}

@Composable
private fun AlbumQuickBar(
    state: HomeUiState,
    onPick: (Long) -> Unit,
    onManage: (AlbumEntity) -> Unit,
    onMore: () -> Unit,
) {
    val quickAlbums =
        remember(state.albums, state.albumCounts, state.albumLastAddedAt, state.pinnedAlbumIds, state.hiddenAlbumIds) {
            sortedAlbumsForOrganize(
                albums = state.albums.filter { it.id !in state.hiddenAlbumIds },
                counts = state.albumCounts,
                lastAddedAt = state.albumLastAddedAt,
                pinnedAlbumIds = state.pinnedAlbumIds,
                hiddenAlbumIds = state.hiddenAlbumIds,
            ).take(6)
        }
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
            items(quickAlbums) { album ->
                AlbumChip(
                    text = album.name,
                    onClick = { onPick(album.id) },
                    onLongClick = { onManage(album) },
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
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumChip(
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.16f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.34f)), RoundedCornerShape(100.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumSheetChip(
    text: String,
    count: Int,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$text · $count",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AlbumActionSheet(
    album: AlbumEntity,
    count: Int,
    pinned: Boolean,
    hidden: Boolean,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(album.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "$count 项 · App 内标签",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    onTogglePin()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (pinned) "取消置顶" else "置顶到快捷区")
            }
            TextButton(
                onClick = {
                    onToggleHidden()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (hidden) "显示在整理页" else "从整理页隐藏")
            }
            Text(
                "短按相册会把当前照片加入这里；长按可调整快捷区显示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
private fun AlbumPickerSheet(
    albums: List<AlbumEntity>,
    counts: Map<Long, Int>,
    lastAddedAt: Map<Long, Long>,
    pinnedAlbumIds: Set<Long>,
    hiddenAlbumIds: Set<Long>,
    onCreateAndPick: (String) -> Unit,
    onPick: (Long) -> Unit,
    onManage: (AlbumEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val trimmedQuery = query.trim()
    val sortedAlbums =
        remember(albums, counts, lastAddedAt, pinnedAlbumIds, hiddenAlbumIds) {
            sortedAlbumsForOrganize(
                albums = albums,
                counts = counts,
                lastAddedAt = lastAddedAt,
                pinnedAlbumIds = pinnedAlbumIds,
                hiddenAlbumIds = hiddenAlbumIds,
            )
        }
    val pinnedAlbums = sortedAlbums.filter { it.id in pinnedAlbumIds && it.id !in hiddenAlbumIds }.take(6)
    val recentAlbums =
        sortedAlbums
            .filter { it.id !in pinnedAlbumIds && it.id !in hiddenAlbumIds && (lastAddedAt[it.id] ?: 0L) > 0L }
            .take(5)
    val filtered =
        sortedAlbums.filter {
            query.isBlank() || it.name.contains(trimmedQuery, ignoreCase = true)
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("加入相册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "App 内标签，不移动系统文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索或新建相册") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (trimmedQuery.isNotEmpty()) {
                Button(
                    onClick = { onCreateAndPick(trimmedQuery) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("新建并加入「$trimmedQuery」")
                }
            } else {
                Text(
                    "输入名称可直接创建，点相册会立即归类当前照片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trimmedQuery.isBlank() && pinnedAlbums.isNotEmpty()) {
                AlbumSheetSectionTitle("置顶相册", pinnedAlbums.size)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(pinnedAlbums, key = { it.id }) { album ->
                        AlbumSheetChip(
                            text = album.name,
                            count = counts[album.id] ?: 0,
                            onClick = { onPick(album.id) },
                            onLongClick = { onManage(album) },
                        )
                    }
                }
            }
            if (trimmedQuery.isBlank() && recentAlbums.isNotEmpty()) {
                AlbumSheetSectionTitle("最近使用", recentAlbums.size)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentAlbums, key = { it.id }) { album ->
                        AlbumSheetChip(
                            text = album.name,
                            count = counts[album.id] ?: 0,
                            onClick = { onPick(album.id) },
                            onLongClick = { onManage(album) },
                        )
                    }
                }
            }
            AlbumSheetSectionTitle(
                title = if (trimmedQuery.isBlank()) "全部相册" else "搜索结果",
                count = filtered.size,
            )
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
                        val count = counts[album.id] ?: 0
                        val recent = lastAddedAt[album.id] ?: 0L
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = { onPick(album.id) },
                                        onLongClick = { onManage(album) },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(album.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    albumSheetMeta(
                                        count = count,
                                        recent = recent,
                                        pinned = album.id in pinnedAlbumIds,
                                        hidden = album.id in hiddenAlbumIds,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
private fun AlbumSheetSectionTitle(
    title: String,
    count: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun sortedAlbumsForOrganize(
    albums: List<AlbumEntity>,
    counts: Map<Long, Int>,
    lastAddedAt: Map<Long, Long>,
    pinnedAlbumIds: Set<Long>,
    hiddenAlbumIds: Set<Long> = emptySet(),
): List<AlbumEntity> =
    albums.sortedWith(
        compareByDescending<AlbumEntity> { if (it.id in pinnedAlbumIds) 1 else 0 }
            .thenBy { if (it.id in hiddenAlbumIds) 1 else 0 }
            .thenByDescending { lastAddedAt[it.id] ?: 0L }
            .thenByDescending { counts[it.id] ?: 0 }
            .thenByDescending { it.createdAt }
            .thenBy { it.name },
    )

private fun albumSheetMeta(
    count: Int,
    recent: Long,
    pinned: Boolean,
    hidden: Boolean,
): String =
    buildString {
        append("$count 项")
        if (pinned) append(" · 置顶")
        if (hidden) append(" · 已隐藏")
        if (recent > 0L) append(" · 最近 ${formatDate(recent)}")
        append(" · App 内标签")
    }

private fun similarMeta(asset: MediaAsset): String =
    buildString {
        append("当前：${formatBytes(asset.size)}")
        if (asset.capturedAt > 0) append(" · ${formatDate(asset.capturedAt)}")
        if (asset.bucketName.isNotEmpty()) append(" · ${asset.bucketName}")
    }

private fun similarGroupMeta(items: List<MediaAsset>): String {
    val capturedTimes =
        items
            .mapNotNull { captureOrAddedMs(it).takeIf { ms -> ms > 0L } }
            .sorted()
    val timeText =
        if (capturedTimes.size >= 2) {
            "时间差 ${formatTimeSpan(capturedTimes.last() - capturedTimes.first())}"
        } else {
            "时间差未知"
        }
    val sizes = items.map { it.size }.filter { it > 0L }.sorted()
    val sizeText =
        when {
            sizes.isEmpty() -> "大小未知"
            sizes.first() == sizes.last() -> "大小 ${formatBytes(sizes.first())}"
            else -> "大小 ${formatBytes(sizes.first())} - ${formatBytes(sizes.last())}"
        }
    return "$timeText · $sizeText"
}

private fun captureOrAddedMs(asset: MediaAsset): Long =
    when {
        asset.capturedAt > 0 -> asset.capturedAt
        asset.dateAdded > 0 -> asset.dateAdded * 1000L
        else -> 0L
    }

private fun formatTimeSpan(ms: Long): String {
    val totalMinutes = (ms / 60_000L).coerceAtLeast(0L)
    return when {
        totalMinutes < 1L -> "1 分钟内"
        totalMinutes < 60L -> "$totalMinutes 分钟"
        totalMinutes < 24L * 60L -> "${totalMinutes / 60L} 小时 ${totalMinutes % 60L} 分钟"
        else -> "${totalMinutes / (24L * 60L)} 天"
    }
}

@Composable
private fun EmptyQueue(
    title: String,
    nextQueue: MediaQueue?,
    onNextQueue: () -> Unit,
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
            if (nextQueue == null) {
                "所有可见队列都整理完了，可以回首页看看待删除。"
            } else {
                "可以继续处理「${nextQueue.displayName}」，让整理节奏不断掉。"
            },
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (nextQueue != null) {
            Button(onClick = onNextQueue) {
                Text("继续 ${nextQueue.displayName} · ${nextQueue.items.size} 项")
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onQueue) { Text("选择队列") }
        TextButton(onClick = onExit) { Text("回首页", color = Color.White) }
    }
}

private fun suggestedNextQueue(state: HomeUiState): MediaQueue? {
    val priority =
        listOf(
            QueueType.RANDOM,
            QueueType.UNPROCESSED,
            QueueType.SIMILAR,
            QueueType.SCREENSHOT,
            QueueType.LARGE_VIDEO,
            QueueType.RECENT_30,
            QueueType.FAVORITE,
            QueueType.MONTH,
        )
    return state.queues
        .asSequence()
        .filter { it.items.isNotEmpty() }
        .filterNot { it.type == state.queueType && it.title == state.queueTitle }
        .sortedWith(
            compareBy<MediaQueue> { queue ->
                priority.indexOf(queue.type).let { if (it < 0) priority.size else it }
            }.thenByDescending { it.items.size },
        )
        .firstOrNull()
}

@Composable
private fun FeedAction(
    icon: ImageVector,
    label: String,
    helper: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(accent.copy(alpha = 0.22f), CircleShape)
                .border(BorderStroke(1.dp, accent.copy(alpha = 0.48f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = accent)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            helper,
            color = Color.White.copy(alpha = 0.52f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
