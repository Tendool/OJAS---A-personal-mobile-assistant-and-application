package com.ojas.assistant.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.screentime.AppUsage
import com.ojas.assistant.ui.components.EmptyState
import com.ojas.assistant.ui.components.LinearMeter
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OjasScreen
import com.ojas.assistant.ui.components.OrbitButton
import com.ojas.assistant.ui.components.OrbitIconButton
import com.ojas.assistant.ui.components.OrbitRing
import com.ojas.assistant.ui.components.SectionLabel
import com.ojas.assistant.ui.components.SparkBars
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.theme.AuroraTeal
import com.ojas.assistant.ui.theme.EmberOrange
import com.ojas.assistant.ui.theme.PulsarRose
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint

@Composable
fun ScreenTimeScreen(contentPadding: PaddingValues) {
    val container = LocalOjas.current
    val context = LocalContext.current
    val viewModel: com.ojas.assistant.ui.viewmodel.ScreenTimeViewModel =
        viewModel(factory = com.ojas.assistant.ui.viewmodel.ScreenTimeViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    var limitTarget by remember { mutableStateOf<AppUsage?>(null) }
    var pickingApp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    KeepScreenOn(active = state.focus.running)

    OjasScreen(
        title = "Focus",
        subtitle = if (state.hasAccess) {
            "${TimeUtils.humanDuration(state.totalMinutes * 60)} on screen today"
        } else {
            "Usage access needed"
        },
        contentPadding = contentPadding,
        actions = {
            if (state.hasAccess) {
                OrbitIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add an app limit",
                    onClick = {
                        viewModel.loadInstalledApps()
                        pickingApp = true
                    },
                    tint = AuroraTeal
                )
            }
        }
    ) {
        if (!state.hasAccess) {
            item(key = "grant") {
                OjasCard {
                    Text(
                        "Ojas needs usage access",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Screen time is read from Android's own usage statistics. Nothing is " +
                            "uploaded, and the figures never leave this device.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = StarlightDim
                    )
                    Spacer(Modifier.height(18.dp))
                    OrbitButton(
                        text = "Open usage access settings",
                        onClick = { context.startActivity(viewModel.usageAccessIntent()) },
                        accent = AuroraTeal,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            item(key = "today") {
                OjasCard(contentPadding = PaddingValues(vertical = 24.dp)) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OrbitRing(
                            progress = state.goalProgress,
                            accent = if (state.goalProgress > 1f) PulsarRose else AuroraTeal,
                            strokeWidth = 12.dp,
                            modifier = Modifier.size(190.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    TimeUtils.humanDuration(state.totalMinutes * 60),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Light,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "of ${TimeUtils.humanDuration(state.settings.screenTimeGoalMinutes * 60)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = StarlightDim
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    SliderRow(
                        label = "Daily budget",
                        valueText = "${state.settings.screenTimeGoalMinutes} min",
                        value = state.settings.screenTimeGoalMinutes.toFloat(),
                        range = 30f..720f,
                        steps = 22,
                        onChange = { viewModel.setDailyGoal(it.toInt()) }
                    )
                }
            }

            item(key = "focus") {
                OjasCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (state.focus.running) "Focus running" else "Focus session",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (state.focus.running) {
                                    "Limited apps are blocked until the timer ends."
                                } else {
                                    "${state.focusMinutesToday} focused minutes today"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = StarlightDim
                            )
                        }
                        if (state.focus.running) {
                            Text(
                                TimeUtils.clockDigits(state.focus.secondsLeft),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Light,
                                color = AuroraTeal
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (state.focus.running) {
                        LinearMeter(progress = state.focus.progress, accent = AuroraTeal)
                        Spacer(Modifier.height(14.dp))
                        OrbitButton(
                            text = "End focus",
                            onClick = { viewModel.stopFocus(context) },
                            accent = PulsarRose,
                            filled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        var minutes by remember { mutableIntStateOf(state.settings.focusDefaultMinutes) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(15, 25, 45, 60, 90).forEach { option ->
                                ChoiceChip(
                                    text = "${option}m",
                                    selected = minutes == option,
                                    accent = AuroraTeal,
                                    modifier = Modifier.weight(1f)
                                ) { minutes = option }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OrbitButton(
                            text = "Start focus",
                            onClick = { viewModel.startFocus(context, minutes) },
                            accent = AuroraTeal,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item(key = "week-label") { SectionLabel("Last seven days") }

            item(key = "week") {
                val peak = (state.week.maxOfOrNull { it.value } ?: 0)
                    .coerceAtLeast(state.settings.screenTimeGoalMinutes)
                OjasCard {
                    SparkBars(
                        values = state.week.map { it.value.toFloat() / peak.coerceAtLeast(1) },
                        labels = state.week.map { it.label },
                        accent = AuroraTeal
                    )
                }
            }

            item(key = "enforcement") {
                OjasCard {
                    ToggleRow(
                        label = "Enforce limits in the background",
                        checked = state.settings.screenTimeEnabled
                    ) { viewModel.setMonitoring(context, it) }
                    Text(
                        "Ojas only checks while the screen is on, and widens the interval when " +
                            "nothing is near its cap.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StarlightFaint
                    )
                }
            }

            item(key = "apps-label") {
                SectionLabel("Today by app", trailing = "${state.usage.size}")
            }

            if (state.usage.isEmpty()) {
                item(key = "apps-empty") {
                    OjasCard {
                        EmptyState(
                            title = if (state.loading) "Reading usage…" else "Nothing yet today",
                            body = "Apps appear here once they have a minute of foreground time."
                        )
                    }
                }
            } else {
                items(state.usage, key = { it.packageName }) { app ->
                    val limit = state.limitFor(app.packageName)
                    OjasCard(
                        contentPadding = PaddingValues(16.dp),
                        onClick = { limitTarget = app }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    app.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (limit != null) {
                                    Text(
                                        "Limit ${limit.dailyLimitMinutes} min" +
                                            if (limit.hardBlock) "  ·  blocks" else "  ·  warns",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (app.minutes >= limit.dailyLimitMinutes) {
                                            PulsarRose
                                        } else {
                                            StarlightFaint
                                        }
                                    )
                                }
                            }
                            Text(
                                TimeUtils.humanDuration(app.minutes * 60),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (limit != null && app.minutes >= limit.dailyLimitMinutes) {
                                    PulsarRose
                                } else {
                                    AuroraTeal
                                }
                            )
                        }
                        if (limit != null) {
                            Spacer(Modifier.height(10.dp))
                            LinearMeter(
                                progress = app.minutes.toFloat() / limit.dailyLimitMinutes.coerceAtLeast(1),
                                accent = if (app.minutes >= limit.dailyLimitMinutes) PulsarRose else EmberOrange
                            )
                        }
                    }
                }
            }
        }
    }

    limitTarget?.let { app ->
        LimitEditor(
            app = app,
            existingMinutes = state.limitFor(app.packageName)?.dailyLimitMinutes,
            existingHardBlock = state.limitFor(app.packageName)?.hardBlock ?: false,
            onDismiss = { limitTarget = null },
            onSave = { minutes, hard ->
                viewModel.setLimit(app.packageName, minutes, hard)
                limitTarget = null
            },
            onRemove = if (state.limitFor(app.packageName) == null) null else {
                {
                    viewModel.removeLimit(app.packageName)
                    limitTarget = null
                }
            }
        )
    }

    if (pickingApp) {
        AppPicker(
            apps = state.installedApps,
            onDismiss = { pickingApp = false },
            onPick = {
                pickingApp = false
                limitTarget = it
            }
        )
    }
}

@Composable
private fun LimitEditor(
    app: AppUsage,
    existingMinutes: Int?,
    existingHardBlock: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, Boolean) -> Unit,
    onRemove: (() -> Unit)?
) {
    var minutes by remember(app.packageName) { mutableIntStateOf(existingMinutes ?: 30) }
    var hardBlock by remember(app.packageName) { mutableStateOf(existingHardBlock) }

    EditorDialog(
        title = app.label,
        onDismiss = onDismiss,
        onDelete = onRemove,
        onSave = { onSave(minutes, hardBlock) }
    ) {
        Text(
            "Daily budget",
            style = MaterialTheme.typography.bodyLarge,
            color = StarlightDim
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 30, 45, 60).forEach { option ->
                ChoiceChip(
                    text = "${option}m",
                    selected = minutes == option,
                    accent = AuroraTeal,
                    modifier = Modifier.weight(1f)
                ) { minutes = option }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(90, 120, 180, 240).forEach { option ->
                ChoiceChip(
                    text = "${option / 60}h",
                    selected = minutes == option,
                    accent = AuroraTeal,
                    modifier = Modifier.weight(1f)
                ) { minutes = option }
            }
        }
        Spacer(Modifier.height(16.dp))
        ToggleRow("Put a block screen in front of it", hardBlock) { hardBlock = it }
        Text(
            "Blocking needs the display-over-other-apps permission. Without it Ojas sends a " +
                "notification instead.",
            style = MaterialTheme.typography.bodyMedium,
            color = StarlightFaint
        )
    }
}

@Composable
private fun AppPicker(
    apps: List<AppUsage>,
    onDismiss: () -> Unit,
    onPick: (AppUsage) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF2080E1C),
        title = {
            Text(
                "Choose an app",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            if (apps.isEmpty()) {
                Text(
                    "Reading the app list…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = StarlightDim
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = StarlightFaint)
            }
        }
    )
}
