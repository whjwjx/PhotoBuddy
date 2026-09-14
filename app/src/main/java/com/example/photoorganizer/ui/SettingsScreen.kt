package com.example.photoorganizer.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** 设置页：整理策略与删除安全策略（PRD 六·设置 / 11.4 误删风险）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm: HomeViewModel = viewModel()
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()
    val s = state.settings
    val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    var daysText by remember { mutableStateOf(s.protectRecentDays.toString()) }
    LaunchedEffect(s.protectRecentDays) { daysText = s.protectRecentDays.toString() }
    var chunkText by remember { mutableStateOf(s.batchChunkSize.toString()) }
    LaunchedEffect(s.batchChunkSize) { chunkText = s.batchChunkSize.toString() }
    var goalText by remember { mutableStateOf(s.dailyGoal.toString()) }
    LaunchedEffect(s.dailyGoal) { goalText = s.dailyGoal.toString() }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
        ) {
            Text("删除安全策略", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.fillMaxWidth(0.55f)) {
                            Text("保护收藏内容")
                            Text(
                                "收藏项默认不进入批量删除候选",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = s.protectFavorite,
                            onCheckedChange = { vm.setProtectFavorite(it) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.fillMaxWidth(0.55f)) {
                            Text("保护最近拍摄")
                            Text(
                                "最近 N 天拍摄的内容不进入批量删除候选，0 表示不保护",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedTextField(
                            value = daysText,
                            onValueChange = { text ->
                                daysText = text
                                text.toIntOrNull()?.let { vm.setProtectRecentDays(it) }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(0.35f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("整理策略", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.fillMaxWidth(0.55f)) {
                            Text("每批删除上限")
                            Text(
                                "分批执行，避免系统请求 URI 数量上限",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedTextField(
                            value = chunkText,
                            onValueChange = { text ->
                                chunkText = text
                                text.toIntOrNull()?.let { vm.setBatchChunk(it) }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(0.35f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.fillMaxWidth(0.55f)) {
                            Text("每日整理目标")
                            Text(
                                "每天整理多少张算完成今日任务",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedTextField(
                            value = goalText,
                            onValueChange = { text ->
                                goalText = text
                                text.toIntOrNull()?.let { vm.setDailyGoal(it) }
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(0.35f),
                        )
                    }
                }
            }

            if (isDebuggable) {
                Spacer(Modifier.height(16.dp))
                Text("测试工具", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("重置 App 内测试状态")
                        Text(
                            "清空整理状态、待删除、相册归类、操作记录和今日计数；不影响系统相册文件。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { showResetConfirm = true }) {
                            Text("一键还原")
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("还原测试状态？") },
            text = {
                Text(
                    "这会让照片重新回到未整理队列，并清空 App 内待删除、相册归类和测试记录。" +
                        "已经通过系统确认删除的文件不会恢复。",
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.resetForTesting()
                    showResetConfirm = false
                }) {
                    Text("确认还原")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showResetConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}
