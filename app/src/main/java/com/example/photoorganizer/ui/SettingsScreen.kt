package com.example.photoorganizer.ui

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    var notificationGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    val shouldRequestNotificationPermission =
        !notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationGranted = granted
            vm.setReminderEnabled(granted)
        }

    var goalText by remember { mutableStateOf(s.dailyGoal.toString()) }
    LaunchedEffect(s.dailyGoal) { goalText = s.dailyGoal.toString() }
    var reminderIntervalText by remember { mutableStateOf(s.reminderIntervalDays.toString()) }
    var quietStartText by remember { mutableStateOf(s.quietStartHour.toString()) }
    var quietEndText by remember { mutableStateOf(s.quietEndHour.toString()) }
    LaunchedEffect(s.reminderIntervalDays) { reminderIntervalText = s.reminderIntervalDays.toString() }
    LaunchedEffect(s.quietStartHour) { quietStartText = s.quietStartHour.toString() }
    LaunchedEffect(s.quietEndHour) { quietEndText = s.quietEndHour.toString() }
    var showResetConfirm by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("设置") }) }) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(title = "整理节奏") {
                Text(
                    "设置每天想处理的照片数量。首页会用它显示今日进度。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { text ->
                        goalText = text
                        text.toIntOrNull()?.let { vm.setDailyGoal(it.coerceAtLeast(0)) }
                    },
                    label = { Text("每日整理目标") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsCard(title = "整理提醒") {
                Text(
                    "开启后，后台扫描发现还有未整理照片，并且今日目标未完成时，会发一条温和提醒。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("每日轻提醒", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (s.reminderEnabled) {
                                "每 ${s.reminderIntervalDays} 天最多一次，静默 ${s.quietStartHour}:00-${s.quietEndHour}:00；点通知会直接进入短队列。"
                            } else {
                                "关闭后只保留首页和统计页的今日进度。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.reminderEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && shouldRequestNotificationPermission) {
                                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setReminderEnabled(enabled)
                            }
                        },
                    )
                }
                if (s.reminderEnabled) {
                    OutlinedTextField(
                        value = reminderIntervalText,
                        onValueChange = { text ->
                            val next = text.filter { it.isDigit() }.take(2)
                            reminderIntervalText = next
                            next.toIntOrNull()?.let { vm.setReminderIntervalDays(it.coerceIn(1, 30)) }
                        },
                        label = { Text("提醒间隔（天）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = quietStartText,
                            onValueChange = { text ->
                                val next = text.filter { it.isDigit() }.take(2)
                                quietStartText = next
                                next.toIntOrNull()?.let { hour ->
                                    vm.setQuietHours(hour.coerceIn(0, 23), s.quietEndHour)
                                }
                            },
                            label = { Text("静默开始") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = quietEndText,
                            onValueChange = { text ->
                                val next = text.filter { it.isDigit() }.take(2)
                                quietEndText = next
                                next.toIntOrNull()?.let { hour ->
                                    vm.setQuietHours(s.quietStartHour, hour.coerceIn(0, 23))
                                }
                            },
                            label = { Text("静默结束") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                if (s.reminderEnabled && !notificationGranted) {
                    Text(
                        "系统通知权限未开启，提醒暂时不会弹出。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("允许通知")
                    }
                }
            }

            SettingsCard(title = "删除确认") {
                Text(
                    "整理页上滑只会加入待删除。真正删除前，需要到待删除复核页再次确认。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "系统支持最近删除时，会优先移入系统回收站；不支持时会提示永久删除风险。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard(title = "系统影响边界") {
                SettingsPlainRow(
                    title = "相册归类",
                    body = "加入或移出相册不会删除照片文件。",
                )
                SettingsPlainRow(
                    title = "删除照片",
                    body = "只有在待删除复核页确认，并通过系统弹窗后，才会影响系统相册。",
                )
                SettingsPlainRow(
                    title = "一键还原",
                    body = "只还原本应用整理状态，无法恢复已经被系统确认删除的真实文件。",
                )
            }

            if (isDebuggable) {
                SettingsCard(title = "测试工具") {
                    Text(
                        "清空整理状态、待删除、相册归类、操作记录和今日计数；不影响系统相册文件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { showResetConfirm = true }) {
                        Text("一键还原")
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
                    "这会让照片重新回到未整理队列，并清空待删除、相册归类和测试记录。" +
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
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SettingsPlainRow(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun hasNotificationPermission(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
