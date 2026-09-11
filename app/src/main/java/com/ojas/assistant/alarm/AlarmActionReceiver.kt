package com.ojas.assistant.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.di.ojas
import kotlinx.coroutines.launch

/** Handles the buttons Ojas puts in the shade and on the ringing screen. */
class AlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_SNOOZE_ALARM -> {
                val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, 0L)
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 9).coerceIn(1, 120)
                AlarmRingService.stop(app)
                app.ojas.scheduler.scheduleSnooze(id, TimeUtils.now() + minutes * 60_000L)
            }

            ACTION_DISMISS_ALARM -> {
                val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, 0L)
                AlarmRingService.stop(app)
                app.ojas.scheduler.cancelSnooze(id)
            }

            ACTION_SNOOZE_REMINDER -> {
                val id = intent.getLongExtra(AlarmReceiver.EXTRA_ID, 0L)
                NotificationCenter.cancel(app, NotificationCenter.ID_REMINDER_BASE + id.toInt())
                val pending = goAsync()
                app.ojas.appScope.launch {
                    try {
                        val reminder = app.ojas.reminders.byId(id) ?: return@launch
                        app.ojas.reminders.save(
                            reminder.copy(
                                triggerAt = TimeUtils.now() + 10 * 60_000L,
                                enabled = true
                            )
                        )
                    } finally {
                        pending.finish()
                    }
                }
            }

            ACTION_LOG_WATER -> {
                val amount = intent.getIntExtra(EXTRA_AMOUNT, 250)
                NotificationCenter.cancel(app, NotificationCenter.ID_WATER)
                val pending = goAsync()
                app.ojas.appScope.launch {
                    try {
                        app.ojas.water.log(amount)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE_ALARM = "com.ojas.assistant.action.SNOOZE_ALARM"
        const val ACTION_DISMISS_ALARM = "com.ojas.assistant.action.DISMISS_ALARM"
        const val ACTION_SNOOZE_REMINDER = "com.ojas.assistant.action.SNOOZE_REMINDER"
        const val ACTION_LOG_WATER = "com.ojas.assistant.action.LOG_WATER"

        const val EXTRA_MINUTES = "ojas.extra.minutes"
        const val EXTRA_AMOUNT = "ojas.extra.amount"
    }
}
