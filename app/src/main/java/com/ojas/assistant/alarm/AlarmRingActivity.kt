package com.ojas.assistant.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.di.ojas
import com.ojas.assistant.ui.galaxy.GalaxyBackground
import com.ojas.assistant.ui.theme.EmberOrange
import com.ojas.assistant.ui.theme.NebulaBlue
import com.ojas.assistant.ui.theme.OjasTheme
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * The full-screen alarm.
 *
 * Launched by the ringing service's full-screen intent, so it comes up over the lock
 * screen with the display already turned on. It owns no audio and no timers: every
 * button here just tells [AlarmRingService] what to do, which keeps the ring alive even
 * if this window is destroyed.
 */
class AlarmRingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        showOverLockScreen()

        val alarmId = intent?.getLongExtra(AlarmRingService.EXTRA_ALARM_ID, 0L) ?: 0L
        val label = intent?.getStringExtra(AlarmRingService.EXTRA_LABEL).orEmpty()
        val snoozeMinutes = intent?.getIntExtra(AlarmRingService.EXTRA_SNOOZE, 9) ?: 9
        val container = ojas

        setContent {
            OjasTheme {
                val use24h by produceState(initialValue = false) {
                    value = container.settings.settings.first().use24h
                }
                // Re-reads the clock once a second so the displayed time stays honest
                // while the alarm keeps ringing.
                val now by produceState(initialValue = TimeUtils.now()) {
                    while (true) {
                        value = TimeUtils.now()
                        delay(1_000L)
                    }
                }

                RingingSurface(
                    label = label.ifBlank { "Alarm" },
                    clock = TimeUtils.formatTime(now, use24h),
                    date = TimeUtils.formatFullDay(TimeUtils.epochDayOf(now)),
                    snoozeMinutes = snoozeMinutes,
                    onSnooze = {
                        sendAction(AlarmActionReceiver.ACTION_SNOOZE_ALARM, alarmId, snoozeMinutes)
                        finishAndRemoveTask()
                    },
                    onDismiss = {
                        sendAction(AlarmActionReceiver.ACTION_DISMISS_ALARM, alarmId, snoozeMinutes)
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }

    /** Back must not silence an alarm; the only ways out are snooze and dismiss. */
    override fun onBackPressed() = Unit

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService<KeyguardManager>()?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun sendAction(action: String, alarmId: Long, minutes: Int) {
        sendBroadcast(
            Intent(this, AlarmActionReceiver::class.java).apply {
                this.action = action
                putExtra(AlarmReceiver.EXTRA_ID, alarmId)
                putExtra(AlarmActionReceiver.EXTRA_MINUTES, minutes)
            }
        )
    }
}

@Composable
private fun RingingSurface(
    label: String,
    clock: String,
    date: String,
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "alarm-pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "alarm-glow"
    )

    Box(Modifier.fillMaxSize()) {
        // A slow, low-density galaxy: this screen fires at 3am and should not be loud.
        GalaxyBackground(
            modifier = Modifier.fillMaxSize(),
            starCount = 2500,
            reduceMotion = false,
            interactive = false
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(126.dp)
                        .scale(0.9f + glow * 0.25f)
                        .alpha(glow * 0.5f)
                        .clip(CircleShape)
                        .background(EmberOrange.copy(alpha = 0.16f))
                )
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    color = EmberOrange,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                clock,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(date, style = MaterialTheme.typography.bodyLarge, color = StarlightDim)

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RingAction(
                    text = "Snooze ${snoozeMinutes}m",
                    accent = NebulaBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onSnooze
                )
                RingAction(
                    text = "Dismiss",
                    accent = EmberOrange,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Ojas will keep ringing for ten minutes",
                style = MaterialTheme.typography.labelSmall,
                color = StarlightFaint
            )
        }
    }
}

@Composable
private fun RingAction(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(accent.copy(alpha = 0.18f))
            .border(1.dp, SpaceOutlineSoft, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = accent)
    }
}
