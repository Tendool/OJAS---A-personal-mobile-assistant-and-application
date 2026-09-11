package com.ojas.assistant.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.DisposableEffect
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OjasScreen
import com.ojas.assistant.ui.components.SectionLabel
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.theme.AuroraTeal
import com.ojas.assistant.ui.theme.NebulaBlue
import com.ojas.assistant.ui.theme.PulsarRose
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import com.ojas.assistant.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(contentPadding: PaddingValues) {
    val container = LocalOjas.current
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Any of these can be revoked while the user is away in system settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var name by remember(state.settings.userName) { mutableStateOf(state.settings.userName) }

    OjasScreen(
        title = "Settings",
        subtitle = "Everything here stays on this device",
        contentPadding = contentPadding
    ) {
        item(key = "identity") {
            OjasCard {
                FieldLabel("What should Ojas call you")
                PlainField(
                    value = name,
                    onChange = {
                        name = it
                        viewModel.setName(it)
                    },
                    placeholder = "Your name"
                )
                Spacer(Modifier.height(14.dp))
                ToggleRow("24-hour clock", state.settings.use24h, viewModel::setUse24h)
            }
        }

        item(key = "galaxy-label") { SectionLabel("The galaxy") }

        item(key = "galaxy") {
            OjasCard {
                SliderRow(
                    label = "Star density",
                    valueText = "${state.settings.starCount} stars",
                    value = state.settings.starCount.toFloat(),
                    range = 1500f..12000f,
                    steps = 20,
                    onChange = { viewModel.setStarCount(it.toInt()) }
                )
                Spacer(Modifier.height(10.dp))
                ToggleRow(
                    "Turn with a finger",
                    state.settings.galaxyInteractive,
                    viewModel::setGalaxyInteractive
                )
                ToggleRow(
                    "Reduce motion",
                    state.settings.reduceMotion,
                    viewModel::setReduceMotion
                )
                Text(
                    "Reduced motion parks the galaxy and stops redrawing it entirely, which is " +
                        "the single biggest battery saving available in Ojas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarlightFaint
                )
            }
        }

        item(key = "permissions-label") { SectionLabel("Permissions") }

        item(key = "permissions") {
            OjasCard {
                PermissionRow(
                    title = "Exact alarms",
                    detail = "Lets alarms fire to the second, even in Doze.",
                    granted = state.permissions.exactAlarms,
                    onFix = { viewModel.exactAlarmSettingsIntent(context)?.let { context.launch(it) } }
                )
                PermissionRow(
                    title = "Notifications",
                    detail = "Required for reminders and alarm alerts.",
                    granted = state.permissions.notifications,
                    onFix = { context.launch(viewModel.notificationSettingsIntent(context)) }
                )
                PermissionRow(
                    title = "Unrestricted battery",
                    detail = "Stops the system deferring Ojas overnight.",
                    granted = state.permissions.batteryUnrestricted,
                    onFix = { context.launch(viewModel.batteryIntent(context)) }
                )
                PermissionRow(
                    title = "Usage access",
                    detail = "Needed to read screen time.",
                    granted = state.permissions.usageAccess,
                    onFix = { context.launch(viewModel.usageAccessIntent()) }
                )
                PermissionRow(
                    title = "Display over other apps",
                    detail = "Only needed if you want hard app blocking.",
                    granted = state.permissions.overlay,
                    onFix = { context.launch(viewModel.overlayIntent(context)) },
                    optional = true
                )
            }
        }

        item(key = "sessions-label") { SectionLabel("Sessions") }

        item(key = "sessions") {
            OjasCard {
                ToggleRow(
                    "Chime at the end of a sit",
                    state.settings.meditationBell,
                    viewModel::setMeditationBell
                )
                ToggleRow(
                    "Cue the end of a rest period",
                    state.settings.workoutRestCue,
                    viewModel::setRestCue
                )
                ToggleRow(
                    "Haptics",
                    state.settings.hapticsEnabled,
                    viewModel::setHaptics
                )
            }
        }

        item(key = "about") {
            OjasCard {
                Text(
                    "Ojas",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Version ${com.ojas.assistant.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarlightDim
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Ojas holds no internet permission at all. Your logs, alarms, calendar and " +
                        "usage figures live in a database on this phone and nowhere else.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarlightFaint
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    detail: String,
    granted: Boolean,
    onFix: () -> Unit,
    optional: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onFix)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        granted -> AuroraTeal
                        optional -> StarlightFaint
                        else -> PulsarRose
                    }
                )
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = StarlightFaint)
        }
        if (!granted) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(NebulaBlue.copy(alpha = 0.12f))
                    .border(1.dp, SpaceOutlineSoft, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text("Grant", style = MaterialTheme.typography.labelMedium, color = NebulaBlue)
            }
        }
    }
}

/** Settings intents can be missing on heavily customised ROMs; failing silently beats crashing. */
private fun android.content.Context.launch(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
