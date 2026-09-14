package com.example.photoorganizer.domain

import com.example.photoorganizer.data.MediaAsset
import com.example.photoorganizer.data.MediaType

/** 相册统计（PRD 4.1 首页统计：总量、图片/视频、占用空间、截图/大视频数）。 */
data class LibraryStats(
    val total: Int,
    val images: Int,
    val videos: Int,
    val totalBytes: Long,
    val screenshotCount: Int,
    val largeVideoCount: Int,
)

object StatsService {
    private const val LARGE_VIDEO_BYTES = 100L * 1024 * 1024

    fun compute(assets: List<MediaAsset>): LibraryStats =
        LibraryStats(
            total = assets.size,
            images = assets.count { it.mediaType == MediaType.IMAGE },
            videos = assets.count { it.mediaType == MediaType.VIDEO },
            totalBytes = assets.sumOf { it.size },
            screenshotCount = assets.count { it.isScreenshot() },
            largeVideoCount =
                assets.count {
                    it.mediaType == MediaType.VIDEO && it.size >= LARGE_VIDEO_BYTES
                },
        )
}
