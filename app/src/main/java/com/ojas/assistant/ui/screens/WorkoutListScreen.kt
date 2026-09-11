package com.ojas.assistant.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.ui.components.EmptyState
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OjasScreen
import com.ojas.assistant.ui.components.OrbitIconButton
import com.ojas.assistant.ui.components.SectionLabel
import com.ojas.assistant.ui.components.SparkBars
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.theme.EmberOrange
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import com.ojas.assistant.ui.theme.accentFor
import com.ojas.assistant.ui.viewmodel.WorkoutListViewModel

@Composable
fun WorkoutListScreen(
    contentPadding: PaddingValues,
    onStart: (Long) -> Unit,
    onEdit: (Long) -> Unit
) {
    val container = LocalOjas.current
    val viewModel: WorkoutListViewModel = viewModel(factory = WorkoutListViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    OjasScreen(
        title = "Move",
        subtitle = when {
            state.streak > 1 -> "${state.streak} day streak"
            state.streak == 1 -> "Streak started today"
            else -> "Pick a routine and begin"
        },
        contentPadding = contentPadding,
        actions = {
            OrbitIconButton(
                icon = Icons.Rounded.Add,
                contentDescription = "New routine",
                onClick = { onEdit(0L) },
                tint = EmberOrange
            )
        }
    ) {
        item(key = "week") {
            val peak = (state.week.maxOfOrNull { it.value } ?: 0).coerceAtLeast(30)
            OjasCard {
                SparkBars(
                    values = state.week.map { it.value.toFloat() / peak },
                    labels = state.week.map { it.label },
                    accent = EmberOrange
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${state.week.sumOf { it.value }} active minutes this week",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarlightDim
                )
            }
        }

        item(key = "routines-label") {
            SectionLabel("Routines", trailing = "${state.workouts.size}")
        }

        if (state.workouts.isEmpty()) {
            item(key = "routines-empty") {
                OjasCard {
                    EmptyState(
                        title = "No routines",
                        body = "Build one with the plus button. Ojas ships a few starters, so this is unusual."
                    )
                }
            }
        } else {
            items(state.workouts, key = { it.workout.id }) { item ->
                val accent = accentFor(item.workout.accentKey)
                OjasCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.workout.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                item.workout.focus,
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                        }
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.16f))
                                .clickable { onStart(item.workout.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Start ${item.workout.name}",
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Meta("${item.exercises.size}", "moves")
                        Meta("${item.totalSets}", "sets")
                        Meta("~${item.estimatedMinutes}", "min")
                        Spacer(Modifier.weight(1f))
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit ${item.workout.name}",
                            tint = StarlightFaint,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onEdit(item.workout.id) }
                        )
                    }
                }
            }
        }

        if (state.recent.isNotEmpty()) {
            item(key = "history-label") { SectionLabel("Recent sessions") }

            items(state.recent, key = { it.id }) { session ->
                OjasCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                session.workoutName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${session.completedSets} of ${session.totalSets} sets  ·  " +
                                    TimeUtils.humanDuration(session.activeSeconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = StarlightFaint
                            )
                        }
                        Text(
                            TimeUtils.formatDay(session.startedAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = StarlightDim
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Meta(value: String, label: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = StarlightFaint)
    }
}
