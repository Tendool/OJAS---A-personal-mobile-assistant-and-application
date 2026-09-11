package com.ojas.assistant.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.getSystemService
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.dao.DayTotal
import com.ojas.assistant.data.local.dao.ScreenTimeDao
import com.ojas.assistant.data.local.entity.AppLimitEntity
import com.ojas.assistant.data.local.entity.FocusSessionEntity
import com.ojas.assistant.data.local.entity.ScreenTimeDayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class AppUsage(
    val packageName: String,
    val label: String,
    val minutes: Int
)

/**
 * Reads usage from [UsageStatsManager] and keeps a compact daily snapshot in Room so
 * history survives past the window the platform retains, and so charts can be drawn
 * without hitting the (relatively slow) stats provider on every recomposition.
 */
class ScreenTimeRepository(
    private val context: Context,
    private val dao: ScreenTimeDao
) {

    private val packageManager: PackageManager get() = context.packageManager

    private val labelCache = HashMap<String, String>()

    // ------------------------------------------------------------- permission

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService<AppOpsManager>() ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ------------------------------------------------------------------ usage

    /**
     * Foreground time per app for [epochDay], derived from the raw event stream.
     *
     * Event replay is used rather than queryUsageStats because the bucketed API reports
     * totals for the whole bucket rather than the requested slice, which makes a mid-day
     * "so far today" figure wrong on most devices.
     */
    suspend fun usageFor(epochDay: Long = TimeUtils.todayEpochDay()): List<AppUsage> =
        withContext(Dispatchers.IO) {
            if (!hasUsageAccess()) return@withContext emptyList()
            val usm = context.getSystemService<UsageStatsManager>() ?: return@withContext emptyList()

            val start = TimeUtils.startOfDayMillis(epochDay)
            val end = minOf(TimeUtils.endOfDayMillis(epochDay), TimeUtils.now())
            if (end <= start) return@withContext emptyList()

            val totals = HashMap<String, Long>()
            val openedAt = HashMap<String, Long>()
            val events = usm.queryEvents(start, end)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> openedAt[pkg] = event.timeStamp
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val from = openedAt.remove(pkg) ?: continue
                        if (event.timeStamp > from) {
                            totals[pkg] = (totals[pkg] ?: 0L) + (event.timeStamp - from)
                        }
                    }
                }
            }
            // Whatever is still in the foreground counts up to "now".
            openedAt.forEach { (pkg, from) ->
                if (end > from) totals[pkg] = (totals[pkg] ?: 0L) + (end - from)
            }

            totals.entries
                .mapNotNull { (pkg, millis) ->
                    val minutes = (millis / 60_000L).toInt()
                    if (minutes < 1 || pkg == context.packageName) return@mapNotNull null
                    if (!isLaunchable(pkg)) return@mapNotNull null
                    AppUsage(pkg, labelFor(pkg), minutes)
                }
                .sortedByDescending { it.minutes }
        }

    suspend fun foregroundPackage(windowMillis: Long = 10_000L): String? =
        withContext(Dispatchers.IO) {
            val usm = context.getSystemService<UsageStatsManager>() ?: return@withContext null
            val now = TimeUtils.now()
            val events = usm.queryEvents(now - windowMillis, now)
            val event = UsageEvents.Event()
            var latest: String? = null
            var latestAt = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= latestAt) {
                    latestAt = event.timeStamp
                    latest = event.packageName
                }
            }
            latest
        }

    suspend fun minutesUsedToday(packageName: String): Int =
        usageFor().firstOrNull { it.packageName == packageName }?.minutes ?: 0

    /** Persists today's figures so the weekly chart keeps working offline and long term. */
    suspend fun snapshotToday() {
        val day = TimeUtils.todayEpochDay()
        val rows = usageFor(day).map {
            ScreenTimeDayEntity(day, it.packageName, it.label, it.minutes)
        }
        if (rows.isNotEmpty()) dao.upsertDays(rows)
        dao.prune(day - 400)
    }

    // ----------------------------------------------------------------- limits

    fun observeLimits(): Flow<List<AppLimitEntity>> = dao.observeLimits()

    suspend fun activeLimits(): List<AppLimitEntity> = dao.activeLimits()

    suspend fun setLimit(packageName: String, minutes: Int, hardBlock: Boolean) {
        dao.upsertLimit(
            AppLimitEntity(
                packageName = packageName,
                appLabel = labelFor(packageName),
                dailyLimitMinutes = minutes.coerceIn(1, 1440),
                enabled = true,
                hardBlock = hardBlock
            )
        )
    }

    suspend fun removeLimit(packageName: String) = dao.deleteLimit(packageName)

    fun observeDay(epochDay: Long = TimeUtils.todayEpochDay()): Flow<List<ScreenTimeDayEntity>> =
        dao.observeDay(epochDay)

    fun lastDays(count: Int): Flow<List<DayTotal>> {
        val today = TimeUtils.todayEpochDay()
        return dao.dailyTotals(today - (count - 1), today)
    }

    // ---------------------------------------------------------- focus history

    fun recentFocus(limit: Int = 20): Flow<List<FocusSessionEntity>> = dao.recentFocus(limit)

    fun focusMinutesToday(): Flow<Int> = dao.focusMinutesForDay(TimeUtils.todayEpochDay())

    suspend fun recordFocus(startedAt: Long, endedAt: Long, plannedMinutes: Int, completed: Boolean) {
        dao.insertFocus(
            FocusSessionEntity(
                startedAt = startedAt,
                endedAt = endedAt,
                epochDay = TimeUtils.epochDayOf(startedAt),
                plannedMinutes = plannedMinutes,
                completed = completed
            )
        )
    }

    // ------------------------------------------------------------- app lookup

    suspend fun launchableApps(): List<AppUsage> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName }
            .distinct()
            .map { AppUsage(it, labelFor(it), 0) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun labelFor(packageName: String): String = labelCache.getOrPut(packageName) {
        runCatching {
            val info: ApplicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName.substringAfterLast('.'))
    }

    private fun isLaunchable(packageName: String): Boolean =
        runCatching { packageManager.getLaunchIntentForPackage(packageName) != null }
            .getOrDefault(false)
}
