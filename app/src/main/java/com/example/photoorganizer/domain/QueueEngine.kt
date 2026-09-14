package com.example.photoorganizer.domain

import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaType
import java.util.Calendar
import java.util.Locale

/** 本地整理队列类型（PRD 4.1 / 4.3）。 */
enum class QueueType(val label: String) {
    RANDOM("随机整理"),
    SCREENSHOT("截图"),
    LARGE_VIDEO("大视频"),
    RECENT_30("最近 30 天"),
    MONTH("按月份"),
    UNPROCESSED("未整理"),
    FAVORITE("收藏"),
}

/** 一个整理队列及其预计可释放空间（PRD 4.3 / 4.6）。 */
data class MediaQueue(
    val type: QueueType,
    val title: String,
    val items: List<MediaAsset>,
    val estimatedSavingBytes: Long,
) {
    val displayName: String get() = if (title.isEmpty()) type.label else "${type.label} · $title"
}

/**
 * 本地规则队列引擎（PRD 8.2.1 QueueEngine）。
 * MVP 不依赖 AI，仅基于元数据过滤。AI 队列后续在阶段二扩展。
 */
object QueueEngine {
    private const val LARGE_VIDEO_BYTES = 100L * 1024 * 1024
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * @param processedIds 已被用户处理过的媒体 ID（来自 Room），用于「未整理」队列。
     */
    fun build(
        assets: List<MediaAsset>,
        processedIds: Set<Long> = emptySet(),
    ): List<MediaQueue> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<MediaQueue>()
        out += queue(QueueType.RANDOM, "", assets)
        out += queue(QueueType.SCREENSHOT, "", assets.filter { it.isScreenshot() })
        out += queue(
            QueueType.LARGE_VIDEO,
            "",
            assets.filter { it.mediaType == MediaType.VIDEO && it.size >= LARGE_VIDEO_BYTES },
        )
        out += queue(
            QueueType.RECENT_30,
            "",
            assets.filter { it.capturedAt > 0 && it.capturedAt >= now - 30 * DAY_MS },
        )
        out += queue(QueueType.UNPROCESSED, "", assets.filter { it.id !in processedIds })
        out += queue(QueueType.FAVORITE, "", assets.filter { it.isFavorite })

        // 按月份拆分为多个队列，便于逐步整理历史相册（PRD 4.3 某个月份）
        assets
            .groupBy { monthKey(it.capturedAt) }
            .toSortedMap(compareByDescending<String> { it })
            .forEach { (key, items) -> out += queue(QueueType.MONTH, key, items) }
        return out
    }

    private fun queue(
        type: QueueType,
        title: String,
        items: List<MediaAsset>,
    ) = MediaQueue(type, title, items, items.sumOf { it.size })

    private fun monthKey(ms: Long): String {
        if (ms <= 0) return "未知时间"
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return String.format(Locale.getDefault(), "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }
}
