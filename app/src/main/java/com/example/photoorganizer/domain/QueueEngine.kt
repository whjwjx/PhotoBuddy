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
    SIMILAR("相似照片"),
    RECENT_30("最近 30 天"),
    MONTH("按月份"),
    UNPROCESSED("未整理"),
    LATER("稍后"),
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
        laterIds: Set<Long> = emptySet(),
    ): List<MediaQueue> {
        val now = System.currentTimeMillis()
        val unprocessed = assets.filter { it.id !in processedIds }
        val later = assets.filter { it.id in laterIds }
        val out = mutableListOf<MediaQueue>()
        out += queue(QueueType.RANDOM, "", unprocessed)
        out += queue(QueueType.SCREENSHOT, "", unprocessed.filter { it.isScreenshot() })
        out += queue(
            QueueType.LARGE_VIDEO,
            "",
            unprocessed.filter { it.mediaType == MediaType.VIDEO && it.size >= LARGE_VIDEO_BYTES },
        )
        out += queue(QueueType.SIMILAR, "", similarCandidates(unprocessed))
        out += queue(
            QueueType.RECENT_30,
            "",
            unprocessed.filter { it.capturedAt > 0 && it.capturedAt >= now - 30 * DAY_MS },
        )
        out += queue(QueueType.UNPROCESSED, "", unprocessed)
        out += queue(QueueType.LATER, "", later)
        out += queue(QueueType.FAVORITE, "", unprocessed.filter { it.isFavorite })

        // 按月份拆分为多个队列，便于逐步整理历史相册（PRD 4.3 某个月份）
        unprocessed
            .groupBy { monthKey(it.capturedAt) }
            .toSortedMap(compareByDescending<String> { it })
            .forEach { (key, items) -> out += queue(QueueType.MONTH, key, items) }
        return out
    }

    fun isSimilarGroupPeer(
        a: MediaAsset,
        b: MediaAsset,
    ): Boolean =
        a.mediaType == MediaType.IMAGE &&
            b.mediaType == MediaType.IMAGE &&
            a.width > 0 &&
            a.height > 0 &&
            b.width > 0 &&
            b.height > 0 &&
            SimilarBucket.from(a) == SimilarBucket.from(b)

    private fun queue(
        type: QueueType,
        title: String,
        items: List<MediaAsset>,
    ) = MediaQueue(type, title, items, items.sumOf { it.size })

    private fun similarCandidates(assets: List<MediaAsset>): List<MediaAsset> =
        assets
            .asSequence()
            .filter { it.mediaType == MediaType.IMAGE && it.width > 0 && it.height > 0 }
            .groupBy { SimilarBucket.from(it) }
            .values
            .filter { it.size >= 2 }
            .flatten()
            .sortedWith(compareBy<MediaAsset> { it.bucketName }.thenBy { captureOrAddedMs(it) }.thenBy { it.id })

    private fun monthKey(ms: Long): String {
        if (ms <= 0) return "未知时间"
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return String.format(Locale.getDefault(), "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    private data class SimilarBucket(
        val bucketId: String,
        val timeWindow: Long,
        val ratio: Int,
        val longSide: Int,
    ) {
        companion object {
            private const val WINDOW_MS = 5L * 60L * 1000L
            private const val SIDE_BUCKET = 160

            fun from(asset: MediaAsset): SimilarBucket {
                val long = maxOf(asset.width, asset.height)
                val short = minOf(asset.width, asset.height).coerceAtLeast(1)
                return SimilarBucket(
                    bucketId = asset.bucketId.ifEmpty { asset.bucketName },
                    timeWindow = captureOrAddedMs(asset) / WINDOW_MS,
                    ratio = (long * 100 / short),
                    longSide = long / SIDE_BUCKET,
                )
            }
        }
    }

    private fun captureOrAddedMs(asset: MediaAsset): Long =
        when {
            asset.capturedAt > 0 -> asset.capturedAt
            asset.dateAdded > 0 -> asset.dateAdded * 1000L
            else -> 0L
        }
}
