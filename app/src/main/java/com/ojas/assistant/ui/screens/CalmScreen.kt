package com.ojas.assistant.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OjasScreen
import com.ojas.assistant.ui.components.SectionLabel
import com.ojas.assistant.ui.components.SparkBars
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import com.ojas.assistant.ui.theme.VioletDrift
import com.ojas.assistant.ui.viewmodel.BreathPresets
import com.ojas.assistant.ui.viewmodel.MeditationViewModel

@Composable
fun CalmScreen(contentPadding: PaddingValues) {
    val container = LocalOjas.current
    val viewModel: MeditationViewModel = viewModel(factory = MeditationViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    KeepScreenOn(active = state.running)

    OjasScreen(
        title = "Calm",
        subtitle = when {
            state.streak > 1 -> "${state.streak} days in a row"
            state.todaySeconds > 0 -> "${state.todaySeconds / 60} minutes today"
            else -> "Find the rhythm"
        },
        contentPadding = contentPadding
    ) {
        item(key = "orb") {
            OjasCard(contentPadding = PaddingValues(vertical = 28.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(268.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BreathOrb(
                        expanded = state.phase.expand,
                        phaseSeconds = state.phase.seconds,
                        phaseKey = state.phaseIndex,
                        running = state.running
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            state.phase.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = VioletDrift
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            TimeUtils.clockDigits(state.remainingSeconds),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Light,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            state.preset.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StarlightFaint
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.elapsedSeconds > 0) {
                        RoundControl(
                            size = 52,
                            accent = StarlightDim,
                            description = "End sit"
                        ) { viewModel.stop() }
                        Spacer(Modifier.size(18.dp))
                    }
                    RoundControl(
                        size = 76,
                        accent = VioletDrift,
                        description = if (state.running) "Pause" else "Begin",
                        icon = if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow
                    ) { viewModel.toggle() }
                }

                if (state.finished) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Sit complete. ${TimeUtils.humanDuration(state.elapsedSeconds)} logged.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VioletDrift,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.dismissFinished() },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item(key = "length") {
            OjasCard {
                SectionLabel("Length")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 10, 15, 20, 30).forEach { minutes ->
                        val selected = state.plannedMinutes == minutes
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (selected) VioletDrift.copy(alpha = 0.2f) else Color(0x330A1224)
                                )
                                .border(
                                    1.dp,
                                    if (selected) VioletDrift.copy(alpha = 0.6f) else SpaceOutlineSoft,
                                    RoundedCornerShape(13.dp)
                                )
                                .clickable(enabled = !state.running) { viewModel.setMinutes(minutes) }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$minutes",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) VioletDrift else StarlightDim
                            )
                        }
                    }
                }
            }
        }

        item(key = "presets-label") { SectionLabel("Rhythm") }

        items(BreathPresets.all, key = { it.key }) { preset ->
            val selected = preset.key == state.preset.key
            OjasCard(
                contentPadding = PaddingValues(16.dp),
                borderColor = if (selected) VioletDrift.copy(alpha = 0.55f) else SpaceOutlineSoft,
                onClick = { viewModel.selectPreset(preset) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            preset.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) VioletDrift else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            preset.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StarlightFaint
                        )
                    }
                    Text(
                        preset.phases.joinToString("-") { "${it.seconds}" },
                        style = MaterialTheme.typography.labelMedium,
                        color = StarlightDim
                    )
                }
            }
        }

        item(key = "week-label") { SectionLabel("Last seven days") }

        item(key = "week") {
            val peak = (state.week.maxOfOrNull { it.value } ?: 0).coerceAtLeast(10)
            OjasCard {
                SparkBars(
                    values = state.week.map { it.value.toFloat() / peak },
                    labels = state.week.map { it.label },
                    accent = VioletDrift
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${state.lifetimeMinutes} minutes of stillness all time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarlightDim
                )
            }
        }

        if (state.recent.isNotEmpty()) {
            item(key = "history-label") { SectionLabel("Recent sits") }
            items(state.recent, key = { it.id }) { session ->
                OjasCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.presetName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                TimeUtils.formatDay(session.startedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = StarlightFaint
                            )
                        }
                        Text(
                            TimeUtils.humanDuration(session.actualSeconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (session.completed) VioletDrift else StarlightDim
                        )
                    }
                }
            }
        }
    }
}

/**
 * The pacer. It grows over the inhale, holds its size through a hold, and shrinks on the
 * exhale, animating for exactly the length of the current phase so the visual and the
 * count never drift apart.
 */
@Composable
private fun BreathOrb(
    expanded: Boolean?,
    phaseSeconds: Int,
    phaseKey: Int,
    running: Boolean
) {
    val target = when {
        !running -> 0.62f
        expanded == true -> 1f
        expanded == false -> 0.45f
        else -> null
    }

    val scale by animateFloatAsState(
        targetValue = target ?: 1f,
        animationSpec = tween(
            durationMillis = if (target == null) 60 else (phaseSeconds * 1000).coerceIn(300, 20_000),
            easing = LinearEasing
        ),
        label = "breath-$phaseKey"
    )

    Canvas(Modifier.fillMaxSize()) {
        val maxRadius = minOf(size.width, size.height) / 2f
        val radius = maxRadius * scale.coerceIn(0.3f, 1f)
        val centre = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.0f to VioletDrift.copy(alpha = 0.20f),
                    0.68f to VioletDrift.copy(alpha = 0.09f),
                    1.0f to Color.Transparent
                ),
                center = centre,
                radius = radius
            ),
            radius = radius,
            center = centre
        )
        drawCircle(
            color = VioletDrift.copy(alpha = 0.55f),
            radius = radius,
            center = centre,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f * density)
        )
    }
}

@Composable
private fun RoundControl(
    size: Int,
    accent: Color,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Rounded.Stop,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, SpaceOutlineSoft, RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = accent,
            modifier = Modifier.size((size * 0.4f).dp)
        )
    }
}
