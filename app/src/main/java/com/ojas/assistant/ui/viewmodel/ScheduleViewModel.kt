package com.ojas.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.entity.AlarmEntity
import com.ojas.assistant.data.local.entity.CalendarEventEntity
import com.ojas.assistant.data.local.entity.ReminderEntity
import com.ojas.assistant.data.repository.OjasSettings
import com.ojas.assistant.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ScheduleUiState(
    val settings: OjasSettings = OjasSettings(),
    val alarms: List<AlarmEntity> = emptyList(),
    val reminders: List<ReminderEntity> = emptyList(),
    val canScheduleExact: Boolean = true
)

/**
 * Backs the alarms and reminders tabs. Both write through repositories that register
 * with AlarmManager as part of the same call, so the UI never has to think about it.
 */
class ScheduleViewModel(private val container: AppContainer) : ViewModel() {

    private val exactAllowed = MutableStateFlow(container.scheduler.canScheduleExact())

    val state: StateFlow<ScheduleUiState> = combine(
        container.settings.settings,
        container.alarms.observeAll(),
        container.reminders.observeAll(),
        exactAllowed
    ) { settings, alarms, reminders, exact ->
        ScheduleUiState(settings, alarms, reminders, exact)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun refreshPermissions() {
        exactAllowed.value = container.scheduler.canScheduleExact()
    }

    // ------------------------------------------------------------------ alarms

    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch { container.alarms.save(alarm) }
    }

    fun toggleAlarm(id: Long, enabled: Boolean) {
        viewModelScope.launch { container.alarms.setEnabled(id, enabled) }
    }

    fun skipNextAlarm(id: Long) {
        viewModelScope.launch { container.alarms.skipNext(id) }
    }

    fun deleteAlarm(id: Long) {
        viewModelScope.launch { container.alarms.delete(id) }
    }

    fun nextTriggerFor(alarm: AlarmEntity): Long = TimeUtils.nextAlarmTrigger(alarm)

    // --------------------------------------------------------------- reminders

    fun saveReminder(reminder: ReminderEntity) {
        viewModelScope.launch { container.reminders.save(reminder) }
    }

    fun toggleReminder(id: Long, enabled: Boolean) {
        viewModelScope.launch { container.reminders.setEnabled(id, enabled) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { container.reminders.delete(id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScheduleViewModel(container) }
        }
    }
}

// ----------------------------------------------------------------- calendar

data class CalendarUiState(
    val settings: OjasSettings = OjasSettings(),
    val visibleMonth: LocalDate = LocalDate.now(),
    val selectedDay: Long = TimeUtils.todayEpochDay(),
    val monthEvents: List<CalendarEventEntity> = emptyList(),
    val dayEvents: List<CalendarEventEntity> = emptyList()
) {
    /** Day-of-month values that carry at least one event, for the month grid dots. */
    val busyDays: Set<Long> get() = monthEvents.map { it.epochDay }.toSet()
}

class CalendarViewModel(private val container: AppContainer) : ViewModel() {

    private val visibleMonth = MutableStateFlow(LocalDate.now(TimeUtils.zone).withDayOfMonth(1))
    private val selectedDay = MutableStateFlow(TimeUtils.todayEpochDay())

    val month: StateFlow<LocalDate> = visibleMonth.asStateFlow()

    @Suppress("OPT_IN_USAGE")
    val state: StateFlow<CalendarUiState> = combine(
        container.settings.settings,
        visibleMonth,
        selectedDay,
        visibleMonth.flatMapLatest { first ->
            // A month either side keeps the grid populated while paging without refetching.
            val from = first.minusMonths(1).toEpochDay()
            val to = first.plusMonths(2).toEpochDay()
            container.calendar.observeRange(from, to)
        },
        selectedDay.flatMapLatest { container.calendar.observeDay(it) }
    ) { settings, monthStart, day, monthEvents, dayEvents ->
        CalendarUiState(settings, monthStart, day, monthEvents, dayEvents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun showMonth(date: LocalDate) {
        visibleMonth.value = date.withDayOfMonth(1)
    }

    fun nextMonth() = showMonth(visibleMonth.value.plusMonths(1))

    fun previousMonth() = showMonth(visibleMonth.value.minusMonths(1))

    fun select(epochDay: Long) {
        selectedDay.value = epochDay
        val date = LocalDate.ofEpochDay(epochDay).withDayOfMonth(1)
        if (date != visibleMonth.value) visibleMonth.value = date
    }

    fun today() {
        val today = TimeUtils.todayEpochDay()
        select(today)
    }

    fun save(event: CalendarEventEntity) {
        viewModelScope.launch { container.calendar.save(event) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { container.calendar.delete(id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CalendarViewModel(container) }
        }
    }
}
