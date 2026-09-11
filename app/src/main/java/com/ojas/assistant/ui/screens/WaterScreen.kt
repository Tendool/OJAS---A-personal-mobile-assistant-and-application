package com.ojas.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.ui.components.EmptyState
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OjasScreen
import com.ojas.assistant.ui.components.OrbitIconButton
import com.ojas.assistant.ui.components.OrbitRing
import com.ojas.assistant.ui.components.SectionLabel
import com.ojas.assistant.ui.components.SparkBars
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.theme.NebulaBlue
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import com.ojas.assistant.ui.viewmodel.WaterViewModel

@Composable
fun WaterScreen(contentPadding: PaddingValues) {
    val container = LocalOjas.current
    val viewModel: WaterViewModel = viewModel(factory = WaterViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    OjasScreen(
        title = "Water",
        subtitle = if (state.remainingMl == 0) {
            "Goal reached. Nicely done."
        } else {
            "${state.remainingMl} ml to go today"
        },
        contentPadding = contentPadding,
        actions = {
            OrbitIconButton(
                icon = Icons.Rounded.Undo,
                contentDescription = "Undo last drink",
                onClick = viewModel::undoLast,
                tint = StarlightDim
            )
        }
    ) {
        item(key = "ring") {
            OjasCard(contentPadding = PaddingValues(vertical = 26.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    OrbitRing(
                        progress = state.progress,
                        accent = NebulaBlue,
                        strokeWidth = 14.dp,
                        modifier = Modifier.size(206.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${state.totalMl}",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "of ${state.settings.waterGoalMl} ml",
                                style = MaterialTheme.typography.bodyMedium,
                                color = StarlightDim
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${state.cupsLogged} / ${state.settings.cupsToGoal} cups",
                                style = MaterialTheme.typography.labelMedium,
                                color = StarlightFaint
                            )
                        }
                    }
                }
            }
        }

        item(key = "quick") {
            val amounts = remember(state.settings.waterCupMl) {
                listOf(100, state.settings.waterCupMl, state.settings.waterCupMl * 2, 500, 750)
                    .distinct()
                    .sorted()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                amounts.forEach { amount ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NebulaBlue.copy(alpha = 0.11f))
                            .border(1.dp, SpaceOutlineSoft, RoundedCornerShape(16.dp))
                            .clickable { viewModel.log(amount) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = NebulaBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "$amount",
                                style = MaterialTheme.typography.labelLarge,
                                color = NebulaBlue
                            )
                        }
                    }
                }
            }
        }

        item(key = "week-label") { SectionLabel("Last seven days") }

        item(key = "week") {
            OjasCard {
                SparkBars(
                    values = state.week.map { it.value.toFloat() / state.weekPeak.coerceAtLeast(1) },
                    labels = state.week.map { it.label },
                    accent = NebulaBlue
                )
                Spacer(Modifier.height(12.dp))
                val average = if (state.week.isEmpty()) 0 else state.week.sumOf { it.value } / state.week.size
                Text(
                    "Averaging $average ml a day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarlightDim
                )
            }
        }

        item(key = "settings-label") { SectionLabel("Targets") }

        item(key = "goal") {
            OjasCard {
                SliderRow(
                    label = "Daily goal",
                    valueText = "${state.settings.waterGoalMl} ml",
                    value = state.settings.waterGoalMl.toFloat(),
                    range = 500f..5000f,
                    steps = 44,
                    onChange = { viewModel.setGoal(it.toInt()) }
                )
                Spacer(Modifier.height(14.dp))
                SliderRow(
                    label = "Cup size",
                    valueText = "${state.settings.waterCupMl} ml",
                    value = state.settings.waterCupMl.toFloat(),
                    range = 100f..750f,
                    steps = 25,
                    onChange = { viewModel.setCup(it.toInt()) }
                )
            }
        }

        item(key = "reminders") {
            OjasCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Hydration nudges",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (state.settings.waterRemindersEnabled && state.nextNudgeAt > 0) {
                                "Next ${TimeUtils.relative(state.nextNudgeAt, use24h = state.settings.use24h)}"
                            } else {
                                "Off"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = StarlightFaint
                        )
                    }
                    Switch(
                        checked = state.settings.waterRemindersEnabled,
                        onCheckedChange = viewModel::setRemindersEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NebulaBlue,
                            checkedTrackColor = NebulaBlue.copy(alpha = 0.28f),
                            uncheckedThumbColor = StarlightFaint,
                            uncheckedTrackColor = Color(0x330A1224)
                        )
                    )
                }

                if (state.settings.waterRemindersEnabled) {
                    Spacer(Modifier.height(16.dp))
                    SliderRow(
                        label = "Every",
                        valueText = "${state.settings.waterIntervalMinutes} min",
                        value = state.settings.waterIntervalMinutes.toFloat(),
                        range = 30f..240f,
                        steps = 13,
                        onChange = { viewModel.setInterval(it.toInt()) }
                    )
                    Spacer(Modifier.height(14.dp))
                    SliderRow(
                        label = "From",
                        valueText = "${state.settings.waterWindowStartHour}:00",
                        value = state.settings.waterWindowStartHour.toFloat(),
                        range = 0f..12f,
                        steps = 11,
                        onChange = { viewModel.setWindow(it.toInt(), state.settings.waterWindowEndHour) }
                    )
                    Spacer(Modifier.height(14.dp))
                    SliderRow(
                        label = "Until",
                        valueText = "${state.settings.waterWindowEndHour}:00",
                        value = state.settings.waterWindowEndHour.toFloat(),
                        range = 13f..24f,
                        steps = 10,
                        onChange = { viewModel.setWindow(state.settings.waterWindowStartHour, it.toInt()) }
                    )
                }
            }
        }

        item(key = "log-label") { SectionLabel("Today", trailing = "${state.logs.size} entries") }

        if (state.logs.isEmpty()) {
            item(key = "log-empty") {
                OjasCard {
                    EmptyState(
                        title = "Nothing logged yet",
                        body = "Tap an amount above, or tell Ojas \"log 300 ml\"."
                    )
                }
            }
        } else {
            items(state.logs, key = { it.id }) { log ->
                OjasCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(NebulaBlue)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${log.amountMl} ml",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            TimeUtils.formatTime(log.loggedAt, state.settings.use24h),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StarlightFaint
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "remove",
                            style = MaterialTheme.typography.labelSmall,
                            color = StarlightFaint,
                            modifier = Modifier.clickable { viewModel.delete(log) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Slider rows commit on release rather than on every pixel, so dragging a goal does not
 * fire dozens of DataStore writes and reminder reschedules.
 */
@Composable
internal fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit
) {
    var live by remember(value) { mutableFloatStateOf(value) }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = StarlightDim)
            Text(
                if (live == value) valueText else formatLive(live, valueText),
                style = MaterialTheme.typography.labelLarge,
                color = NebulaBlue
            )
        }
        Slider(
            value = live,
            onValueChange = { live = it },
            onValueChangeFinished = { onChange(live) },
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = NebulaBlue,
                activeTrackColor = NebulaBlue.copy(alpha = 0.7f),
                inactiveTrackColor = Color(0x330A1224)
            )
        )
    }
}

private fun formatLive(live: Float, template: String): String {
    val suffix = template.dropWhile { it.isDigit() || it == ':' || it == '.' }
    return "${live.toInt()}$suffix"
}
