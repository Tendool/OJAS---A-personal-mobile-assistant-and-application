package com.ojas.assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojas.assistant.data.local.entity.ExerciseEntity
import com.ojas.assistant.data.local.entity.WorkoutEntity
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OjasScreen
import com.ojas.assistant.ui.components.OrbitButton
import com.ojas.assistant.ui.components.SectionLabel
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.theme.EmberOrange
import com.ojas.assistant.ui.theme.PulsarRose
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import com.ojas.assistant.ui.theme.accentFor
import com.ojas.assistant.ui.theme.accentKeys
import com.ojas.assistant.ui.viewmodel.WorkoutListViewModel

/** Mutable working copy of one exercise while the routine is being edited. */
private class ExerciseDraft(
    name: String = "",
    sets: Int = 3,
    reps: Int = 12,
    seconds: Int = 0,
    rest: Int = 45,
    note: String = ""
) {
    var name by mutableStateOf(name)
    var sets by mutableStateOf(sets)
    var reps by mutableStateOf(reps)
    var seconds by mutableStateOf(seconds)
    var rest by mutableStateOf(rest)
    var note by mutableStateOf(note)

    val timed: Boolean get() = seconds > 0

    fun toEntity(position: Int) = ExerciseEntity(
        workoutId = 0L,
        position = position,
        name = name.trim().ifBlank { "Move ${position + 1}" },
        sets = sets.coerceIn(1, 20),
        reps = if (timed) 0 else reps.coerceIn(1, 200),
        durationSec = if (timed) seconds.coerceIn(5, 3600) else 0,
        restSec = rest.coerceIn(0, 600),
        note = note.trim()
    )
}

@Composable
fun WorkoutEditScreen(
    workoutId: Long,
    contentPadding: PaddingValues,
    onDone: () -> Unit
) {
    val container = LocalOjas.current
    val viewModel: WorkoutListViewModel = viewModel(factory = WorkoutListViewModel.factory(container))

    var name by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf("") }
    var accentKey by remember { mutableStateOf("nebula") }
    var loaded by remember { mutableStateOf(workoutId == 0L) }
    val drafts = remember { mutableStateListOf<ExerciseDraft>() }

    LaunchedEffect(workoutId) {
        if (workoutId == 0L) {
            if (drafts.isEmpty()) drafts.add(ExerciseDraft())
            return@LaunchedEffect
        }
        val existing = viewModel.load(workoutId)
        if (existing != null) {
            name = existing.workout.name
            focus = existing.workout.focus
            accentKey = existing.workout.accentKey
            drafts.clear()
            existing.exercises.sortedBy { it.position }.forEach {
                drafts.add(
                    ExerciseDraft(it.name, it.sets, it.reps, it.durationSec, it.restSec, it.note)
                )
            }
        }
        loaded = true
    }

    val accent = accentFor(accentKey)

    OjasScreen(
        title = if (workoutId == 0L) "New routine" else "Edit routine",
        subtitle = if (loaded) "${drafts.size} moves" else "Loading…",
        contentPadding = contentPadding
    ) {
        item(key = "identity") {
            OjasCard {
                FieldLabel("Name")
                PlainField(value = name, onChange = { name = it }, placeholder = "Orbit Reset")
                Spacer(Modifier.height(14.dp))
                FieldLabel("Focus")
                PlainField(value = focus, onChange = { focus = it }, placeholder = "Full body")
                Spacer(Modifier.height(16.dp))
                FieldLabel("Accent")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    accentKeys.forEach { key ->
                        val colour = accentFor(key)
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(colour.copy(alpha = if (key == accentKey) 0.95f else 0.28f))
                                .border(
                                    if (key == accentKey) 2.dp else 1.dp,
                                    if (key == accentKey) Color.White else SpaceOutlineSoft,
                                    CircleShape
                                )
                                .clickable { accentKey = key }
                        )
                    }
                }
            }
        }

        item(key = "moves-label") {
            SectionLabel("Moves", trailing = "${drafts.size}")
        }

        itemsIndexed(drafts) { index, draft ->
            ExerciseCard(
                draft = draft,
                index = index,
                canMoveUp = index > 0,
                canMoveDown = index < drafts.lastIndex,
                accent = accent,
                onMoveUp = { if (index > 0) drafts.swap(index, index - 1) },
                onMoveDown = { if (index < drafts.lastIndex) drafts.swap(index, index + 1) },
                onRemove = { if (drafts.size > 1) drafts.removeAt(index) }
            )
        }

        item(key = "add") {
            OrbitButton(
                text = "Add a move",
                icon = Icons.Rounded.Add,
                onClick = { drafts.add(ExerciseDraft()) },
                accent = accent,
                filled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item(key = "save") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (workoutId != 0L) {
                    OrbitButton(
                        text = "Delete",
                        onClick = {
                            viewModel.delete(workoutId)
                            onDone()
                        },
                        accent = PulsarRose,
                        filled = false,
                        modifier = Modifier.weight(1f)
                    )
                }
                OrbitButton(
                    text = "Save routine",
                    onClick = {
                        viewModel.save(
                            WorkoutEntity(
                                id = workoutId,
                                name = name.trim().ifBlank { "Untitled routine" },
                                focus = focus.trim().ifBlank { "General" },
                                accentKey = accentKey
                            ),
                            drafts.mapIndexed { i, d -> d.toEntity(i) }
                        )
                        onDone()
                    },
                    accent = accent,
                    enabled = drafts.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    draft: ExerciseDraft,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    accent: Color,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    OjasCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "%02d".format(index + 1),
                style = MaterialTheme.typography.labelLarge,
                color = accent
            )
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                PlainField(
                    value = draft.name,
                    onChange = { draft.name = it },
                    placeholder = "Push-ups"
                )
            }
            if (canMoveUp) {
                TinyIcon(Icons.Rounded.ArrowUpward, "Move up", onMoveUp)
            }
            if (canMoveDown) {
                TinyIcon(Icons.Rounded.ArrowDownward, "Move down", onMoveDown)
            }
            TinyIcon(Icons.Rounded.Close, "Remove", onRemove, tint = PulsarRose)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Stepper(
                label = "Sets",
                value = draft.sets,
                accent = accent,
                modifier = Modifier.weight(1f),
                onChange = { draft.sets = it.coerceIn(1, 20) }
            )
            if (draft.timed) {
                Stepper(
                    label = "Seconds",
                    value = draft.seconds,
                    accent = accent,
                    step = 5,
                    modifier = Modifier.weight(1f),
                    onChange = { draft.seconds = it.coerceIn(5, 900) }
                )
            } else {
                Stepper(
                    label = "Reps",
                    value = draft.reps,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                    onChange = { draft.reps = it.coerceIn(1, 100) }
                )
            }
            Stepper(
                label = "Rest",
                value = draft.rest,
                accent = accent,
                step = 5,
                modifier = Modifier.weight(1f),
                onChange = { draft.rest = it.coerceIn(0, 300) }
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = if (draft.timed) "Switch to reps" else "Switch to a timed hold",
            style = MaterialTheme.typography.labelSmall,
            color = StarlightFaint,
            modifier = Modifier.clickable {
                if (draft.timed) {
                    draft.seconds = 0
                    if (draft.reps <= 0) draft.reps = 12
                } else {
                    draft.seconds = 30
                }
            }
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    accent: Color,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = StarlightFaint)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(Color(0x4D0A1224))
                .border(1.dp, SpaceOutlineSoft, RoundedCornerShape(13.dp))
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepIcon(Icons.Rounded.Remove, "Decrease $label") { onChange(value - step) }
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            StepIcon(Icons.Rounded.Add, "Increase $label") { onChange(value + step) }
        }
    }
}

@Composable
private fun StepIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = StarlightDim, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun TinyIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = StarlightFaint
) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(15.dp))
    }
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = StarlightFaint)
}

@Composable
internal fun PlainField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = StarlightFaint)
        },
        singleLine = singleLine,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0x660A1224),
            unfocusedContainerColor = Color(0x4D0A1224),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = EmberOrange
        )
    )
}

private fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.swap(a: Int, b: Int) {
    val tmp = this[a]
    this[a] = this[b]
    this[b] = tmp
}
