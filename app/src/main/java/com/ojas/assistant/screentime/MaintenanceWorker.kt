package com.ojas.assistant.screentime

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ojas.assistant.di.ojas
import java.util.concurrent.TimeUnit

/**
 * Low-frequency housekeeping: snapshot today's screen time into the database, drop
 * history nobody will ever look at, and re-arm anything AlarmManager may have dropped
 * (an OS update, a force stop, or an exact-alarm permission that was just granted).
 *
 * Deliberately deferrable — nothing here is time critical, so it batches with whatever
 * else the system is already waking up for.
 */
class MaintenanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val container = applicationContext.ojas

        if (container.screenTime.hasUsageAccess()) {
            container.screenTime.snapshotToday()
        }
        container.water.prune()

        container.alarms.rescheduleAll()
        container.reminders.rescheduleAll()
        container.calendar.rescheduleAll()
        container.water.rescheduleNudge()

        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        private const val NAME = "ojas-maintenance"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
