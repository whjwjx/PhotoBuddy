package com.example.photoorganizer.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

/**
 * 普通页面使用的紧凑顶栏。
 *
 * App 当前没有启用沉浸式 edge-to-edge，系统已经为状态栏留出了空间；
 * Material3 TopAppBar 默认再叠加一次 statusBars inset，会让标题上方出现一大块空白。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}
