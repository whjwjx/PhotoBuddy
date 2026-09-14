package com.example.photoorganizer.data

import android.content.Context
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
    }

    val settings: Flow<OrganizeSettings> =
        context.settingsDataStore.data.map { p ->
            OrganizeSettings(
                dailyGoal = p[Keys.DAILY_GOAL] ?: 20,
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

    suspend fun setDailyGoal(v: Int) {
        context.settingsDataStore.edit { it[Keys.DAILY_GOAL] = v }
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
            p[Keys.DAILY_COUNT] = cur + n
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
}
