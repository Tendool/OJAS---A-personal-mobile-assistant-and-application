package com.ojas.assistant.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.ojas.assistant.MainActivity
import com.ojas.assistant.R

/**
 * One place that owns every channel, notification id and PendingIntent flag combination.
 * Channels are created eagerly in Application.onCreate so the very first alarm of a fresh
 * install can post without a round-trip through the UI.
 */
object NotificationCenter {

    const val CH_ALARM = "ojas.alarms"
    const val CH_REMINDER = "ojas.reminders"
    const val CH_FOCUS = "ojas.focus"
    const val CH_ONGOING = "ojas.ongoing"

    // Notification id space, kept disjoint from alarm request codes.
    const val ID_RINGING = 7001
    const val ID_SCREEN_TIME_SERVICE = 7002
    const val ID_WATER = 7003
    const val ID_LIMIT_WARNING_BASE = 7100
    const val ID_REMINDER_BASE = 20_000
    const val ID_EVENT_BASE = 40_000

    private const val PI_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return

        val alarm = NotificationChannel(
            CH_ALARM,
            context.getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.alarm_channel_desc)
            // The ringing service owns audio and vibration, so the channel stays silent
            // to avoid a second overlapping tone.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        val reminder = NotificationChannel(
            CH_REMINDER,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_desc)
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val focus = NotificationChannel(
            CH_FOCUS,
            context.getString(R.string.focus_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.focus_channel_desc) }

        val ongoing = NotificationChannel(
            CH_ONGOING,
            context.getString(R.string.ongoing_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = context.getString(R.string.ongoing_channel_desc)
            setShowBadge(false)
        }

        nm.createNotificationChannels(listOf(alarm, reminder, focus, ongoing))
    }

    fun contentIntent(context: Context, route: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) }
        }
        return PendingIntent.getActivity(context, route.hashCode(), intent, PI_FLAGS)
    }

    fun broadcast(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, requestCode, intent, PI_FLAGS)

    fun base(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_ojas_notification)
            .setColor(0xFF8FB8FF.toInt())
            .setAutoCancel(true)

    fun post(context: Context, id: Int, notification: Notification) {
        if (!canPost(context)) return
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    fun canPost(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true
        }
}
