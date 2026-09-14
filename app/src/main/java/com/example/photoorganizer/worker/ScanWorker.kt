package com.example.photoorganizer.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.photoorganizer.data.MediaLibraryRepository
import com.example.photoorganizer.data.MediaStoreRepository
import com.example.photoorganizer.data.SettingsRepository
import com.example.photoorganizer.data.local.AppDatabase
import java.util.concurrent.TimeUnit

/**
 * 后台增量扫描（PRD 8.2.1：WorkManager 执行增量扫描等可中断后台任务）。
 * 只同步上次扫描之后新增/删除的媒体，避免每次全量扫描。
 */
class ScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result =
        try {
            repository().sync(full = false)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }

    private fun repository(): MediaLibraryRepository {
        val db = AppDatabase.getDatabase(applicationContext)
        return MediaLibraryRepository(
            MediaStoreRepository(applicationContext.contentResolver),
            db.mediaIndexDao(),
            SettingsRepository(applicationContext),
        )
    }

    companion object {
        private const val PERIODIC_NAME = "incremental_scan"
        private const val ONCE_NAME = "scan_once"

        /** 每日一次增量扫描（电量不低时执行）。 */
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
            val request =
                PeriodicWorkRequestBuilder<ScanWorker>(1, TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** 立即执行一次增量扫描。 */
        fun enqueueOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<ScanWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
