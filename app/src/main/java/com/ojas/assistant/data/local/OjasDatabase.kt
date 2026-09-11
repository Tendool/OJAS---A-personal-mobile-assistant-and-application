package com.ojas.assistant.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ojas.assistant.data.local.dao.AlarmDao
import com.ojas.assistant.data.local.dao.CalendarDao
import com.ojas.assistant.data.local.dao.MeditationDao
import com.ojas.assistant.data.local.dao.ReminderDao
import com.ojas.assistant.data.local.dao.ScreenTimeDao
import com.ojas.assistant.data.local.dao.WaterDao
import com.ojas.assistant.data.local.dao.WorkoutDao
import com.ojas.assistant.data.local.entity.AlarmEntity
import com.ojas.assistant.data.local.entity.AppLimitEntity
import com.ojas.assistant.data.local.entity.CalendarEventEntity
import com.ojas.assistant.data.local.entity.ExerciseEntity
import com.ojas.assistant.data.local.entity.FocusSessionEntity
import com.ojas.assistant.data.local.entity.MeditationSessionEntity
import com.ojas.assistant.data.local.entity.ReminderEntity
import com.ojas.assistant.data.local.entity.ScreenTimeDayEntity
import com.ojas.assistant.data.local.entity.WaterLogEntity
import com.ojas.assistant.data.local.entity.WorkoutEntity
import com.ojas.assistant.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Database(
    entities = [
        WaterLogEntity::class,
        WorkoutEntity::class,
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        MeditationSessionEntity::class,
        AlarmEntity::class,
        ReminderEntity::class,
        CalendarEventEntity::class,
        AppLimitEntity::class,
        ScreenTimeDayEntity::class,
        FocusSessionEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class OjasDatabase : RoomDatabase() {

    abstract fun waterDao(): WaterDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun meditationDao(): MeditationDao
    abstract fun alarmDao(): AlarmDao
    abstract fun reminderDao(): ReminderDao
    abstract fun calendarDao(): CalendarDao
    abstract fun screenTimeDao(): ScreenTimeDao

    companion object {
        private const val NAME = "ojas.db"

        fun build(context: Context, scope: CoroutineScope): OjasDatabase {
            var instance: OjasDatabase? = null
            val db = Room.databaseBuilder(context.applicationContext, OjasDatabase::class.java, NAME)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Seeding runs off the creation transaction so the first frame
                        // is never blocked waiting on the starter workouts.
                        scope.launch { instance?.let { WorkoutSeed.seed(it.workoutDao()) } }
                    }
                })
                .build()
            instance = db
            return db
        }
    }
}
