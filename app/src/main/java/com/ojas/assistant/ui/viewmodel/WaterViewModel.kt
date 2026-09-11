package com.ojas.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.entity.WaterLogEntity
import com.ojas.assistant.data.repository.OjasSettings
import com.ojas.assistant.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class DayBar(val label: String, val value: Int)

data class WaterUiState(
    val settings: OjasSettings = OjasSettings(),
    val totalMl: Int = 0,
    val logs: List<WaterLogEntity> = emptyList(),
    val week: List<DayBar> = emptyList(),
    val nextNudgeAt: Long = 0L
) {
    val progress: Float
        get() = if (settings.waterGoalMl <= 0) 0f else totalMl.toFloat() / settings.waterGoalMl

    val remainingMl: Int get() = (settings.waterGoalMl - totalMl).coerceAtLeast(0)

    val cupsLogged: Int
        get() = if (settings.waterCupMl <= 0) 0 else totalMl / settings.waterCupMl

    /** Best day in the window, used to scale the bar chart. */
    val weekPeak: Int get() = maxOf(week.maxOfOrNull { it.value } ?: 0, settings.waterGoalMl)
}

class WaterViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<WaterUiState> = combine(
        container.settings.settings,
        container.water.todayTotal(),
        container.water.todayLogs(),
        container.water.lastDays(7)
    ) { settings, total, logs, week ->
        WaterUiState(
            settings = settings,
            totalMl = total,
            logs = logs,
            week = toWeekBars(week.associate { it.epochDay to it.total }),
            nextNudgeAt = if (settings.waterRemindersEnabled) {
                container.water.nextNudgeAt(settings)
            } else {
                0L
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WaterUiState())

    fun log(amountMl: Int) {
        viewModelScope.launch { container.water.log(amountMl) }
    }

    fun undoLast() {
        viewModelScope.launch { container.water.undoLast() }
    }

    fun delete(log: WaterLogEntity) {
        viewModelScope.launch { container.water.delete(log) }
    }

    fun setGoal(ml: Int) {
        viewModelScope.launch { container.settings.setWaterGoal(ml) }
    }

    fun setCup(ml: Int) {
        viewModelScope.launch { container.settings.setWaterCup(ml) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setWaterReminders(enabled)
            container.water.rescheduleNudge()
        }
    }

    fun setInterval(minutes: Int) {
        viewModelScope.launch {
            container.settings.setWaterInterval(minutes)
            container.water.rescheduleNudge()
        }
    }

    fun setWindow(startHour: Int, endHour: Int) {
        viewModelScope.launch {
            container.settings.setWaterWindow(startHour, endHour)
            container.water.rescheduleNudge()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WaterViewModel(container) }
        }
    }
}

/** Turns a sparse day-to-total map into a dense, ordered seven-day series. */
internal fun toWeekBars(totals: Map<Long, Int>, days: Int = 7): List<DayBar> {
    val today = TimeUtils.todayEpochDay()
    return (0 until days).map { offset ->
        val day = today - (days - 1 - offset)
        val date = LocalDate.ofEpochDay(day)
        DayBar(
            label = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.US),
            value = totals[day] ?: 0
        )
    }
}
