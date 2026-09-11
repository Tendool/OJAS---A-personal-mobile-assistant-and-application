package com.ojas.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.dao.WorkoutWithExercises
import com.ojas.assistant.data.local.entity.ExerciseEntity
import com.ojas.assistant.data.local.entity.WorkoutEntity
import com.ojas.assistant.data.local.entity.WorkoutSessionEntity
import com.ojas.assistant.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WorkoutListUiState(
    val workouts: List<WorkoutWithExercises> = emptyList(),
    val recent: List<WorkoutSessionEntity> = emptyList(),
    val streak: Int = 0,
    val week: List<DayBar> = emptyList()
)

class WorkoutListViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<WorkoutListUiState> = combine(
        container.workouts.observeWorkouts(),
        container.workouts.recentSessions(12),
        container.workouts.streak(),
        container.workouts.lastDays(7)
    ) { workouts, recent, streak, week ->
        WorkoutListUiState(
            workouts = workouts,
            recent = recent,
            streak = streak,
            week = toWeekBars(week.associate { it.epochDay to it.total })
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutListUiState())

    fun delete(id: Long) {
        viewModelScope.launch { container.workouts.deleteWorkout(id) }
    }

    fun save(workout: WorkoutEntity, exercises: List<ExerciseEntity>, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch { onSaved(container.workouts.saveWorkout(workout, exercises)) }
    }

    suspend fun load(id: Long): WorkoutWithExercises? = container.workouts.getWorkout(id)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WorkoutListViewModel(container) }
        }
    }
}

// ---------------------------------------------------------------- the session

enum class StepKind { WORK, REST }

data class SessionStep(
    val kind: StepKind,
    val name: String,
    val note: String,
    val setNumber: Int,
    val setsInExercise: Int,
    val reps: Int,
    val seconds: Int
) {
    val isTimed: Boolean get() = kind == StepKind.REST || seconds > 0
}

data class WorkoutSessionUiState(
    val workout: WorkoutWithExercises? = null,
    val steps: List<SessionStep> = emptyList(),
    val index: Int = 0,
    val secondsLeft: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    val completedSets: Int = 0,
    val elapsedSeconds: Int = 0
) {
    val current: SessionStep? get() = steps.getOrNull(index)
    val next: SessionStep? get() = steps.drop(index + 1).firstOrNull { it.kind == StepKind.WORK }
    val totalSets: Int get() = steps.count { it.kind == StepKind.WORK }
    val progress: Float
        get() = if (steps.isEmpty()) 0f else (index.toFloat() / steps.size).coerceIn(0f, 1f)
}

/**
 * Runs one workout as a flat list of work and rest steps.
 *
 * A single coroutine drives the clock; timed steps advance themselves and rep-based
 * steps wait for the user. Keeping it flat means "skip", "back" and progress reporting
 * are all trivial index maths rather than nested exercise/set bookkeeping.
 */
class WorkoutSessionViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutSessionUiState())
    val state: StateFlow<WorkoutSessionUiState> = _state.asStateFlow()

    private var ticker: Job? = null
    private var startedAt = 0L

    fun load(workoutId: Long) {
        if (_state.value.workout?.workout?.id == workoutId) return
        viewModelScope.launch {
            val workout = container.workouts.getWorkout(workoutId) ?: return@launch
            val steps = buildSteps(workout)
            _state.value = WorkoutSessionUiState(
                workout = workout,
                steps = steps,
                secondsLeft = steps.firstOrNull()?.seconds ?: 0
            )
        }
    }

    fun start() {
        if (_state.value.steps.isEmpty() || _state.value.finished) return
        if (startedAt == 0L) startedAt = TimeUtils.now()
        _state.update { it.copy(running = true) }
        startTicker()
    }

    fun pause() {
        _state.update { it.copy(running = false) }
        ticker?.cancel()
        ticker = null
    }

    fun toggle() = if (_state.value.running) pause() else start()

    /** Completes the current step; for rep-based work this is the user tapping "done". */
    fun advance() {
        val s = _state.value
        val current = s.current ?: return
        val completed = s.completedSets + if (current.kind == StepKind.WORK) 1 else 0
        moveTo(s.index + 1, completed)
    }

    fun skip() {
        val s = _state.value
        moveTo(s.index + 1, s.completedSets)
    }

    fun back() {
        val s = _state.value
        if (s.index == 0) return
        val previousWasWork = s.steps.getOrNull(s.index - 1)?.kind == StepKind.WORK
        moveTo(s.index - 1, (s.completedSets - if (previousWasWork) 1 else 0).coerceAtLeast(0))
    }

    fun finish() {
        val s = _state.value
        pause()
        val workout = s.workout ?: return
        if (startedAt == 0L) return
        val now = TimeUtils.now()
        viewModelScope.launch {
            container.workouts.recordSession(
                workoutId = workout.workout.id,
                workoutName = workout.workout.name,
                startedAt = startedAt,
                endedAt = now,
                completedSets = s.completedSets,
                totalSets = s.totalSets,
                activeSeconds = s.elapsedSeconds
            )
        }
        _state.update { it.copy(finished = true, running = false) }
    }

    fun reset() {
        pause()
        startedAt = 0L
        val steps = _state.value.steps
        _state.value = _state.value.copy(
            index = 0,
            secondsLeft = steps.firstOrNull()?.seconds ?: 0,
            completedSets = 0,
            elapsedSeconds = 0,
            finished = false,
            running = false
        )
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    // ------------------------------------------------------------------ internals

    private fun moveTo(index: Int, completedSets: Int) {
        val s = _state.value
        if (index >= s.steps.size) {
            _state.update { it.copy(completedSets = completedSets) }
            finish()
            return
        }
        _state.update {
            it.copy(
                index = index.coerceAtLeast(0),
                secondsLeft = it.steps[index.coerceAtLeast(0)].seconds,
                completedSets = completedSets
            )
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val s = _state.value
                if (!s.running || s.finished) break

                val elapsed = s.elapsedSeconds + 1
                val step = s.current
                if (step == null) break

                if (step.isTimed) {
                    val left = s.secondsLeft - 1
                    if (left <= 0) {
                        _state.update { it.copy(elapsedSeconds = elapsed) }
                        advance()
                    } else {
                        _state.update { it.copy(secondsLeft = left, elapsedSeconds = elapsed) }
                    }
                } else {
                    // Rep-based work still accrues time so the session total is honest.
                    _state.update { it.copy(elapsedSeconds = elapsed) }
                }
            }
        }
    }

    private fun buildSteps(workout: WorkoutWithExercises): List<SessionStep> {
        val exercises = workout.exercises.sortedBy { it.position }
        val steps = ArrayList<SessionStep>(exercises.sumOf { it.sets } * 2)

        exercises.forEachIndexed { exerciseIndex, exercise ->
            repeat(exercise.sets.coerceAtLeast(1)) { setIndex ->
                steps += SessionStep(
                    kind = StepKind.WORK,
                    name = exercise.name,
                    note = exercise.note,
                    setNumber = setIndex + 1,
                    setsInExercise = exercise.sets.coerceAtLeast(1),
                    reps = exercise.reps,
                    seconds = exercise.durationSec
                )

                val isFinalSetOfFinalExercise =
                    exerciseIndex == exercises.lastIndex && setIndex == exercise.sets - 1
                if (!isFinalSetOfFinalExercise && exercise.restSec > 0) {
                    steps += SessionStep(
                        kind = StepKind.REST,
                        name = "Rest",
                        note = "",
                        setNumber = setIndex + 1,
                        setsInExercise = exercise.sets.coerceAtLeast(1),
                        reps = 0,
                        seconds = exercise.restSec
                    )
                }
            }
        }
        return steps
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WorkoutSessionViewModel(container) }
        }
    }
}
