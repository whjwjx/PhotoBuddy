package com.example.photoorganizer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.photoorganizer.R

/** 一级入口（PRD 六·信息架构）。 */
enum class AppTab(val label: String) {
    HOME("整理"),
    ORGANIZE("整理"),
    TRASH("待删除"),
    ALBUMS("相册"),
    STATS("统计"),
    SETTINGS("设置"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    initialTab: AppTab = AppTab.HOME,
    initialQueueTypeName: String? = null,
    initialQueueRequestId: Int = 0,
) {
    val context = LocalContext.current
    val permissions = remember { requiredPermissions() }
    var granted by remember {
        mutableStateOf(hasMediaAccess(context))
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = hasMediaAccess(context)
    }

    if (!granted) {
        PermissionScreen(
            onRequest = { launcher.launch(permissions.toTypedArray()) },
        )
        return
    }

    var tab by remember { mutableStateOf(initialTab) }
    var pendingQueueTypeName by remember { mutableStateOf(initialQueueTypeName) }
    LaunchedEffect(initialTab) {
        tab = initialTab
    }
    LaunchedEffect(initialQueueRequestId, initialQueueTypeName) {
        pendingQueueTypeName = initialQueueTypeName
    }
    Scaffold(
        // 刷照片流是全屏沉浸式，隐藏底部导航
        bottomBar = {
            if (tab != AppTab.ORGANIZE) {
                NavigationBar {
                    listOf(AppTab.HOME, AppTab.ALBUMS, AppTab.STATS, AppTab.SETTINGS).forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                Icon(
                                    imageVector =
                                        when (t) {
                                            AppTab.HOME -> Icons.Default.Home
                                            AppTab.TRASH -> Icons.Default.DeleteOutline
                                            AppTab.ALBUMS -> Icons.Default.PhotoAlbum
                                            AppTab.STATS -> Icons.Default.BarChart
                                            AppTab.SETTINGS -> Icons.Default.Settings
                                            AppTab.ORGANIZE -> Icons.Default.Home
                                        },
                                    contentDescription = t.label,
                                )
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                AppTab.HOME -> HomeScreen(
                    onStartOrganize = { tab = AppTab.ORGANIZE },
                    onOpenTrash = { tab = AppTab.TRASH },
                )
                AppTab.ORGANIZE -> FeedScreen(
                    initialQueueTypeName = pendingQueueTypeName,
                    onInitialQueueConsumed = { pendingQueueTypeName = null },
                    onExit = { tab = AppTab.HOME },
                    onOpenTrash = { tab = AppTab.TRASH },
                )
                AppTab.TRASH -> TrashScreen(onExit = { tab = AppTab.HOME })
                AppTab.ALBUMS -> AlbumsScreen()
                AppTab.STATS -> StatsScreen(
                    onPickQueue = { tab = AppTab.ORGANIZE },
                    onOpenTrash = { tab = AppTab.TRASH },
                    onOpenSettings = { tab = AppTab.SETTINGS },
                )
                AppTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

private fun requiredPermissions(): List<String> =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
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

private fun hasMediaAccess(context: android.content.Context): Boolean =
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            val hasImages =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) ==
                    PackageManager.PERMISSION_GRANTED
            val hasVideo =
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) ==
                    PackageManager.PERMISSION_GRANTED
            val hasPartial =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    ) == PackageManager.PERMISSION_GRANTED
            hasImages || hasVideo || hasPartial
        }
        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
            requiredPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        else ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "允许访问相册后开始整理",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "请选择允许访问所有照片和视频，这样短队列、相似照片和待删除复核才会完整。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                        Text("允许访问全部照片和视频")
                    }
                }
            }

            PermissionTip(
                icon = Icons.Default.CloudOff,
                title = "只整理本机媒体",
                body = "照片和视频只在设备上扫描，不会上传到云端。",
            )
            PermissionTip(
                icon = Icons.Default.DeleteOutline,
                title = "不会自动删除",
                body = "上滑只是加入待删除，真正删除前还要在复核页确认。",
            )
            PermissionTip(
                icon = Icons.Default.Security,
                title = "相册先用 App 内标签",
                body = "加入相册不会移动系统文件，后续需要同步系统相册时再单独开启。",
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionTip(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.06f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
