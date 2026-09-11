package com.ojas.assistant.screentime

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ojas.assistant.alarm.NotificationCenter
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.di.ojas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches which app is in the foreground and enforces the user's own daily budgets.
 *
 * Battery discipline is the whole design here:
 *  - the polling loop only runs while the screen is on, torn down on ACTION_SCREEN_OFF;
 *  - the interval widens automatically when nothing is close to its limit, so an idle
 *    day costs a handful of wakeups rather than one every few seconds;
 *  - usage figures come from the OS, so there is nothing to accumulate or persist here.
 */
class ScreenTimeMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null

    /** Package to (epoch day) for warnings already shown, so each fires once a day. */
    private val warned = HashMap<String, Long>()

    private var focusUntil: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startPolling()
                Intent.ACTION_SCREEN_OFF -> stopPolling()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NotificationCenter.ID_SCREEN_TIME_SERVICE,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_FOCUS -> {
                val minutes = intent.getIntExtra(EXTRA_FOCUS_MINUTES, 25).coerceIn(1, 480)
                focusUntil = TimeUtils.now() + minutes * 60_000L
                startPolling()
            }
            ACTION_END_FOCUS -> focusUntil = 0L
        }
        // Restarted by the system if it is ever killed; limits should keep applying.
        return START_STICKY
    }

    override fun onDestroy() {
        stopPolling()
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    // ----------------------------------------------------------------- polling

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                val interval = runCatching { checkOnce() }
                    .onFailure { Log.w(TAG, "screen time check failed", it) }
                    .getOrDefault(IDLE_INTERVAL_MS)
                delay(interval)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Runs one enforcement pass and returns how long to wait before the next one. */
    private suspend fun checkOnce(): Long {
        val container = ojas
        val repo = container.screenTime
        if (!repo.hasUsageAccess()) return IDLE_INTERVAL_MS

        val limits = repo.activeLimits()
        if (limits.isEmpty() && focusUntil <= TimeUtils.now()) return IDLE_INTERVAL_MS

        val foreground = repo.foregroundPackage() ?: return IDLE_INTERVAL_MS
        if (foreground == packageName) return IDLE_INTERVAL_MS

        val inFocus = focusUntil > TimeUtils.now()
        val limit = limits.firstOrNull { it.packageName == foreground }

        if (inFocus && limit != null) {
            block(foreground, limit.appLabel, reason = BlockOverlayActivity.REASON_FOCUS, used = 0, cap = 0)
            return ACTIVE_INTERVAL_MS
        }

        if (limit == null) return IDLE_INTERVAL_MS

        val used = repo.minutesUsedToday(foreground)
        val remaining = limit.dailyLimitMinutes - used

        return when {
            remaining <= 0 -> {
                if (limit.hardBlock && canOverlay()) {
                    block(
                        foreground,
                        limit.appLabel,
                        BlockOverlayActivity.REASON_LIMIT,
                        used,
                        limit.dailyLimitMinutes
                    )
                } else {
                    warnOnce(foreground, limit.appLabel, used, limit.dailyLimitMinutes)
                }
                ACTIVE_INTERVAL_MS
            }
            // Close to the cap: check often enough to catch the crossing promptly.
            remaining <= 5 -> ACTIVE_INTERVAL_MS
            remaining <= 20 -> NEAR_INTERVAL_MS
            else -> IDLE_INTERVAL_MS
        }
    }

    private fun warnOnce(pkg: String, label: String, used: Int, cap: Int) {
        val today = TimeUtils.todayEpochDay()
        if (warned[pkg] == today) return
        warned[pkg] = today

        val notification = NotificationCenter.base(this, NotificationCenter.CH_FOCUS)
            .setContentTitle("$label is over its budget")
            .setContentText("$used of $cap minutes used today.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(NotificationCenter.contentIntent(this, "screentime"))
            .build()
        NotificationCenter.post(
            this,
            NotificationCenter.ID_LIMIT_WARNING_BASE + (pkg.hashCode() and 0xFF),
            notification
        )
    }

    private fun block(pkg: String, label: String, reason: String, used: Int, cap: Int) {
        if (!canOverlay()) {
            warnOnce(pkg, label, used, cap)
            return
        }
        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(BlockOverlayActivity.EXTRA_LABEL, label)
            putExtra(BlockOverlayActivity.EXTRA_REASON, reason)
            putExtra(BlockOverlayActivity.EXTRA_USED, used)
            putExtra(BlockOverlayActivity.EXTRA_CAP, cap)
            putExtra(BlockOverlayActivity.EXTRA_UNTIL, focusUntil)
        }
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "could not show block screen", it) }
    }

    private fun canOverlay(): Boolean = Settings.canDrawOverlays(this)

    private fun buildNotification() =
        NotificationCenter.base(this, NotificationCenter.CH_ONGOING)
            .setContentTitle("Ojas is watching your limits")
            .setContentText("Only while the screen is on.")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(NotificationCenter.contentIntent(this, "screentime"))
            .build()

    companion object {
        private const val TAG = "OjasScreenTime"

        const val ACTION_STOP = "com.ojas.assistant.action.STOP_MONITOR"
        const val ACTION_START_FOCUS = "com.ojas.assistant.action.START_FOCUS"
        const val ACTION_END_FOCUS = "com.ojas.assistant.action.END_FOCUS"
        const val EXTRA_FOCUS_MINUTES = "ojas.extra.focus_minutes"

        private const val ACTIVE_INTERVAL_MS = 15_000L
        private const val NEAR_INTERVAL_MS = 60_000L
        private const val IDLE_INTERVAL_MS = 180_000L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ScreenTimeMonitorService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenTimeMonitorService::class.java))
        }

        fun startFocus(context: Context, minutes: Int) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ScreenTimeMonitorService::class.java)
                        .setAction(ACTION_START_FOCUS)
                        .putExtra(EXTRA_FOCUS_MINUTES, minutes)
                )
            }
        }

        fun endFocus(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ScreenTimeMonitorService::class.java).setAction(ACTION_END_FOCUS)
                )
            }
        }
    }
}
