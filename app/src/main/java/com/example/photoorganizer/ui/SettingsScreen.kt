package com.example.photoorganizer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** 设置页：整理策略与删除安全策略（PRD 六·设置 / 11.4 误删风险）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val vm: HomeViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val s = state.settings

    var daysText by remember { mutableStateOf(s.protectRecentDays.toString()) }
    LaunchedEffect(s.protectRecentDays) { daysText = s.protectRecentDays.toString() }
    var chunkText by remember { mutableStateOf(s.batchChunkSize.toString()) }
    LaunchedEffect(s.batchChunkSize) { chunkText = s.batchChunkSize.toString() }

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
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
                }
            }
        }
    }
}
