package com.ojas.assistant.assistant

import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.entity.AlarmEntity
import com.ojas.assistant.data.local.entity.CalendarEventEntity
import com.ojas.assistant.data.local.entity.RepeatRule
import com.ojas.assistant.data.local.entity.ReminderEntity
import com.ojas.assistant.data.repository.AlarmRepository
import com.ojas.assistant.data.repository.CalendarRepository
import com.ojas.assistant.data.repository.MeditationRepository
import com.ojas.assistant.data.repository.ReminderRepository
import com.ojas.assistant.data.repository.SettingsRepository
import com.ojas.assistant.data.repository.WaterRepository
import com.ojas.assistant.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What the UI should do after Ojas has finished answering. */
sealed interface OjasAction {
    data object None : OjasAction
    data class Navigate(val route: String) : OjasAction
    data class StartMeditation(val minutes: Int) : OjasAction
    data class StartWorkout(val workoutId: Long) : OjasAction
    data class StartFocus(val minutes: Int) : OjasAction
}

data class OjasResponse(
    val speech: String,
    val action: OjasAction = OjasAction.None
)

/**
 * The assistant, entirely on device.
 *
 * This is a deterministic intent matcher rather than a language model: it runs in
 * microseconds, needs no network, no model file and no permissions, and it never
 * misfires on a request it does not understand because unmatched input falls through to
 * an explicit "I did not catch that".
 */
class OjasBrain(
    private val water: WaterRepository,
    private val workouts: WorkoutRepository,
    private val meditation: MeditationRepository,
    private val alarms: AlarmRepository,
    private val reminders: ReminderRepository,
    private val calendar: CalendarRepository,
    private val settings: SettingsRepository
) {

    suspend fun interpret(rawInput: String): OjasResponse {
        val input = rawInput.trim().lowercase()
        if (input.isEmpty()) return OjasResponse("Say the word and I will handle it.")

        return when {
            matches(input, "water", "drink", "drank", "hydrate", "glass", "sip") -> handleWater(input)
            matches(input, "remind", "reminder") -> handleReminder(rawInput, input)
            matches(input, "alarm", "wake me", "wake up") -> handleAlarm(input)
            matches(input, "meditate", "meditation", "breathe", "breathing", "calm") -> handleMeditation(input)
            matches(input, "workout", "exercise", "train", "training") -> handleWorkout(input)
            matches(input, "focus", "deep work", "block apps") -> handleFocus(input)
            matches(input, "schedule", "event", "meeting", "appointment", "calendar") -> handleEvent(rawInput, input)
            matches(input, "screen time", "screentime", "phone usage", "usage") ->
                OjasResponse("Opening screen time.", OjasAction.Navigate("screentime"))
            matches(input, "how am i", "status", "summary", "today", "progress") -> handleStatus()
            matches(input, "help", "what can you do") -> help()
            else -> OjasResponse(
                "I did not catch that. Try \"log 250 ml\", \"set an alarm for 6:30\", " +
                    "\"remind me to stretch in 20 minutes\" or \"meditate 10 minutes\"."
            )
        }
    }

    // ------------------------------------------------------------------- water

    private suspend fun handleWater(input: String): OjasResponse {
        val s = settings.settings.first()
        if (matches(input, "how much", "how many", "left", "remaining", "so far")) {
            val drunk = water.totalToday()
            val remaining = (s.waterGoalMl - drunk).coerceAtLeast(0)
            return OjasResponse(
                if (remaining == 0) "$drunk ml logged. Goal met for today."
                else "$drunk ml so far, $remaining ml to go.",
                OjasAction.Navigate("water")
            )
        }

        val explicitMl = Regex("(\\d{2,4})\\s*(ml|millilit)").find(input)?.groupValues?.get(1)?.toIntOrNull()
        val glasses = Regex("(\\d+)\\s*(glass|cup|bottle)").find(input)?.groupValues?.get(1)?.toIntOrNull()
        val amount = explicitMl ?: glasses?.let { it * s.waterCupMl } ?: s.waterCupMl

        water.log(amount)
        val total = water.totalToday()
        val remaining = (s.waterGoalMl - total).coerceAtLeast(0)
        return OjasResponse(
            if (remaining == 0) "Logged $amount ml. That is your goal for today."
            else "Logged $amount ml. $remaining ml left.",
            OjasAction.Navigate("water")
        )
    }

    // --------------------------------------------------------------- reminders

    private suspend fun handleReminder(raw: String, input: String): OjasResponse {
        val at = parseWhen(input) ?: (TimeUtils.now() + 30 * 60_000L)
        val title = extractSubject(raw)
            .ifBlank { "Reminder" }

        val repeat = when {
            matches(input, "every day", "daily") -> RepeatRule.DAILY
            matches(input, "every week", "weekly") -> RepeatRule.WEEKLY
            matches(input, "weekdays", "every weekday") -> RepeatRule.WEEKDAYS
            matches(input, "every hour", "hourly") -> RepeatRule.HOURLY
            else -> RepeatRule.NONE
        }

        reminders.save(ReminderEntity(title = title, triggerAt = at, repeat = repeat))
        val when24 = TimeUtils.relative(at)
        return OjasResponse(
            "Reminder set: $title, $when24.",
            OjasAction.Navigate("reminders")
        )
    }

    // ------------------------------------------------------------------ alarms

    private suspend fun handleAlarm(input: String): OjasResponse {
        val time = parseTimeOfDay(input)
            ?: return OjasResponse("What time should the alarm ring? Try \"set an alarm for 6:30 am\".")

        val daysMask = when {
            matches(input, "every day", "daily") -> TimeUtils.MASK_EVERY_DAY
            matches(input, "weekday", "work day", "workdays") -> TimeUtils.MASK_WEEKDAYS
            matches(input, "weekend") -> TimeUtils.MASK_WEEKEND
            else -> 0
        }

        val label = extractSubject(input).takeIf { it.isNotBlank() && it.length < 40 } ?: ""
        val alarm = AlarmEntity(
            hour = time.hour,
            minute = time.minute,
            label = label,
            daysMask = daysMask
        )
        alarms.save(alarm)

        val clock = TimeUtils.formatTime(time.hour, time.minute, settings.settings.first().use24h)
        val repeatText = if (daysMask == 0) "" else " (${TimeUtils.describeDays(daysMask).lowercase()})"
        return OjasResponse("Alarm set for $clock$repeatText.", OjasAction.Navigate("alarms"))
    }

    // -------------------------------------------------------------- meditation

    private suspend fun handleMeditation(input: String): OjasResponse {
        val minutes = parseMinutes(input) ?: settings.settings.first().meditationDefaultMinutes
        return OjasResponse(
            "Starting a $minutes minute sit. Settle in.",
            OjasAction.StartMeditation(minutes)
        )
    }

    // ----------------------------------------------------------------- workout

    private suspend fun handleWorkout(input: String): OjasResponse {
        val all = workouts.observeWorkouts().first()
        if (all.isEmpty()) {
            return OjasResponse("No routines yet. Open Workouts to build one.", OjasAction.Navigate("workout"))
        }
        val named = all.firstOrNull { input.contains(it.workout.name.lowercase()) }
            ?: all.firstOrNull { input.contains(it.workout.focus.lowercase()) }

        return if (named != null) {
            OjasResponse("Loading ${named.workout.name}.", OjasAction.StartWorkout(named.workout.id))
        } else {
            OjasResponse("Here are your routines.", OjasAction.Navigate("workout"))
        }
    }

    // ------------------------------------------------------------------- focus

    private suspend fun handleFocus(input: String): OjasResponse {
        val minutes = parseMinutes(input) ?: settings.settings.first().focusDefaultMinutes
        return OjasResponse("Focus session for $minutes minutes.", OjasAction.StartFocus(minutes))
    }

    // ---------------------------------------------------------------- calendar

    private suspend fun handleEvent(raw: String, input: String): OjasResponse {
        val at = parseWhen(input)
            ?: return OjasResponse("When is it? Try \"schedule dentist tomorrow at 3 pm\".")
        val title = extractSubject(raw).ifBlank { "Event" }

        calendar.save(
            CalendarEventEntity(
                title = title,
                startAt = at,
                endAt = at + 60 * 60_000L,
                epochDay = TimeUtils.epochDayOf(at)
            )
        )
        return OjasResponse(
            "Added $title on ${TimeUtils.formatDay(at)} at ${TimeUtils.formatTime(at, false)}.",
            OjasAction.Navigate("calendar")
        )
    }

    // ------------------------------------------------------------------ status

    private suspend fun handleStatus(): OjasResponse {
        val s = settings.settings.first()
        val drunk = water.totalToday()
        val medSeconds = meditation.secondsToday().first()
        val workoutsToday = workouts.sessionsToday().first()
        val nextAlarm = alarms.nextTrigger()

        val parts = buildList {
            add("$drunk of ${s.waterGoalMl} ml water")
            add(if (workoutsToday > 0) "$workoutsToday workout${if (workoutsToday > 1) "s" else ""}" else "no workout yet")
            add(if (medSeconds > 0) "${medSeconds / 60} min of stillness" else "no sit yet")
            nextAlarm?.let { add("next alarm ${TimeUtils.relative(it.second, use24h = s.use24h)}") }
        }
        return OjasResponse("Today: " + parts.joinToString(", ") + ".")
    }

    private fun help(): OjasResponse = OjasResponse(
        "I can log water, set alarms and reminders, start a sit or a workout, open your " +
            "calendar and run a focus block. Everything stays on this device."
    )

    // ----------------------------------------------------------------- parsing

    private fun matches(input: String, vararg keys: String): Boolean =
        keys.any { input.contains(it) }

    /** "in 20 minutes", "in 2 hours", "10 min", "half an hour". */
    private fun parseMinutes(input: String): Int? {
        if (input.contains("half an hour")) return 30
        Regex("(\\d+)\\s*(hour|hr|h)\\b").find(input)?.let {
            return (it.groupValues[1].toIntOrNull() ?: return@let) * 60
        }
        Regex("(\\d+)\\s*(minute|min|m)\\b").find(input)?.let {
            return it.groupValues[1].toIntOrNull()
        }
        return null
    }

    /** Resolves an absolute moment from either a relative offset or a clock time. */
    private fun parseWhen(input: String): Long? {
        Regex("in\\s+(\\d+)\\s*(minute|min|m)\\b").find(input)?.let {
            val m = it.groupValues[1].toIntOrNull() ?: return@let
            return TimeUtils.now() + m * 60_000L
        }
        Regex("in\\s+(\\d+)\\s*(hour|hr|h)\\b").find(input)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: return@let
            return TimeUtils.now() + h * 3_600_000L
        }
        if (input.contains("in a minute")) return TimeUtils.now() + 60_000L
        if (input.contains("in an hour")) return TimeUtils.now() + 3_600_000L

        val time = parseTimeOfDay(input) ?: return null
        var date = LocalDate.now(TimeUtils.zone)
        if (input.contains("tomorrow")) date = date.plusDays(1)
        if (input.contains("next week")) date = date.plusWeeks(1)

        var target = LocalDateTime.of(date, time)
        // A bare clock time that has already passed today means tomorrow.
        if (!input.contains("tomorrow") && target.isBefore(LocalDateTime.now(TimeUtils.zone))) {
            target = target.plusDays(1)
        }
        return TimeUtils.toMillis(target)
    }

    /** "6:30 am", "18:45", "at 7", "tonight", "noon", "midnight". */
    private fun parseTimeOfDay(input: String): LocalTime? {
        if (input.contains("noon")) return LocalTime.of(12, 0)
        if (input.contains("midnight")) return LocalTime.of(0, 0)

        val match = Regex("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").findAll(input)
            .firstOrNull { m ->
                val hour = m.groupValues[1].toIntOrNull() ?: return@firstOrNull false
                // Reject numbers that are clearly durations or quantities.
                hour in 0..23 && (m.groupValues[3].isNotEmpty() || m.groupValues[2].isNotEmpty() ||
                    input.contains("at ${m.groupValues[1]}"))
            } ?: return null

        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridiem = match.groupValues[3]

        when {
            meridiem == "pm" && hour < 12 -> hour += 12
            meridiem == "am" && hour == 12 -> hour = 0
            meridiem.isEmpty() && input.contains("tonight") && hour < 12 -> hour += 12
            meridiem.isEmpty() && input.contains("evening") && hour < 12 -> hour += 12
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /**
     * Pulls the human-readable subject out of a command, dropping the verb prefix and any
     * trailing time phrase so "remind me to call mum at 5 pm" becomes "Call mum".
     */
    private fun extractSubject(raw: String): String {
        var text = raw.trim()
        for (prefix in SUBJECT_PREFIXES) {
            val idx = text.lowercase().indexOf(prefix)
            if (idx >= 0) {
                text = text.substring(idx + prefix.length)
                break
            }
        }
        text = text
            .replace(Regex("(?i)\\b(at|in|on)\\s+\\d{1,2}(:\\d{2})?\\s*(am|pm)?.*$"), "")
            .replace(Regex("(?i)\\b(tomorrow|tonight|today|next week|every day|daily|weekly|weekdays|hourly)\\b"), "")
            .replace(Regex("(?i)\\b(in)\\s+(a|an|\\d+)\\s*(minute|min|hour|hr)s?\\b.*$"), "")
            .trim()
            .trim('.', ',', '!', '?')

        return text.replaceFirstChar { it.uppercase() }
    }

    private companion object {
        val SUBJECT_PREFIXES = listOf(
            "remind me to ", "remind me ", "reminder to ", "reminder ",
            "schedule a ", "schedule ", "add event ", "event ",
            "meeting with ", "meeting ", "appointment ",
            "set an alarm for ", "set alarm for ", "alarm for ", "wake me up for ", "wake me for "
        )
    }
}
