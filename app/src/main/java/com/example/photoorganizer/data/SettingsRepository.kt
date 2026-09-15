package com.example.photoorganizer.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 整理节奏设置（PRD 六·设置）。 */
data class OrganizeSettings(
    /** 每日整理目标数量。 */
    val dailyGoal: Int = 20,
    /** 是否开启每日整理提醒。 */
    val reminderEnabled: Boolean = false,
    /** 整理提醒最小间隔天数。 */
    val reminderIntervalDays: Int = 1,
    /** 静默开始小时（0-23）。 */
    val quietStartHour: Int = 22,
    /** 静默结束小时（0-23）。 */
    val quietEndHour: Int = 8,
)

/** 每日整理任务进度（PRD 4.1 / 阶段 4）。 */
data class DailyProgress(
    val date: String,
    val count: Int,
) {
    fun percent(goal: Int): Float = if (goal <= 0) 1f else (count.coerceAtMost(goal).toFloat() / goal)
}

class SettingsRepository(
    private val context: Context,
) {
    private object Keys {
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val LAST_SCAN_MS = longPreferencesKey("last_scan_ms")
        val DAILY_DATE = stringPreferencesKey("daily_date")
        val DAILY_COUNT = intPreferencesKey("daily_count")
        val PINNED_ALBUM_IDS = stringPreferencesKey("pinned_album_ids")
        val HIDDEN_ALBUM_IDS = stringPreferencesKey("hidden_album_ids")
        val ALBUM_ORDER_IDS = stringPreferencesKey("album_order_ids")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_INTERVAL_DAYS = intPreferencesKey("reminder_interval_days")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val LAST_REMINDER_DATE = stringPreferencesKey("last_reminder_date")
    }

    val settings: Flow<OrganizeSettings> =
        context.settingsDataStore.data.map { p ->
            OrganizeSettings(
                dailyGoal = p[Keys.DAILY_GOAL] ?: 20,
                reminderEnabled = p[Keys.REMINDER_ENABLED] ?: false,
                reminderIntervalDays = (p[Keys.REMINDER_INTERVAL_DAYS] ?: 1).coerceAtLeast(1),
                quietStartHour = (p[Keys.QUIET_START_HOUR] ?: 22).coerceIn(0, 23),
                quietEndHour = (p[Keys.QUIET_END_HOUR] ?: 8).coerceIn(0, 23),
            )
        }

    val daily: Flow<DailyProgress> =
        context.settingsDataStore.data.map { p ->
            val date = p[Keys.DAILY_DATE]
            if (date == today()) {
                DailyProgress(date, p[Keys.DAILY_COUNT] ?: 0)
            } else {
                DailyProgress(today(), 0)
            }
        }

    val pinnedAlbumIds: Flow<Set<Long>> =
        context.settingsDataStore.data.map { p -> parseIdSet(p[Keys.PINNED_ALBUM_IDS].orEmpty()) }

    val hiddenAlbumIds: Flow<Set<Long>> =
        context.settingsDataStore.data.map { p -> parseIdSet(p[Keys.HIDDEN_ALBUM_IDS].orEmpty()) }

    val albumOrderIds: Flow<List<Long>> =
        context.settingsDataStore.data.map { p -> parseIdList(p[Keys.ALBUM_ORDER_IDS].orEmpty()) }

    suspend fun setDailyGoal(v: Int) {
        context.settingsDataStore.edit { it[Keys.DAILY_GOAL] = v }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderIntervalDays(days: Int) {
        context.settingsDataStore.edit { it[Keys.REMINDER_INTERVAL_DAYS] = days.coerceAtLeast(1) }
    }

    suspend fun setQuietHours(
        startHour: Int,
        endHour: Int,
    ) {
        context.settingsDataStore.edit { p ->
            p[Keys.QUIET_START_HOUR] = startHour.coerceIn(0, 23)
            p[Keys.QUIET_END_HOUR] = endHour.coerceIn(0, 23)
        }
    }

    suspend fun getLastReminderDate(): String =
        context.settingsDataStore.data.first()[Keys.LAST_REMINDER_DATE].orEmpty()

    suspend fun markReminderPosted() {
        context.settingsDataStore.edit { it[Keys.LAST_REMINDER_DATE] = today() }
    }

    suspend fun setAlbumPinned(
        albumId: Long,
        pinned: Boolean,
    ) {
        context.settingsDataStore.edit { p ->
            val current = parseIdSet(p[Keys.PINNED_ALBUM_IDS].orEmpty())
            val next = if (pinned) current + albumId else current - albumId
            p[Keys.PINNED_ALBUM_IDS] = next.sorted().joinToString(",")
        }
    }

    suspend fun setAlbumHidden(
        albumId: Long,
        hidden: Boolean,
    ) {
        context.settingsDataStore.edit { p ->
            val current = parseIdSet(p[Keys.HIDDEN_ALBUM_IDS].orEmpty())
            val next = if (hidden) current + albumId else current - albumId
            p[Keys.HIDDEN_ALBUM_IDS] = next.sorted().joinToString(",")
        }
    }

    suspend fun setAlbumOrderIds(albumIds: List<Long>) {
        context.settingsDataStore.edit { p ->
            p[Keys.ALBUM_ORDER_IDS] = albumIds.distinct().joinToString(",")
        }
    }

    suspend fun getLastScanMs(): Long = context.settingsDataStore.data.first()[Keys.LAST_SCAN_MS] ?: 0L

    suspend fun setLastScanMs(v: Long) {
        context.settingsDataStore.edit { it[Keys.LAST_SCAN_MS] = v }
    }

    /** 记录今日整理数量；跨天自动清零。 */
    suspend fun addProcessed(n: Int) {
        context.settingsDataStore.edit { p ->
            val cur = if (p[Keys.DAILY_DATE] == today()) p[Keys.DAILY_COUNT] ?: 0 else 0
            p[Keys.DAILY_DATE] = today()
            p[Keys.DAILY_COUNT] = (cur + n).coerceAtLeast(0)
        }
    }

    /** 测试用：重置今日整理计数，让有限素材可以反复跑完整流程。 */
    suspend fun resetDaily() {
        context.settingsDataStore.edit { p ->
            p[Keys.DAILY_DATE] = today()
            p[Keys.DAILY_COUNT] = 0
        }
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun parseIdSet(raw: String): Set<Long> =
        parseIdList(raw).toSet()

    private fun parseIdList(raw: String): List<Long> =
        raw
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .distinct()
}
