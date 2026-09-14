package com.example.photoorganizer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.photoorganizer.R

/** 一级入口（PRD 六·信息架构）。 */
enum class AppTab(val label: String) {
    HOME("首页"),
    ORGANIZE("整理"),
    ALBUMS("相册"),
    SETTINGS("设置"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val permissions = remember { requiredPermissions() }
    var granted by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            },
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            granted = result.values.all { it }
        }

    if (!granted) {
        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
            ) {
                Button(onClick = { launcher.launch(permissions.toTypedArray()) }) {
                    Text("授权读取相册")
                }
                Text("需要相册读取权限才能扫描本地照片和视频。", Modifier.padding(top = 8.dp))
            }
        }
        return
    }

    var tab by remember { mutableStateOf(AppTab.HOME) }
    Scaffold(
        // 刷照片流是全屏沉浸式，隐藏底部导航
        bottomBar = {
            if (tab != AppTab.ORGANIZE) {
                NavigationBar {
                    AppTab.values().forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {},
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                AppTab.HOME -> HomeScreen(onStartOrganize = { tab = AppTab.ORGANIZE })
                AppTab.ORGANIZE -> FeedScreen(onExit = { tab = AppTab.HOME })
                AppTab.ALBUMS -> AlbumsScreen()
                AppTab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

private fun requiredPermissions(): List<String> =
    when {
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
