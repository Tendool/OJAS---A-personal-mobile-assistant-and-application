package com.ojas.assistant.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ojas.assistant.di.ojas
import com.ojas.assistant.screentime.MaintenanceWorker
import com.ojas.assistant.screentime.ScreenTimeMonitorService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AlarmManager forgets everything across a reboot, a timezone change and an app update,
 * so every one of those events replays the schedule out of the database.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> Unit
            else -> return
        }

        val app = context.applicationContext
        val pending = goAsync()
        app.ojas.appScope.launch {
            try {
                NotificationCenter.ensureChannels(app)
                app.ojas.alarms.rescheduleAll()
                app.ojas.reminders.rescheduleAll()
                app.ojas.calendar.rescheduleAll()
                app.ojas.water.rescheduleNudge()
                MaintenanceWorker.enqueue(app)

                if (app.ojas.settings.settings.first().screenTimeEnabled) {
                    ScreenTimeMonitorService.start(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
