package com.ojas.assistant.di

import android.content.Context
import com.ojas.assistant.OjasApp
import com.ojas.assistant.alarm.OjasAlarmScheduler
import com.ojas.assistant.assistant.OjasBrain
import com.ojas.assistant.data.local.OjasDatabase
import com.ojas.assistant.data.repository.AlarmRepository
import com.ojas.assistant.data.repository.CalendarRepository
import com.ojas.assistant.data.repository.MeditationRepository
import com.ojas.assistant.data.repository.ReminderRepository
import com.ojas.assistant.data.repository.SettingsRepository
import com.ojas.assistant.data.repository.WaterRepository
import com.ojas.assistant.data.repository.WorkoutRepository
import com.ojas.assistant.screentime.ScreenTimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled dependency graph.
 *
 * Ojas has one process, one database and a handful of singletons, so a lazy container
 * beats an annotation processor here: nothing is constructed until something asks for
 * it, which keeps cold start down to inflating the first frame.
 */
class AppContainer(context: Context) {

    private val app = context.applicationContext

    /** Outlives any single screen; used for writes triggered from receivers and services. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: OjasDatabase by lazy { OjasDatabase.build(app, appScope) }

    val settings: SettingsRepository by lazy { SettingsRepository(app) }

    val scheduler: OjasAlarmScheduler by lazy { OjasAlarmScheduler(app) }

    val water: WaterRepository by lazy { WaterRepository(database.waterDao(), settings, scheduler) }

    val workouts: WorkoutRepository by lazy { WorkoutRepository(database.workoutDao()) }

    val meditation: MeditationRepository by lazy { MeditationRepository(database.meditationDao()) }

    val alarms: AlarmRepository by lazy { AlarmRepository(database.alarmDao(), scheduler) }

    val reminders: ReminderRepository by lazy { ReminderRepository(database.reminderDao(), scheduler) }

    val calendar: CalendarRepository by lazy { CalendarRepository(database.calendarDao(), scheduler) }

    val screenTime: ScreenTimeRepository by lazy {
        ScreenTimeRepository(app, database.screenTimeDao())
    }

    val brain: OjasBrain by lazy {
        OjasBrain(
            water = water,
            workouts = workouts,
            meditation = meditation,
            alarms = alarms,
            reminders = reminders,
            calendar = calendar,
            settings = settings
        )
    }
}

/** Convenience accessor so receivers and composables can reach the graph from a Context. */
val Context.ojas: AppContainer
    get() = (applicationContext as OjasApp).container
