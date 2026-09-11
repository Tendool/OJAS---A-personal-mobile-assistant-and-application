package com.ojas.assistant.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.entity.AppLimitEntity
import com.ojas.assistant.data.repository.OjasSettings
import com.ojas.assistant.di.AppContainer
import com.ojas.assistant.screentime.AppUsage
import com.ojas.assistant.screentime.ScreenTimeMonitorService
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

data class FocusState(
    val running: Boolean = false,
    val plannedMinutes: Int = 25,
    val secondsLeft: Int = 0,
    val startedAt: Long = 0L
) {
    val progress: Float
        get() {
            val total = plannedMinutes * 60
            return if (total == 0) 0f else ((total - secondsLeft).toFloat() / total).coerceIn(0f, 1f)
        }
}

data class ScreenTimeUiState(
    val settings: OjasSettings = OjasSettings(),
    val hasAccess: Boolean = false,
    val loading: Boolean = true,
    val usage: List<AppUsage> = emptyList(),
    val limits: List<AppLimitEntity> = emptyList(),
    val week: List<DayBar> = emptyList(),
    val focus: FocusState = FocusState(),
    val focusMinutesToday: Int = 0,
    val installedApps: List<AppUsage> = emptyList()
) {
    val totalMinutes: Int get() = usage.sumOf { it.minutes }

    val goalProgress: Float
        get() = if (settings.screenTimeGoalMinutes <= 0) 0f
        else totalMinutes.toFloat() / settings.screenTimeGoalMinutes

    fun limitFor(pkg: String): AppLimitEntity? = limits.firstOrNull { it.packageName == pkg }
}

class ScreenTimeViewModel(private val container: AppContainer) : ViewModel() {

    private val usage = MutableStateFlow<List<AppUsage>>(emptyList())
    private val hasAccess = MutableStateFlow(false)
    private val loading = MutableStateFlow(true)
    private val installed = MutableStateFlow<List<AppUsage>>(emptyList())
    private val focus = MutableStateFlow(FocusState())

    val focusState: StateFlow<FocusState> = focus.asStateFlow()

    private var focusTicker: Job? = null

    /** Everything sourced from UsageStatsManager, bundled so `combine` stays inside its arity. */
    private data class UsageBundle(
        val usage: List<AppUsage>,
        val hasAccess: Boolean,
        val loading: Boolean,
        val installed: List<AppUsage>
    )

    val state: StateFlow<ScreenTimeUiState> = combine(
        container.settings.settings,
        combine(usage, hasAccess, loading, installed, ::UsageBundle),
        container.screenTime.observeLimits(),
        container.screenTime.lastDays(7),
        combine(focus, container.screenTime.focusMinutesToday()) { f, minutes -> f to minutes }
    ) { settings, bundle, limits, week, focusPair ->
        ScreenTimeUiState(
            settings = settings,
            usage = bundle.usage,
            hasAccess = bundle.hasAccess,
            loading = bundle.loading,
            installedApps = bundle.installed,
            limits = limits,
            week = toWeekBars(week.associate { it.epochDay to it.total }),
            focus = focusPair.first,
            focusMinutesToday = focusPair.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenTimeUiState())

    fun refresh() {
        viewModelScope.launch {
            val repo = container.screenTime
            val access = repo.hasUsageAccess()
            hasAccess.value = access
            if (access) {
                usage.value = repo.usageFor()
                repo.snapshotToday()
            } else {
                usage.value = emptyList()
            }
            loading.value = false
        }
    }

    fun loadInstalledApps() {
        if (installed.value.isNotEmpty()) return
        viewModelScope.launch { installed.value = container.screenTime.launchableApps() }
    }

    fun setLimit(packageName: String, minutes: Int, hardBlock: Boolean) {
        viewModelScope.launch { container.screenTime.setLimit(packageName, minutes, hardBlock) }
    }

    fun removeLimit(packageName: String) {
        viewModelScope.launch { container.screenTime.removeLimit(packageName) }
    }

    fun setMonitoring(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setScreenTimeEnabled(enabled)
            if (enabled) ScreenTimeMonitorService.start(context)
            else ScreenTimeMonitorService.stop(context)
        }
    }

    fun setDailyGoal(minutes: Int) {
        viewModelScope.launch { container.settings.setScreenTimeGoal(minutes) }
    }

    // ------------------------------------------------------------------- focus

    fun startFocus(context: Context, minutes: Int) {
        if (focus.value.running) return
        focus.value = FocusState(
            running = true,
            plannedMinutes = minutes,
            secondsLeft = minutes * 60,
            startedAt = TimeUtils.now()
        )
        ScreenTimeMonitorService.startFocus(context, minutes)
        viewModelScope.launch { container.settings.setFocusMinutes(minutes) }

        focusTicker?.cancel()
        focusTicker = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val current = focus.value
                if (!current.running) break
                val left = current.secondsLeft - 1
                if (left <= 0) {
                    completeFocus(context, completed = true)
                    break
                }
                focus.update { it.copy(secondsLeft = left) }
            }
        }
    }

    fun stopFocus(context: Context) = completeFocus(context, completed = false)

    private fun completeFocus(context: Context, completed: Boolean) {
        val current = focus.value
        focusTicker?.cancel()
        focusTicker = null
        focus.value = FocusState(plannedMinutes = current.plannedMinutes)
        ScreenTimeMonitorService.endFocus(context)
        if (current.startedAt == 0L) return
        viewModelScope.launch {
            container.screenTime.recordFocus(
                startedAt = current.startedAt,
                endedAt = TimeUtils.now(),
                plannedMinutes = current.plannedMinutes,
                completed = completed
            )
        }
    }

    override fun onCleared() {
        focusTicker?.cancel()
        super.onCleared()
    }

    fun usageAccessIntent() = container.screenTime.usageAccessSettingsIntent()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScreenTimeViewModel(container) }
        }
    }
}
