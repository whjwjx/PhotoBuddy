package com.example.photoorganizer.ui

import com.example.photoorganizer.data.local.UserActionLogEntity
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun formatBytes(bytes: Long): String {
    val df = DecimalFormat("#.##")
    return when {
        bytes >= 1024L * 1024 * 1024 -> "${df.format(bytes / (1024.0 * 1024 * 1024))} GB"
        bytes >= 1024L * 1024 -> "${df.format(bytes / (1024.0 * 1024))} MB"
        bytes >= 1024 -> "${df.format(bytes / 1024.0)} KB"
        else -> "$bytes B"
    }
}

internal fun formatDate(ms: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

/** 视频时长：mm:ss。 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    return String.format(Locale.getDefault(), "%02d:%02d", totalSec / 60, totalSec % 60)
}

internal fun actionLabel(log: UserActionLogEntity): String =
    when (log.action) {
        "keep" -> "已保留"
        "album" -> "已归类"
        "later" -> "稍后"
        "trash" -> "待删除"
        "favorite" -> "已收藏"
        "delete" -> if (log.freedBytes > 0) "已删除 ${formatBytes(log.freedBytes)}" else "已删除"
        "restore" -> "已恢复"
        "undo" -> "已撤销"
        "permanent" -> "永久保留"
        else -> log.action
    }
