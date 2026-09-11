package com.ojas.assistant.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.ui.components.OjasCard
import com.ojas.assistant.ui.components.OrbitButton
import com.ojas.assistant.ui.components.OrbitRing
import com.ojas.assistant.ui.navigation.LocalOjas
import com.ojas.assistant.ui.theme.AuroraTeal
import com.ojas.assistant.ui.theme.EmberOrange
import com.ojas.assistant.ui.theme.SpaceOutlineSoft
import com.ojas.assistant.ui.theme.StarlightDim
import com.ojas.assistant.ui.theme.StarlightFaint
import com.ojas.assistant.ui.theme.accentFor
import com.ojas.assistant.ui.viewmodel.StepKind
import com.ojas.assistant.ui.viewmodel.WorkoutSessionViewModel

@Composable
fun WorkoutSessionScreen(
    workoutId: Long,
    contentPadding: PaddingValues,
    onDone: () -> Unit
) {
    val container = LocalOjas.current
    val viewModel: WorkoutSessionViewModel =
        viewModel(factory = WorkoutSessionViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(workoutId) { viewModel.load(workoutId) }
    KeepScreenOn(active = state.running)

    // Leaving mid-session should not silently discard the work already done.
    BackHandler(enabled = state.running) {
        viewModel.finish()
        onDone()
    }

    val accent = accentFor(state.workout?.workout?.accentKey)
    val step = state.current
    val resting = step?.kind == StepKind.REST
    val liveAccent = if (resting) AuroraTeal else accent

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
            .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    state.workout?.workout?.name ?: "Session",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${state.completedSets} of ${state.totalSets} sets  ·  " +
                        TimeUtils.humanDuration(state.elapsedSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = StarlightFaint
                )
            }
            Text(
                "end",
                style = MaterialTheme.typography.labelLarge,
                color = StarlightFaint,
                modifier = Modifier
                    .clickable {
                        viewModel.finish()
                        onDone()
                    }
                    .padding(8.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        if (state.finished) {
            FinishedPanel(
                completedSets = state.completedSets,
                totalSets = state.totalSets,
                seconds = state.elapsedSeconds,
                accent = accent,
                onAgain = { viewModel.reset() },
                onDone = onDone
            )
        } else if (step == null) {
            Text(
                "Loading routine…",
                style = MaterialTheme.typography.bodyLarge,
                color = StarlightDim
            )
        } else {
            OrbitRing(
                progress = state.progress,
                accent = liveAccent,
                strokeWidth = 10.dp,
                modifier = Modifier.size(268.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 26.dp)
                ) {
                    Text(
                        if (resting) "REST" else "SET ${step.setNumber} / ${step.setsInExercise}",
                        style = MaterialTheme.typography.labelMedium,
                        color = liveAccent
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        step.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (step.isTimed) TimeUtils.clockDigits(state.secondsLeft) else "${step.reps} reps",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Light,
                        color = liveAccent
                    )
                    if (step.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            step.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = StarlightFaint,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            state.next?.let { upcoming ->
                Text(
                    "Next · ${upcoming.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StarlightFaint
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlOrb(
                    icon = Icons.Rounded.SkipPrevious,
                    description = "Previous step",
                    size = 52.dp,
                    accent = StarlightDim,
                    onClick = viewModel::back
                )
                ControlOrb(
                    icon = if (state.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    description = if (state.running) "Pause" else "Start",
                    size = 74.dp,
                    accent = liveAccent,
                    onClick = viewModel::toggle
                )
                ControlOrb(
                    icon = if (step.isTimed) Icons.Rounded.SkipNext else Icons.Rounded.Check,
                    description = if (step.isTimed) "Skip" else "Set complete",
                    size = 52.dp,
                    accent = if (step.isTimed) StarlightDim else EmberOrange,
                    onClick = { if (step.isTimed) viewModel.skip() else viewModel.advance() }
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FinishedPanel(
    completedSets: Int,
    totalSets: Int,
    seconds: Int,
    accent: Color,
    onAgain: () -> Unit,
    onDone: () -> Unit
) {
    OjasCard(contentPadding = PaddingValues(24.dp)) {
        Text(
            "Session logged",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "$completedSets of $totalSets sets in ${TimeUtils.humanDuration(seconds)}.",
            style = MaterialTheme.typography.bodyLarge,
            color = StarlightDim
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OrbitButton(
                text = "Again",
                onClick = onAgain,
                accent = accent,
                filled = false,
                modifier = Modifier.weight(1f)
            )
            OrbitButton(
                text = "Done",
                onClick = onDone,
                accent = accent,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ControlOrb(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, SpaceOutlineSoft, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = accent, modifier = Modifier.size(size * 0.42f))
    }
}

/**
 * Holds the display awake for the duration of a timed activity. Scoped with a
 * DisposableEffect so the flag is always cleared, including on process death paths that
 * still run composition teardown.
 */
@Composable
fun KeepScreenOn(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active) {
        val window = context.findActivity()?.window
        if (active) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
