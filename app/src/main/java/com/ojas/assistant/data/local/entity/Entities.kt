package com.ojas.assistant.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Every table carries a denormalised `epochDay` column wherever daily rollups are needed.
 * Grouping on an indexed integer keeps the "today" queries — which run on almost every
 * screen — off the timestamp-arithmetic path and lets SQLite answer them from the index.
 */

// ---------------------------------------------------------------- hydration

@Entity(
    tableName = "water_log",
    indices = [Index("epochDay"), Index("loggedAt")]
)
data class WaterLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loggedAt: Long,
    val epochDay: Long,
    val amountMl: Int
)

// ----------------------------------------------------------------- workouts

@Entity(tableName = "workout")
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val focus: String,
    val accentKey: String = "nebula",
    val builtIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workoutId")]
)
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val position: Int,
    val name: String,
    /** Number of sets; a pure-duration move (plank, hold) still uses at least one set. */
    val sets: Int = 3,
    /** Reps per set, or 0 when the move is timed. */
    val reps: Int = 12,
    /** Seconds per set, or 0 when the move is rep-counted. */
    val durationSec: Int = 0,
    val restSec: Int = 45,
    val note: String = ""
) {
    val isTimed: Boolean get() = durationSec > 0
}

@Entity(
    tableName = "workout_session",
    indices = [Index("epochDay"), Index("startedAt")]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val workoutName: String,
    val startedAt: Long,
    val endedAt: Long,
    val epochDay: Long,
    val completedSets: Int,
    val totalSets: Int,
    val activeSeconds: Int
)

// --------------------------------------------------------------- meditation

@Entity(
    tableName = "meditation_session",
    indices = [Index("epochDay"), Index("startedAt")]
)
data class MeditationSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetKey: String,
    val presetName: String,
    val startedAt: Long,
    val epochDay: Long,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val completed: Boolean
)

// ------------------------------------------------------------------- alarms

@Entity(tableName = "alarm")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    /** Bitmask, Monday = bit 0 … Sunday = bit 6. Zero means "fire once". */
    val daysMask: Int = 0,
    val enabled: Boolean = true,
    val vibrate: Boolean = true,
    val soundUri: String? = null,
    val snoozeMinutes: Int = 9,
    /** Fade the alarm in over ~30s instead of starting at full volume. */
    val gentleWake: Boolean = true,
    /** Set when the user skips a single occurrence of a repeating alarm. */
    val skipUntil: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

// ---------------------------------------------------------------- reminders

@Entity(tableName = "reminder", indices = [Index("triggerAt"), Index("enabled")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val triggerAt: Long,
    val repeat: RepeatRule = RepeatRule.NONE,
    val enabled: Boolean = true,
    val category: String = "general",
    val lastFiredAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RepeatRule(val label: String) {
    NONE("Once"),
    HOURLY("Every hour"),
    DAILY("Every day"),
    WEEKDAYS("Weekdays"),
    WEEKLY("Every week"),
    MONTHLY("Every month"),
    YEARLY("Every year")
}

// ----------------------------------------------------------------- calendar

@Entity(tableName = "calendar_event", indices = [Index("startAt"), Index("epochDay")])
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val startAt: Long,
    val endAt: Long,
    val epochDay: Long,
    val allDay: Boolean = false,
    val colorKey: String = "nebula",
    /** Minutes before `startAt` to alert, or -1 for no alert. */
    val reminderMinutesBefore: Int = 10,
    val repeat: RepeatRule = RepeatRule.NONE,
    val createdAt: Long = System.currentTimeMillis()
)

// -------------------------------------------------------------- screen time

@Entity(tableName = "app_limit")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean = true,
    /** When true Ojas puts a block screen in front of the app instead of only warning. */
    val hardBlock: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "screen_time_day", primaryKeys = ["epochDay", "packageName"])
data class ScreenTimeDayEntity(
    val epochDay: Long,
    val packageName: String,
    val appLabel: String,
    val minutes: Int
)

@Entity(tableName = "focus_session", indices = [Index("epochDay")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    val epochDay: Long,
    val plannedMinutes: Int,
    val completed: Boolean
)
