package com.ojas.assistant.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.entity.AlarmEntity
import com.ojas.assistant.di.ojas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns a ringing alarm: audio, vibration, the wake lock and the full-screen notification.
 *
 * It runs as a foreground service so the ring survives the app being swiped away and
 * cannot be killed by the usual background limits. Every path out of here goes through
 * [teardown], so audio focus, the wake lock and the vibrator are always released even
 * when the user dismisses from the lock screen.
 */
class AlarmRingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var alarmId: Long = 0L
    private var ringingSince: Long = 0L
    private var currentVolume = START_VOLUME

    private val autoStop = Runnable {
        Log.i(TAG, "alarm $alarmId rang out after ${MAX_RING_MS / 60_000} minutes")
        stopSelf()
    }

    private val fadeIn = object : Runnable {
        override fun run() {
            currentVolume = (currentVolume + VOLUME_STEP).coerceAtMost(1f)
            runCatching { player?.setVolume(currentVolume, currentVolume) }
            if (currentVolume < 1f) handler.postDelayed(this, FADE_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val id = intent?.getLongExtra(EXTRA_ALARM_ID, 0L) ?: 0L
        // A second alarm arriving while one is already ringing simply keeps the first.
        if (ringingSince != 0L) return START_NOT_STICKY

        alarmId = id
        ringingSince = TimeUtils.now()

        // Must happen within a few seconds of the start request, before any disk I/O.
        ServiceCompat.startForeground(
            this,
            NotificationCenter.ID_RINGING,
            buildNotification(label = "Alarm", snoozeMinutes = DEFAULT_SNOOZE),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )

        acquireWakeLock()
        handler.postDelayed(autoStop, MAX_RING_MS)

        scope.launch {
            val alarm = withContext(Dispatchers.IO) { ojas.alarms.byId(alarmId) }
            startRinging(alarm)
            ServiceCompat.startForeground(
                this@AlarmRingService,
                NotificationCenter.ID_RINGING,
                buildNotification(
                    label = alarm?.label?.takeIf { it.isNotBlank() } ?: "Alarm",
                    snoozeMinutes = alarm?.snoozeMinutes ?: DEFAULT_SNOOZE
                ),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
            )
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Deliberately keep ringing: swiping the app away must not silence an alarm.
        super.onTaskRemoved(rootIntent)
    }

    // ------------------------------------------------------------------ ringing

    private fun startRinging(alarm: AlarmEntity?) {
        val gentle = alarm?.gentleWake ?: true
        currentVolume = if (gentle) START_VOLUME else 1f
        val soundUri = alarm?.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        if (soundUri != null) {
            runCatching {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@AlarmRingService, soundUri)
                    isLooping = true
                    setWakeMode(this@AlarmRingService, PowerManager.PARTIAL_WAKE_LOCK)
                    setVolume(currentVolume, currentVolume)
                    prepare()
                    start()
                }
            }.onFailure { Log.e(TAG, "could not start alarm audio", it) }

            if (gentle) handler.postDelayed(fadeIn, FADE_INTERVAL_MS)
        }

        if (alarm?.vibrate != false) startVibration()
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        runCatching {
            v.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0), attributes)
        }.onFailure { Log.w(TAG, "vibration unavailable", it) }
    }

    private fun acquireWakeLock() {
        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ojas:ringing")
            ?.apply {
                setReferenceCounted(false)
                acquire(MAX_RING_MS + 30_000L)
            }
    }

    private fun teardown() {
        handler.removeCallbacks(autoStop)
        handler.removeCallbacks(fadeIn)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        wakeLock = null
        ringingSince = 0L
    }

    // ------------------------------------------------------------ notification

    private fun buildNotification(label: String, snoozeMinutes: Int): android.app.Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            alarmId.toInt(),
            Intent(this, AlarmRingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_ALARM_ID, alarmId)
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_SNOOZE, snoozeMinutes)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snooze = NotificationCenter.broadcast(
            this,
            900_000 + alarmId.toInt(),
            Intent(this, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_SNOOZE_ALARM
                putExtra(AlarmReceiver.EXTRA_ID, alarmId)
                putExtra(AlarmActionReceiver.EXTRA_MINUTES, snoozeMinutes)
            }
        )
        val dismiss = NotificationCenter.broadcast(
            this,
            910_000 + alarmId.toInt(),
            Intent(this, AlarmActionReceiver::class.java).apply {
                action = AlarmActionReceiver.ACTION_DISMISS_ALARM
                putExtra(AlarmReceiver.EXTRA_ID, alarmId)
            }
        )

        return NotificationCenter.base(this, NotificationCenter.CH_ALARM)
            .setContentTitle(label)
            .setContentText(TimeUtils.formatTime(TimeUtils.now(), false))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Snooze ${snoozeMinutes}m", snooze)
            .addAction(0, "Dismiss", dismiss)
            .build()
    }

    companion object {
        private const val TAG = "OjasAlarmRing"
        const val EXTRA_ALARM_ID = "ojas.alarm.id"
        const val EXTRA_LABEL = "ojas.alarm.label"
        const val EXTRA_SNOOZE = "ojas.alarm.snooze"
        const val ACTION_STOP = "com.ojas.assistant.action.STOP_RINGING"

        private const val DEFAULT_SNOOZE = 9
        private const val MAX_RING_MS = 10 * 60_000L
        private const val START_VOLUME = 0.06f
        private const val VOLUME_STEP = 0.035f
        private const val FADE_INTERVAL_MS = 900L
        private val VIBRATION_PATTERN = longArrayOf(0, 700, 600, 700, 1400)

        fun start(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmRingService::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.e(TAG, "could not start ringing service", it) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmRingService::class.java).setAction(ACTION_STOP)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        /** True when the alarm stream is muted, which would make the alarm silent. */
        fun alarmVolumeMuted(context: Context): Boolean {
            val am = context.getSystemService<AudioManager>() ?: return false
            return am.getStreamVolume(AudioManager.STREAM_ALARM) == 0
        }
    }
}
