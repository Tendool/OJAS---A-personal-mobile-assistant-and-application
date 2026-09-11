package com.ojas.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ojas.assistant.data.local.entity.ExerciseEntity
import com.ojas.assistant.data.local.entity.MeditationSessionEntity
import com.ojas.assistant.data.local.entity.WaterLogEntity
import com.ojas.assistant.data.local.entity.WorkoutEntity
import com.ojas.assistant.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterDao {

    @Insert
    suspend fun insert(log: WaterLogEntity): Long

    @Delete
    suspend fun delete(log: WaterLogEntity)

    @Query("DELETE FROM water_log WHERE id = (SELECT id FROM water_log WHERE epochDay = :day ORDER BY loggedAt DESC LIMIT 1)")
    suspend fun undoLast(day: Long)

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE epochDay = :day")
    fun totalForDay(day: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(amountMl), 0) FROM water_log WHERE epochDay = :day")
    suspend fun totalForDayOnce(day: Long): Int

    @Query("SELECT * FROM water_log WHERE epochDay = :day ORDER BY loggedAt DESC")
    fun logsForDay(day: Long): Flow<List<WaterLogEntity>>

    @Query(
        """
        SELECT epochDay AS epochDay, SUM(amountMl) AS total
        FROM water_log
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY epochDay
        ORDER BY epochDay
        """
    )
    fun dailyTotals(from: Long, to: Long): Flow<List<DayTotal>>

    @Query("DELETE FROM water_log WHERE epochDay < :beforeDay")
    suspend fun prune(beforeDay: Long): Int
}

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workout ORDER BY builtIn DESC, name COLLATE NOCASE")
    fun observeWorkouts(): Flow<List<WorkoutWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout WHERE id = :id")
    fun observeWorkout(id: Long): Flow<WorkoutWithExercises?>

    @Transaction
    @Query("SELECT * FROM workout WHERE id = :id")
    suspend fun getWorkout(id: Long): WorkoutWithExercises?

    @Query("SELECT COUNT(*) FROM workout")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: WorkoutEntity): Long

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workout WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercise WHERE workoutId = :workoutId")
    suspend fun clearExercises(workoutId: Long)

    @Transaction
    suspend fun replaceExercises(workoutId: Long, exercises: List<ExerciseEntity>) {
        clearExercises(workoutId)
        insertExercises(exercises.mapIndexed { i, e -> e.copy(id = 0, workoutId = workoutId, position = i) })
    }

    // --- sessions

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM workout_session ORDER BY startedAt DESC LIMIT :limit")
    fun recentSessions(limit: Int): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT COUNT(*) FROM workout_session WHERE epochDay = :day")
    fun sessionCountForDay(day: Long): Flow<Int>

    @Query("SELECT DISTINCT epochDay FROM workout_session WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC")
    fun activeDays(from: Long, to: Long): Flow<List<Long>>

    @Query(
        """
        SELECT epochDay AS epochDay, SUM(activeSeconds) / 60 AS total
        FROM workout_session
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY epochDay
        ORDER BY epochDay
        """
    )
    fun dailyMinutes(from: Long, to: Long): Flow<List<DayTotal>>
}

@Dao
interface MeditationDao {

    @Insert
    suspend fun insert(session: MeditationSessionEntity): Long

    @Query("SELECT * FROM meditation_session ORDER BY startedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<MeditationSessionEntity>>

    @Query("SELECT COALESCE(SUM(actualSeconds), 0) FROM meditation_session WHERE epochDay = :day")
    fun secondsForDay(day: Long): Flow<Int>

    @Query("SELECT DISTINCT epochDay FROM meditation_session WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC")
    fun activeDays(from: Long, to: Long): Flow<List<Long>>

    @Query(
        """
        SELECT epochDay AS epochDay, SUM(actualSeconds) / 60 AS total
        FROM meditation_session
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY epochDay
        ORDER BY epochDay
        """
    )
    fun dailyMinutes(from: Long, to: Long): Flow<List<DayTotal>>

    @Query("SELECT COALESCE(SUM(actualSeconds), 0) / 60 FROM meditation_session")
    fun lifetimeMinutes(): Flow<Int>
}
