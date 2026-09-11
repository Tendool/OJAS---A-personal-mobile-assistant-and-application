package com.ojas.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.entity.MeditationSessionEntity
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

/** One leg of a breathing cycle. [expand] drives the visual: true grows, false shrinks. */
data class BreathPhase(val label: String, val seconds: Int, val expand: Boolean?)

data class BreathPreset(
    val key: String,
    val name: String,
    val description: String,
    val phases: List<BreathPhase>
) {
    val cycleSeconds: Int get() = phases.sumOf { it.seconds }
}

object BreathPresets {
    val box = BreathPreset(
        "box", "Box", "Four counts each way. Steadies a racing mind.",
        listOf(
            BreathPhase("Breathe in", 4, true),
            BreathPhase("Hold", 4, null),
            BreathPhase("Breathe out", 4, false),
            BreathPhase("Hold", 4, null)
        )
    )
    val relax = BreathPreset(
        "relax", "4-7-8", "A long exhale. Best right before sleep.",
        listOf(
            BreathPhase("Breathe in", 4, true),
            BreathPhase("Hold", 7, null),
            BreathPhase("Breathe out", 8, false)
        )
    )
    val coherent = BreathPreset(
        "coherent", "Coherent", "Five and a half each way, the resonant rhythm.",
        listOf(
            BreathPhase("Breathe in", 6, true),
            BreathPhase("Breathe out", 6, false)
        )
    )
    val ground = BreathPreset(
        "ground", "Grounding", "Short in, long out. Pulls the nervous system down.",
        listOf(
            BreathPhase("Breathe in", 4, true),
            BreathPhase("Breathe out", 6, false)
        )
    )
    val still = BreathPreset(
        "still", "Silent", "No pacing. Just the timer and the stars.",
        listOf(BreathPhase("Rest", 60, null))
    )

    val all = listOf(box, relax, coherent, ground, still)

    fun byKey(key: String): BreathPreset = all.firstOrNull { it.key == key } ?: box
}

data class MeditationUiState(
    val preset: BreathPreset = BreathPresets.box,
    val plannedMinutes: Int = 10,
    val elapsedSeconds: Int = 0,
    val phaseIndex: Int = 0,
    val phaseSecondsLeft: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    val todaySeconds: Int = 0,
    val streak: Int = 0,
    val lifetimeMinutes: Int = 0,
    val recent: List<MeditationSessionEntity> = emptyList(),
    val week: List<DayBar> = emptyList()
) {
    val plannedSeconds: Int get() = plannedMinutes * 60
    val remainingSeconds: Int get() = (plannedSeconds - elapsedSeconds).coerceAtLeast(0)
    val progress: Float
        get() = if (plannedSeconds == 0) 0f else (elapsedSeconds.toFloat() / plannedSeconds).coerceIn(0f, 1f)

    val phase: BreathPhase get() = preset.phases[phaseIndex.coerceIn(0, preset.phases.lastIndex)]

    /** 0 at the start of the phase, 1 at its end; drives the breathing orb. */
    val phaseProgress: Float
        get() {
            val total = phase.seconds.coerceAtLeast(1)
            return ((total - phaseSecondsLeft).toFloat() / total).coerceIn(0f, 1f)
        }
}

class MeditationViewModel(private val container: AppContainer) : ViewModel() {

    private val session = MutableStateFlow(MeditationUiState())

    private var ticker: Job? = null
    private var startedAt = 0L

    val state: StateFlow<MeditationUiState> = combine(
        session,
        container.meditation.secondsToday(),
        container.meditation.streak(),
        container.meditation.lifetimeMinutes(),
        combine(
            container.meditation.recent(10),
            container.meditation.lastDays(7)
        ) { recent, week -> recent to week }
    ) { local, today, streak, lifetime, history ->
        local.copy(
            todaySeconds = today,
            streak = streak,
            lifetimeMinutes = lifetime,
            recent = history.first,
            week = toWeekBars(history.second.associate { it.epochDay to it.total })
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeditationUiState())

    init {
        viewModelScope.launch {
            val settings = container.settings.settings
            settings.collect { s ->
                if (!session.value.running && session.value.elapsedSeconds == 0) {
                    session.update {
                        it.copy(
                            preset = BreathPresets.byKey(s.meditationPresetKey),
                            plannedMinutes = s.meditationDefaultMinutes,
                            phaseSecondsLeft = BreathPresets.byKey(s.meditationPresetKey).phases.first().seconds
                        )
                    }
                }
            }
        }
    }

    fun selectPreset(preset: BreathPreset) {
        if (session.value.running) return
        session.update {
            it.copy(preset = preset, phaseIndex = 0, phaseSecondsLeft = preset.phases.first().seconds)
        }
        viewModelScope.launch { container.settings.setMeditationPreset(preset.key) }
    }

    fun setMinutes(minutes: Int) {
        if (session.value.running) return
        session.update { it.copy(plannedMinutes = minutes.coerceIn(1, 180)) }
        viewModelScope.launch { container.settings.setMeditationMinutes(minutes) }
    }

    fun start() {
        val s = session.value
        if (s.running) return
        if (startedAt == 0L) startedAt = TimeUtils.now()
        session.update {
            it.copy(
                running = true,
                finished = false,
                phaseSecondsLeft = if (it.phaseSecondsLeft > 0) it.phaseSecondsLeft else it.phase.seconds
            )
        }
        startTicker()
    }

    fun pause() {
        session.update { it.copy(running = false) }
        ticker?.cancel()
        ticker = null
    }

    fun toggle() = if (session.value.running) pause() else start()

    /** Ends the sit early but still records whatever time was actually spent. */
    fun stop(record: Boolean = true) {
        pause()
        val s = session.value
        if (record && startedAt != 0L && s.elapsedSeconds >= MIN_RECORDED_SECONDS) {
            viewModelScope.launch {
                container.meditation.record(
                    presetKey = s.preset.key,
                    presetName = s.preset.name,
                    startedAt = startedAt,
                    plannedSeconds = s.plannedSeconds,
                    actualSeconds = s.elapsedSeconds
                )
            }
        }
        startedAt = 0L
        session.update {
            it.copy(
                elapsedSeconds = 0,
                phaseIndex = 0,
                phaseSecondsLeft = it.preset.phases.first().seconds,
                running = false,
                finished = false
            )
        }
    }

    fun dismissFinished() {
        session.update { it.copy(finished = false) }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val s = session.value
                if (!s.running) break

                val elapsed = s.elapsedSeconds + 1
                if (elapsed >= s.plannedSeconds) {
                    complete(elapsed)
                    break
                }

                var phaseIndex = s.phaseIndex
                var phaseLeft = s.phaseSecondsLeft - 1
                if (phaseLeft <= 0) {
                    phaseIndex = (phaseIndex + 1) % s.preset.phases.size
                    phaseLeft = s.preset.phases[phaseIndex].seconds
                }
                session.update {
                    it.copy(elapsedSeconds = elapsed, phaseIndex = phaseIndex, phaseSecondsLeft = phaseLeft)
                }
            }
        }
    }

    private fun complete(elapsed: Int) {
        val s = session.value
        val began = startedAt
        session.update { it.copy(elapsedSeconds = elapsed, running = false, finished = true) }
        startedAt = 0L
        if (began == 0L) return
        viewModelScope.launch {
            container.meditation.record(
                presetKey = s.preset.key,
                presetName = s.preset.name,
                startedAt = began,
                plannedSeconds = s.plannedSeconds,
                actualSeconds = elapsed
            )
        }
    }

    companion object {
        private const val MIN_RECORDED_SECONDS = 30

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MeditationViewModel(container) }
        }
    }
}
