package com.ojas.assistant.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ojas.assistant.assistant.OjasAction
import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.repository.OjasSettings
import com.ojas.assistant.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

enum class UpcomingKind { ALARM, REMINDER, EVENT }

data class UpcomingItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val at: Long,
    val kind: UpcomingKind
)

data class ChatLine(
    val text: String,
    val fromUser: Boolean,
    val at: Long = TimeUtils.now()
)

data class HomeUiState(
    val settings: OjasSettings = OjasSettings(),
    val waterMl: Int = 0,
    val workoutsToday: Int = 0,
    val workoutStreak: Int = 0,
    val meditationSeconds: Int = 0,
    val meditationStreak: Int = 0,
    val upcoming: List<UpcomingItem> = emptyList(),
    val screenTimeMinutes: Int = 0,
    val screenTimeAvailable: Boolean = false
) {
    val waterProgress: Float
        get() = if (settings.waterGoalMl <= 0) 0f else waterMl.toFloat() / settings.waterGoalMl

    val greeting: String
        get() = when (LocalTime.now(TimeUtils.zone).hour) {
            in 0..4 -> "Still awake"
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val screenTimeMinutes = MutableStateFlow(0)
    private val screenTimeAvailable = MutableStateFlow(false)

    private val _chat = MutableStateFlow<List<ChatLine>>(emptyList())
    val chat: StateFlow<List<ChatLine>> = _chat.asStateFlow()

    private val _pendingAction = MutableStateFlow<OjasAction?>(null)
    val pendingAction: StateFlow<OjasAction?> = _pendingAction.asStateFlow()

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking.asStateFlow()

    /**
     * Five source flows collapsed into one snapshot. Everything downstream reads a single
     * immutable state object, so a change to any one metric costs exactly one recomposition.
     */
    val state: StateFlow<HomeUiState> = combine(
        container.settings.settings,
        container.water.todayTotal(),
        combine(
            container.workouts.sessionsToday(),
            container.workouts.streak(),
            container.meditation.secondsToday(),
            container.meditation.streak()
        ) { workoutsToday, workoutStreak, medSeconds, medStreak ->
            intArrayOf(workoutsToday, workoutStreak, medSeconds, medStreak)
        },
        combine(
            container.reminders.observeUpcoming(4),
            container.calendar.observeUpcoming(4),
            container.alarms.observeAll()
        ) { reminders, events, alarms ->
            buildUpcoming(reminders, events, alarms)
        },
        combine(screenTimeMinutes, screenTimeAvailable) { minutes, available -> minutes to available }
    ) { settings, water, activity, upcoming, screen ->
        HomeUiState(
            settings = settings,
            waterMl = water,
            workoutsToday = activity[0],
            workoutStreak = activity[1],
            meditationSeconds = activity[2],
            meditationStreak = activity[3],
            upcoming = upcoming,
            screenTimeMinutes = screen.first,
            screenTimeAvailable = screen.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Usage stats are a cross-process query, so they refresh on demand rather than live. */
    fun refreshScreenTime() {
        viewModelScope.launch {
            val repo = container.screenTime
            val available = repo.hasUsageAccess()
            screenTimeAvailable.value = available
            screenTimeMinutes.value = if (available) repo.usageFor().sumOf { it.minutes } else 0
        }
    }

    fun ask(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _chat.value = (_chat.value + ChatLine(trimmed, fromUser = true)).takeLast(MAX_CHAT_LINES)
        _thinking.value = true
        viewModelScope.launch {
            val response = container.brain.interpret(trimmed)
            _chat.value = (_chat.value + ChatLine(response.speech, fromUser = false)).takeLast(MAX_CHAT_LINES)
            _thinking.value = false
            if (response.action != OjasAction.None) _pendingAction.value = response.action
        }
    }

    fun consumeAction() {
        _pendingAction.value = null
    }

    fun clearChat() {
        _chat.value = emptyList()
    }

    fun logWater(amountMl: Int) {
        viewModelScope.launch { container.water.log(amountMl) }
    }

    private fun buildUpcoming(
        reminders: List<com.ojas.assistant.data.local.entity.ReminderEntity>,
        events: List<com.ojas.assistant.data.local.entity.CalendarEventEntity>,
        alarms: List<com.ojas.assistant.data.local.entity.AlarmEntity>
    ): List<UpcomingItem> {
        val now = TimeUtils.now()
        val items = ArrayList<UpcomingItem>(12)

        alarms.filter { it.enabled }.forEach { alarm ->
            val at = TimeUtils.nextAlarmTrigger(alarm, now)
            if (at > 0L) {
                items += UpcomingItem(
                    key = "alarm-${alarm.id}",
                    title = alarm.label.ifBlank { "Alarm" },
                    subtitle = TimeUtils.describeDays(alarm.daysMask),
                    at = at,
                    kind = UpcomingKind.ALARM
                )
            }
        }
        reminders.filter { it.enabled && it.triggerAt > now }.forEach {
            items += UpcomingItem("reminder-${it.id}", it.title, it.repeat.label, it.triggerAt, UpcomingKind.REMINDER)
        }
        events.filter { it.startAt > now }.forEach {
            items += UpcomingItem(
                key = "event-${it.id}",
                title = it.title,
                subtitle = if (it.allDay) "All day" else TimeUtils.formatDay(it.startAt),
                at = it.startAt,
                kind = UpcomingKind.EVENT
            )
        }

        return items.sortedBy { it.at }.take(5)
    }

    companion object {
        private const val MAX_CHAT_LINES = 40

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }
}
