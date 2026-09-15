package com.example.photoorganizer.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaType
import com.example.photoorganizer.data.local.AlbumEntity
import com.example.photoorganizer.data.local.MediaStatus
import com.example.photoorganizer.domain.MediaQueue
import com.example.photoorganizer.domain.QueueEngine
import com.example.photoorganizer.domain.QueueType
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SWIPE_ACTION_THRESHOLD = 120f

/** Slidebox 式单卡整理流：当前照片做完一个决策后自动推进到下一张。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    initialQueueTypeName: String? = null,
    onInitialQueueConsumed: () -> Unit = {},
    onExit: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAlbums: () -> Unit,
) {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    var showAddAlbum by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showExitReview by remember { mutableStateOf(false) }
    var albumActionTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var showGestureGuide by remember { mutableStateOf(false) }
    var appliedInitialQueueTypeName by remember { mutableStateOf<String?>(null) }
    val undoLastAction = {
        vm.clearFeedback()
        vm.undoLast()
    }
    val requestExit = {
        if (state.trashCount > 0) {
            showExitReview = true
        } else {
            onExit()
        }
    }
    LaunchedEffect(initialQueueTypeName, state.queues) {
        val target = initialQueueTypeName?.takeIf { it.isNotBlank() }
        if (target != null && appliedInitialQueueTypeName != target && state.queues.isNotEmpty()) {
            vm.selectQueueType(target)
            appliedInitialQueueTypeName = target
            onInitialQueueConsumed()
        }
    }
    LaunchedEffect(state.current != null, state.feedGestureGuideSeen) {
        if (state.current != null && !state.feedGestureGuideSeen) {
            showGestureGuide = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val asset = state.current
        if (asset == null) {
            val nextQueue = remember(state.queues, state.queueType, state.queueTitle) { suggestedNextQueue(state) }
            EmptyQueue(
                title = emptyQueueTitle(state),
                detail = emptyQueueDetail(state),
                nextQueue = nextQueue,
                onNextQueue = {
                    nextQueue?.let { vm.selectQueue(it) }
                },
                onExit = requestExit,
                onQueue = { showQueue = true },
            )
            UndoBanner(
                visible = state.feedbackMessage != null,
                message = state.feedbackMessage.orEmpty(),
                canUndo = state.undo != null,
                onUndo = undoLastAction,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(8f)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 24.dp),
            )
        } else {
            val similarCandidates =
                remember(state.queueType, state.queueItems, state.current?.id) {
                    if (state.queueType == QueueType.SIMILAR && state.queueItems.size > 1) {
                        similarCandidatesForCurrent(state.queueItems, state.current)
                    } else {
                        emptyList()
                    }
                }
            val showSimilarComparison = similarCandidates.size > 1
            val selectedSimilarPosition = similarCandidates.indexOfFirst { it.index == state.currentIndex }
            val previousSimilarIndex = similarCandidates.getOrNull(selectedSimilarPosition - 1)?.index
            val nextSimilarIndex = similarCandidates.getOrNull(selectedSimilarPosition + 1)?.index
            val scopeLabel =
                remember(state.filterType, state.filterBucket, state.allAssets) {
                    feedScopeLabel(
                        filterType = state.filterType,
                        filterBucket = state.filterBucket,
                        assets = state.allAssets,
                    )
                }
            val isAppFavorite = state.statusById[asset.id] == MediaStatus.FAVORITE.value
            FeedPage(
                asset = asset,
                dragX = dragX,
                dragY = dragY,
                similarMode = showSimilarComparison,
                onKeep = { vm.act(MediaStatus.KEEP) },
                onLater = { vm.act(MediaStatus.LATER) },
                onTrash = { vm.act(MediaStatus.TRASH) },
                onFavorite = { vm.toggleFavorite() },
                onPreviousSimilar = { previousSimilarIndex?.let { vm.setIndex(it) } },
                onNextSimilar = { nextSimilarIndex?.let { vm.setIndex(it) } },
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
                scopeLabel = scopeLabel,
                canUndo = state.undo != null,
                onExit = requestExit,
                onOpenTrash = onOpenTrash,
                onUndo = undoLastAction,
                onQueue = { showQueue = true },
            )

            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (showSimilarComparison) {
                    SimilarComparisonStrip(
                        candidates = similarCandidates,
                        currentIndex = state.currentIndex,
                        onPick = { vm.setIndex(it) },
                        onLaterAll = { vm.deferCurrentSimilarGroup() },
                        onKeepCurrent = { vm.keepCurrentSimilarAndTrashPeers() },
                    )
                }
                UndoBanner(
                    visible = state.feedbackMessage != null,
                    message = state.feedbackMessage.orEmpty(),
                    canUndo = state.undo != null,
                    onUndo = undoLastAction,
                )
                AssetCaption(asset = asset)
                if (state.feedActionBarExpanded) {
                    ActionBar(
                        similarMode = showSimilarComparison,
                        isAppFavorite = isAppFavorite,
                        onTrash = { vm.act(MediaStatus.TRASH) },
                        onKeep = { vm.act(MediaStatus.KEEP) },
                        onLater = { vm.act(MediaStatus.LATER) },
                        onFavorite = { vm.toggleFavorite() },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AlbumQuickBar(
                        state = state,
                        onPick = { vm.addCurrentToAlbum(it) },
                        onMove = { albumId, direction -> vm.moveAlbumOrder(albumId, direction) },
                        onMore = { showAddAlbum = true },
                        modifier = Modifier.weight(1f),
                    )
                    Column(
                        modifier = Modifier.height(92.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        GestureGuideButton(
                            onClick = {
                                showGestureGuide = true
                                vm.setFeedGestureGuideSeen(true)
                            },
                        )
                        ActionPanelToggle(
                            expanded = state.feedActionBarExpanded,
                            onClick = { vm.setFeedActionBarExpanded(!state.feedActionBarExpanded) },
                        )
                    }
                }
            }

            DragActionHint(
                dragX = dragX,
                dragY = dragY,
                similarMode = showSimilarComparison,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .zIndex(6f),
            )
            GestureGuideOverlay(
                visible = showGestureGuide,
                similarMode = showSimilarComparison,
                onDismiss = {
                    showGestureGuide = false
                    vm.setFeedGestureGuideSeen(true)
                },
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .zIndex(7f),
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
            onOpenAlbumManager = {
                showAddAlbum = false
                onOpenAlbums()
            },
            onDismiss = { showAddAlbum = false },
        )
    }

    albumActionTarget?.let { album ->
        AlbumActionSheet(
            album = album,
            albums = state.albums,
            count = state.albumCounts[album.id] ?: 0,
            pinned = album.id in state.pinnedAlbumIds,
            hidden = album.id in state.hiddenAlbumIds,
            onRename = { name ->
                vm.renameAlbum(album.id, name)
                albumActionTarget = null
            },
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
            onClearFilters = { vm.setFilter(null, null) },
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
    similarMode: Boolean,
    onKeep: () -> Unit,
    onLater: () -> Unit,
    onTrash: () -> Unit,
    onFavorite: () -> Unit,
    onPreviousSimilar: () -> Unit,
    onNextSimilar: () -> Unit,
    onDragFeedback: (Float, Float) -> Unit,
) {
    var zoomed by remember(asset.id) { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(asset.id, similarMode) {
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
                            horizontal && similarMode && totalX > SWIPE_ACTION_THRESHOLD -> onPreviousSimilar()
                            horizontal && similarMode && totalX < -SWIPE_ACTION_THRESHOLD -> onNextSimilar()
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
                .graphicsLayer {
                    val imageScale =
                        if (asset.mediaType == MediaType.IMAGE && zoomed) {
                            1.85f
                        } else {
                            1f
                        }
                    scaleX = imageScale
                    scaleY = imageScale
                }
                .pointerInput(asset.id, asset.mediaType) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (asset.mediaType == MediaType.IMAGE) {
                                zoomed = !zoomed
                            }
                        },
                    )
                }
        if (asset.mediaType == MediaType.VIDEO) {
            VideoPage(uri = asset.uri, active = true, modifier = mediaModifier)
        } else {
            SubcomposeAsyncImage(
                model = asset.uri,
                contentDescription = asset.displayName,
                modifier = mediaModifier,
                contentScale = ContentScale.Fit,
                loading = {
                    MediaLoadingState()
                },
                error = {
                    MediaLoadError(asset)
                },
                success = {
                    SubcomposeAsyncImageContent()
                },
            )
        }
    }
}

@Composable
private fun MediaLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.82f))
            Text(
                "正在加载照片",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MediaLoadError(asset: MediaAsset) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 28.dp)
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "照片加载失败",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                asset.displayName,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "可以先标记稍后，或继续处理下一张。",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class SimilarCandidate(
    val index: Int,
    val asset: MediaAsset,
)

private data class UndoVisual(
    val color: Color,
    val detail: String,
)

private data class SwipeActionVisual(
    val label: String,
    val detail: String,
    val color: Color,
)

@Composable
private fun DragActionHint(
    dragX: Float,
    dragY: Float,
    similarMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val visual = swipeActionVisual(dragX, dragY, similarMode) ?: return
    Row(
        modifier =
            modifier
                .offset {
                    IntOffset(
                        x = (dragX * 0.16f).roundToInt(),
                        y = (dragY * 0.16f).roundToInt(),
                    )
                }
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(100.dp))
                .border(BorderStroke(1.dp, visual.color.copy(alpha = 0.7f)), RoundedCornerShape(100.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(visual.color, CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                visual.label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                visual.detail,
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun GestureGuideOverlay(
    visible: Boolean,
    similarMode: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Column(
        modifier =
            modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(12.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "滑动整理",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            GestureGuideLine("上滑", "加入待删除", Color(0xFFFF453A))
            GestureGuideLine("下滑", "收藏", Color(0xFFFFCC00))
            GestureGuideLine("左滑", if (similarMode) "下一张相似" else "稍后", Color(0xFF8E8E93))
            GestureGuideLine("右滑", if (similarMode) "上一张相似" else "保留", Color(0xFF34C759))
        }
        Text(
            "轻点收起",
            color = Color.White.copy(alpha = 0.56f),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun GestureGuideLine(
    direction: String,
    action: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            direction,
            modifier =
                Modifier
                    .width(48.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(100.dp))
                    .padding(vertical = 5.dp),
            color = color,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            action,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GestureGuideButton(
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), CircleShape)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "?",
            color = Color.White.copy(alpha = 0.92f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SimilarComparisonStrip(
    candidates: List<SimilarCandidate>,
    currentIndex: Int,
    onPick: (Int) -> Unit,
    onLaterAll: () -> Unit,
    onKeepCurrent: () -> Unit,
) {
    val selectedPosition = candidates.indexOfFirst { it.index == currentIndex }.takeIf { it >= 0 } ?: 0
    val current = candidates.getOrNull(selectedPosition)?.asset
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.52f), RoundedCornerShape(8.dp))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "对比相似照片",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    similarGroupMeta(candidates.map { it.asset }),
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "${selectedPosition + 1}/${candidates.size}",
                modifier =
                    Modifier
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(100.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(candidates, key = { _, candidate -> candidate.asset.id }) { position, candidate ->
                SimilarCandidateTile(
                    candidate = candidate,
                    position = position,
                    selected = candidate.index == currentIndex,
                    onPick = { onPick(candidate.index) },
                )
            }
        }
        current?.let { asset ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SimilarInfoPill(
                    label = "尺寸",
                    value = similarResolutionText(asset),
                    modifier = Modifier.weight(1f),
                )
                SimilarInfoPill(
                    label = "大小",
                    value = formatBytes(asset.size),
                    modifier = Modifier.weight(1f),
                )
                SimilarInfoPill(
                    label = "时间",
                    value = similarDateText(asset),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                asset.displayName + if (asset.bucketName.isNotEmpty()) " · ${asset.bucketName}" else "",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SimilarGroupAction(
                label = "整组稍后",
                helper = "不处理，稍后再看",
                modifier = Modifier.weight(1f),
                onClick = onLaterAll,
            )
            SimilarGroupAction(
                label = "保留此张",
                helper = "其余进待删复核",
                modifier = Modifier.weight(1f),
                onClick = onKeepCurrent,
            )
        }
    }
}

@Composable
private fun SimilarCandidateTile(
    candidate: SimilarCandidate,
    position: Int,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val item = candidate.asset
    Column(
        modifier =
            Modifier
                .width(82.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = if (selected) 0.22f else 0.11f))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(onClick = onPick)
                .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            Text(
                if (selected) "当前" else "${position + 1}",
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .background(
                            Color.Black.copy(alpha = if (selected) 0.74f else 0.56f),
                            RoundedCornerShape(topEnd = 6.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            formatBytes(item.size),
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SimilarInfoPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.56f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SimilarGroupAction(
    label: String,
    helper: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.13f))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            helper,
            color = Color.White.copy(alpha = 0.64f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun TopBar(
    title: String,
    currentPosition: Int,
    total: Int,
    remaining: Int,
    trashCount: Int,
    scopeLabel: String,
    canUndo: Boolean,
    onExit: () -> Unit,
    onOpenTrash: () -> Unit,
    onUndo: () -> Unit,
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
            .padding(horizontal = 10.dp)
            .padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeedTopIconButton(
            icon = Icons.Default.Close,
            contentDescription = "关闭整理",
            onClick = onExit,
        )
        Column(
            Modifier
                .weight(1f)
                .background(Color.Black.copy(alpha = 0.34f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
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
                buildString {
                    append("${currentPosition.coerceAtMost(total)} / $total · $remaining 待整理")
                    if (scopeLabel.isNotEmpty()) {
                        append(" · $scopeLabel")
                    }
                },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canUndo) {
            FeedTopIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = "撤销上一动作",
                onClick = onUndo,
            )
        }
        TrashTopButton(
            count = trashCount,
            onClick = onOpenTrash,
        )
        FeedTopIconButton(
            icon = Icons.Default.MoreHoriz,
            contentDescription = "切换队列",
            onClick = onQueue,
        )
    }
}

@Composable
private fun FeedTopIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            Modifier
                .size(40.dp)
                .background(Color.Black.copy(alpha = 0.34f), CircleShape),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White)
    }
}

@Composable
private fun TrashTopButton(
    count: Int,
    onClick: () -> Unit,
) {
    val highlighted = count > 0
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(
                    if (highlighted) {
                        Color(0xFFFF453A).copy(alpha = 0.32f)
                    } else {
                        Color.Black.copy(alpha = 0.34f)
                    },
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            Icons.Default.DeleteOutline,
            contentDescription =
                if (highlighted) {
                    "待删除 $count 项"
                } else {
                    "待删除"
                },
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Text(
            count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun UndoBanner(
    visible: Boolean,
    message: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val visual = undoVisual(message)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.74f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(28.dp)
                .background(visual.color, RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                message.ifBlank { "已完成" },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                visual.detail,
                color = Color.White.copy(alpha = 0.58f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canUndo) {
            TextButton(onClick = onUndo) {
                Text("撤销", color = Color.White)
            }
        }
    }
}

private fun undoVisual(message: String): UndoVisual =
    when {
        message.contains("待删除") ->
            UndoVisual(Color(0xFFFF453A), "进入复核页前不会删除")
        message.contains("稍后") ->
            UndoVisual(Color(0xFF8E8E93), "已移出当前短队列")
        message.contains("取消收藏") ->
            UndoVisual(Color(0xFF8E8E93), "已保留但不再收藏")
        message.contains("收藏") ->
            UndoVisual(Color(0xFFFFCC00), "已从未整理中移出")
        message.contains("已撤销") ->
            UndoVisual(Color(0xFF34C759), "已回到上一步")
        message.contains("相册") || message.contains("已加入") || message.contains("已在") ->
            UndoVisual(Color(0xFF0A84FF), "已记录本地映射")
        else ->
            UndoVisual(Color(0xFF34C759), "继续下一张")
    }

private fun swipeActionVisual(
    dragX: Float,
    dragY: Float,
    similarMode: Boolean,
): SwipeActionVisual? {
    val absX = abs(dragX)
    val absY = abs(dragY)
    if (absX < 28f && absY < 28f) return null
    val horizontal = absX > absY
    return when {
        horizontal && similarMode && dragX > 0f ->
            SwipeActionVisual("松手切到上一张", "右滑对比相似照片", Color(0xFF64D2FF))
        horizontal && similarMode && dragX < 0f ->
            SwipeActionVisual("松手切到下一张", "左滑对比相似照片", Color(0xFF64D2FF))
        horizontal && dragX > 0f ->
            SwipeActionVisual("松手保留", "右滑确认这张照片", Color(0xFF34C759))
        horizontal && dragX < 0f ->
            SwipeActionVisual("松手稍后", "左滑移出当前队列", Color(0xFF8E8E93))
        !horizontal && dragY < 0f ->
            SwipeActionVisual("松手加入待删除", "上滑进入复核页", Color(0xFFFF453A))
        else ->
            SwipeActionVisual("松手收藏", "下滑标为收藏", Color(0xFFFFCC00))
    }
}

@Composable
private fun AssetCaption(asset: MediaAsset) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
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
    similarMode: Boolean,
    isAppFavorite: Boolean,
    onTrash: () -> Unit,
    onKeep: () -> Unit,
    onLater: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedAction(
            icon = Icons.Default.DeleteOutline,
            label = if (similarMode) "删这张" else "待删除",
            helper = if (similarMode) "删这张" else "待删除",
            accent = Color(0xFFFF453A),
            modifier = Modifier.weight(1f),
            onClick = onTrash,
        )
        FeedAction(
            icon = Icons.Default.Schedule,
            label = if (similarMode) "当前稍后" else "稍后",
            helper = if (similarMode) "当前稍后" else "稍后",
            accent = Color(0xFF8E8E93),
            modifier = Modifier.weight(1f),
            onClick = onLater,
        )
        FeedAction(
            icon = Icons.Default.Check,
            label = if (similarMode) "保留这张" else "保留",
            helper = if (similarMode) "保留这张" else "保留",
            accent = Color(0xFF34C759),
            modifier = Modifier.weight(1f),
            onClick = onKeep,
        )
        FeedAction(
            icon = Icons.Default.Star,
            label = if (isAppFavorite) "取消收藏" else "收藏",
            helper = if (isAppFavorite) "取消收藏" else "收藏",
            accent = if (isAppFavorite) Color(0xFF8E8E93) else Color(0xFFFFCC00),
            modifier = Modifier.weight(1f),
            onClick = onFavorite,
        )
    }
}

@Composable
private fun ActionPanelToggle(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = if (expanded) 0.22f else 0.14f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)), RoundedCornerShape(100.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Default.Close else Icons.Default.MoreHoriz,
            contentDescription = if (expanded) "收起操作" else "展开操作",
            tint = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(17.dp),
        )
        Text(
            if (expanded) "收起" else "操作",
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun AlbumQuickBar(
    state: HomeUiState,
    onPick: (Long) -> Unit,
    onMove: (Long, Int) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortingAlbumId by remember { mutableStateOf<Long?>(null) }
    val scrollState = rememberScrollState()
    val quickAlbums =
        remember(state.albums, state.hiddenAlbumIds, state.albumOrderIds) {
            orderedAlbumsForQuickBar(
                albums = state.albums.filter { it.id !in state.hiddenAlbumIds },
                orderIds = state.albumOrderIds,
            )
        }
    val quickItems =
        remember(quickAlbums, state.albums.isEmpty()) {
            quickAlbums.map { QuickAlbumItem.Album(it) } +
                QuickAlbumItem.More(
                    text = if (state.albums.isEmpty()) "新建相册" else "更多相册",
                    icon = if (state.albums.isEmpty()) Icons.Default.Add else Icons.Default.MoreHoriz,
                )
        }
    val firstRow = remember(quickItems) { quickItems.filterIndexed { index, _ -> index % 2 == 0 } }
    val secondRow = remember(quickItems) { quickItems.filterIndexed { index, _ -> index % 2 == 1 } }
    Box(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .clipToBounds()
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AlbumQuickRow(
                items = firstRow,
                sortingAlbumId = sortingAlbumId,
                scrollState = scrollState,
                onSortStart = { sortingAlbumId = it },
                onSortEnd = { sortingAlbumId = null },
                onMove = onMove,
                onPick = onPick,
                onMore = onMore,
            )
            AlbumQuickRow(
                items = secondRow,
                sortingAlbumId = sortingAlbumId,
                scrollState = scrollState,
                onSortStart = { sortingAlbumId = it },
                onSortEnd = { sortingAlbumId = null },
                onMove = onMove,
                onPick = onPick,
                onMore = onMore,
            )
        }
    }
}

@Composable
private fun AlbumQuickRow(
    items: List<QuickAlbumItem>,
    sortingAlbumId: Long?,
    scrollState: androidx.compose.foundation.ScrollState,
    onSortStart: (Long) -> Unit,
    onSortEnd: () -> Unit,
    onMove: (Long, Int) -> Unit,
    onPick: (Long) -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            when (item) {
                is QuickAlbumItem.Album ->
                    SortableAlbumChip(
                        album = item.album,
                        sorting = sortingAlbumId == item.album.id,
                        onSortStart = { onSortStart(item.album.id) },
                        onSortEnd = onSortEnd,
                        onMove = onMove,
                        onPick = { onPick(item.album.id) },
                    )
                is QuickAlbumItem.More ->
                    AlbumChip(
                        text = item.text,
                        icon = item.icon,
                        onClick = onMore,
                    )
            }
        }
    }
}

private sealed class QuickAlbumItem {
    abstract val key: String

    data class Album(val album: AlbumEntity) : QuickAlbumItem() {
        override val key: String = "album-${album.id}"
    }

    data class More(
        val text: String,
        val icon: ImageVector,
    ) : QuickAlbumItem() {
        override val key: String = "more"
    }
}

@Composable
private fun SortableAlbumChip(
    album: AlbumEntity,
    sorting: Boolean,
    onSortStart: () -> Unit,
    onSortEnd: () -> Unit,
    onMove: (Long, Int) -> Unit,
    onPick: () -> Unit,
) {
    var dragOffset by remember(album.id) { mutableStateOf(0f) }
    AlbumChip(
        text = album.name,
        onClick = onPick,
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = if (sorting) 1.04f else 1f
                    scaleY = if (sorting) 1.04f else 1f
                    alpha = if (sorting) 0.96f else 1f
                }
                .pointerInput(album.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            dragOffset = 0f
                            onSortStart()
                        },
                        onDragEnd = {
                            dragOffset = 0f
                            onSortEnd()
                        },
                        onDragCancel = {
                            dragOffset = 0f
                            onSortEnd()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount.x
                            if (abs(dragOffset) >= 64f) {
                                val direction = if (dragOffset > 0f) 1 else -1
                                onMove(album.id, direction)
                                dragOffset = 0f
                            }
                        },
                    )
                },
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumChip(
    text: String,
    detail: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), RoundedCornerShape(100.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(17.dp),
            )
        }
        Column(
            modifier = Modifier.widthIn(max = 140.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                Text(
                    detail,
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    albums: List<AlbumEntity>,
    count: Int,
    pinned: Boolean,
    hidden: Boolean,
    onRename: (String) -> Unit,
    onTogglePin: () -> Unit,
    onToggleHidden: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftName by remember(album.id, album.name) { mutableStateOf(album.name) }
    val trimmedName = draftName.trim()
    val hasSameNameAlbum =
        trimmedName.isNotEmpty() &&
            albums.any { it.id != album.id && it.name.equals(trimmedName, ignoreCase = true) }
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
                "$count 项",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draftName,
                onValueChange = { draftName = it },
                label = { Text("相册名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (hasSameNameAlbum) {
                Text(
                    "已有同名相册，请换一个名称。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = { onRename(trimmedName) },
                enabled = trimmedName.isNotEmpty() && trimmedName != album.name && !hasSameNameAlbum,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存名称")
            }
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
                "短按相册会把当前照片加入这里；长按可改名、置顶或调整快捷区显示。",
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
    onOpenAlbumManager: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val trimmedQuery = query.trim()
    val hasExactName = albums.any { it.name.equals(trimmedQuery, ignoreCase = true) }
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
    val starterAlbums = remember { listOf("家人", "朋友", "旅行", "资料", "美食", "风景") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("归类到相册", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "点相册即归类，可撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenAlbumManager) {
                    Icon(Icons.Default.PhotoAlbum, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("管理")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索或新建相册") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (trimmedQuery.isNotEmpty() && !hasExactName) {
                Button(
                    onClick = { onCreateAndPick(trimmedQuery) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("新建并加入「$trimmedQuery」")
                }
            } else if (trimmedQuery.isNotEmpty()) {
                Text(
                    "已有同名映射，点下方结果即可归类。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "输入名称可直接新建，长按相册可置顶或隐藏。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (trimmedQuery.isBlank() && albums.isEmpty()) {
                AlbumSheetSectionTitle("快速开始", starterAlbums.size)
                Text(
                    "点一个常用分类，会立即创建本地映射并归类当前照片。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(starterAlbums, key = { it }) { albumName ->
                        AlbumSheetChip(
                            text = albumName,
                            count = 0,
                            onClick = { onCreateAndPick(albumName) },
                        )
                    }
                }
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
                title = if (trimmedQuery.isBlank()) "全部本地映射" else "搜索结果",
                count = filtered.size,
            )
            if (filtered.isEmpty()) {
                Text(
                    if (albums.isEmpty()) {
                        "还没有本地相册映射，可以先新建一个。"
                    } else {
                        "没有匹配的相册。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(340.dp),
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

private fun orderedAlbumsForQuickBar(
    albums: List<AlbumEntity>,
    orderIds: List<Long>,
): List<AlbumEntity> {
    val albumsById = albums.associateBy { it.id }
    val ordered = orderIds.mapNotNull { albumsById[it] }
    val missing =
        albums
            .filter { album -> ordered.none { it.id == album.id } }
            .sortedWith(compareByDescending<AlbumEntity> { it.createdAt }.thenBy { it.name })
    return ordered + missing
}

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
    }

private fun similarResolutionText(asset: MediaAsset): String =
    if (asset.width > 0 && asset.height > 0) {
        "${asset.width}×${asset.height}"
    } else {
        "未知"
    }

private fun similarDateText(asset: MediaAsset): String {
    val time = captureOrAddedMs(asset)
    return if (time > 0L) formatDate(time) else "未知"
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
    return "共 ${items.size} 张 · $timeText · $sizeText"
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
    detail: String,
    nextQueue: MediaQueue?,
    onNextQueue: () -> Unit,
    onExit: () -> Unit,
    onQueue: () -> Unit,
) {
    val supportingText =
        if (nextQueue == null) {
            detail
        } else {
            detail + "\n建议继续「${nextQueue.displayName}」：${nextQueue.items.size} 项" +
                if (nextQueue.estimatedSavingBytes > 0L) {
                    " · 合计 ${formatBytes(nextQueue.estimatedSavingBytes)}"
                } else {
                    ""
                }
        }
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
            supportingText,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        if (nextQueue != null) {
            Button(onClick = onNextQueue) {
                Text("继续 ${nextQueue.displayName}")
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = onQueue) { Text("查看短队列") }
        TextButton(onClick = onExit) { Text("回首页", color = Color.White) }
    }
}

private fun emptyQueueTitle(state: HomeUiState): String =
    when {
        state.undo != null -> "这个队列刷完啦"
        state.queueType == QueueType.ON_THIS_DAY -> "今天暂无待整理回忆"
        state.queueType == QueueType.MONTH ->
            if (state.queueTitle.isNotBlank()) {
                "${state.queueTitle} 已整理完"
            } else {
                "没有待整理月份"
            }
        else -> "这个队列没有内容"
    }

private fun emptyQueueDetail(state: HomeUiState): String =
    when {
        state.undo != null -> "刚刚的操作可以撤销，也可以切换到下一个短队列。"
        state.queueType == QueueType.ON_THIS_DAY -> "往年今天没有待处理照片，可以回首页选择其他短队列。"
        state.queueType == QueueType.MONTH -> "这个月份暂时没有待处理照片，可以切换其他月份或队列。"
        else -> "没有可继续的短队列。回首页复核待删除，或重新扫描后继续整理。"
    }

private fun suggestedNextQueue(state: HomeUiState): MediaQueue? {
    val priority =
        listOf(
            QueueType.RANDOM,
            QueueType.ON_THIS_DAY,
            QueueType.ALBUM,
            QueueType.UNPROCESSED,
            QueueType.LATER,
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

private fun feedScopeLabel(
    filterType: MediaType?,
    filterBucket: String?,
    assets: List<MediaAsset>,
): String =
    buildList {
        when (filterType) {
            MediaType.IMAGE -> add("图片")
            MediaType.VIDEO -> add("视频")
            null -> Unit
        }
        if (filterBucket != null) {
            val bucketName =
                assets
                    .firstOrNull { it.bucketFilterKey() == filterBucket }
                    ?.bucketName
                    ?.ifBlank { null }
                    ?: "未知相册"
            add(bucketName)
        }
    }.joinToString(" · ")

private fun MediaAsset.bucketFilterKey(): String =
    bucketId.ifBlank { bucketName.ifBlank { "unknown" } }

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
                .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(accent.copy(alpha = 0.22f), CircleShape)
                .border(BorderStroke(1.dp, accent.copy(alpha = 0.48f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = "$label，$helper", tint = accent)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
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
