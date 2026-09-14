package com.example.photoorganizer.domain

import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaType

/** 本地整理队列类型（PRD 4.1 / 4.3）。 */
enum class QueueType {
    RANDOM,
    SCREENSHOT,
    LARGE_VIDEO,
    MONTH,
}

/** 一个整理队列及其预计可释放空间（PRD 4.3 / 4.6）。 */
data class MediaQueue(
    val type: QueueType,
    val items: List<MediaAsset>,
    val estimatedSavingBytes: Long,
)

/**
 * 本地规则队列引擎（PRD 8.2.1 QueueEngine）。
 * MVP 不依赖 AI，仅基于元数据过滤。AI 队列后续在阶段二扩展。
 */
object QueueEngine {
    private const val LARGE_VIDEO_BYTES = 100L * 1024 * 1024

    fun build(assets: List<MediaAsset>): List<MediaQueue> {
        val screenshots = assets.filter { it.isScreenshot() }
        val largeVideos = assets.filter { it.mediaType == MediaType.VIDEO && it.size >= LARGE_VIDEO_BYTES }
        return listOf(
            MediaQueue(QueueType.RANDOM, assets.shuffled(), assets.sumOf { it.size }),
            MediaQueue(QueueType.SCREENSHOT, screenshots, screenshots.sumOf { it.size }),
            MediaQueue(QueueType.LARGE_VIDEO, largeVideos, largeVideos.sumOf { it.size }),
        )
    }
}
