package com.example.photoorganizer.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.photoorganizer.MainActivity
import com.example.photoorganizer.data.MediaLibraryRepository
import com.example.photoorganizer.data.MediaStoreRepository
import com.example.photoorganizer.data.SettingsRepository
import com.example.photoorganizer.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
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
            maybeNotifyDailyReminder()
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

    private suspend fun maybeNotifyDailyReminder() {
        val settingsRepo = SettingsRepository(applicationContext)
        val settings = settingsRepo.settings.first()
        if (!settings.reminderEnabled || settings.dailyGoal <= 0) return
        if (isQuietHour(settings.quietStartHour, settings.quietEndHour)) return
        if (!isReminderIntervalDue(settingsRepo.getLastReminderDate(), settings.reminderIntervalDays)) return
        val daily = settingsRepo.daily.first()
        if (daily.count >= settings.dailyGoal) return

        val db = AppDatabase.getDatabase(applicationContext)
        val remaining = (db.mediaIndexDao().count() - db.mediaStatusDao().countAll()).coerceAtLeast(0)
        if (remaining <= 0 || !canPostNotifications()) return

        ensureChannel()
        val intent =
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_ORGANIZE, true)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setContentTitle("今天轻整理 ${settings.dailyGoal} 张照片")
                .setContentText("还有 $remaining 张未整理，点开直接进入短队列。")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        NotificationManagerCompat.from(applicationContext).notify(DAILY_REMINDER_ID, notification)
        settingsRepo.markReminderPosted()
    }

    private fun isQuietHour(
        startHour: Int,
        endHour: Int,
    ): Boolean {
        if (startHour == endHour) return false
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (startHour < endHour) {
            hour in startHour until endHour
        } else {
            hour >= startHour || hour < endHour
        }
    }

    private fun isReminderIntervalDue(
        lastDate: String,
        intervalDays: Int,
    ): Boolean {
        if (lastDate.isBlank()) return true
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val last = runCatching { format.parse(lastDate)?.time }.getOrNull() ?: return true
        val currentDate = format.format(Calendar.getInstance().time)
        val today = runCatching { format.parse(currentDate)?.time }.getOrNull() ?: return true
        val elapsedDays = ((today - last) / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0)
        return elapsedDays >= intervalDays
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "整理提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "提醒你用短队列轻量整理新增照片"
            }
        applicationContext
            .getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        private const val PERIODIC_NAME = "incremental_scan"
        private const val ONCE_NAME = "scan_once"
        private const val CHANNEL_ID = "daily_organize_reminder"
        private const val DAILY_REMINDER_ID = 1001

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
