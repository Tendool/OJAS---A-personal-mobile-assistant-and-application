package com.ojas.assistant.data.repository

import com.ojas.assistant.core.TimeUtils
import com.ojas.assistant.data.local.dao.DayTotal
import com.ojas.assistant.data.local.dao.MeditationDao
import com.ojas.assistant.data.local.dao.WorkoutDao
import com.ojas.assistant.data.local.dao.WorkoutWithExercises
import com.ojas.assistant.data.local.entity.ExerciseEntity
import com.ojas.assistant.data.local.entity.MeditationSessionEntity
import com.ojas.assistant.data.local.entity.WorkoutEntity
import com.ojas.assistant.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WorkoutRepository(private val dao: WorkoutDao) {

    fun observeWorkouts(): Flow<List<WorkoutWithExercises>> = dao.observeWorkouts()

    fun observeWorkout(id: Long): Flow<WorkoutWithExercises?> = dao.observeWorkout(id)

    suspend fun getWorkout(id: Long): WorkoutWithExercises? = dao.getWorkout(id)

    fun recentSessions(limit: Int = 30): Flow<List<WorkoutSessionEntity>> = dao.recentSessions(limit)

    fun sessionsToday(): Flow<Int> = dao.sessionCountForDay(TimeUtils.todayEpochDay())

    fun lastDays(count: Int): Flow<List<DayTotal>> {
        val today = TimeUtils.todayEpochDay()
        return dao.dailyMinutes(today - (count - 1), today)
    }

    fun streak(): Flow<Int> {
        val today = TimeUtils.todayEpochDay()
        return dao.activeDays(today - 400, today).map { TimeUtils.currentStreak(it, today) }
    }

    suspend fun saveWorkout(workout: WorkoutEntity, exercises: List<ExerciseEntity>): Long {
        val id = if (workout.id == 0L) {
            dao.insertWorkout(workout)
        } else {
            dao.updateWorkout(workout)
            workout.id
        }
        dao.replaceExercises(id, exercises)
        return id
    }

    suspend fun deleteWorkout(id: Long) = dao.deleteWorkout(id)

    suspend fun recordSession(
        workoutId: Long,
        workoutName: String,
        startedAt: Long,
        endedAt: Long,
        completedSets: Int,
        totalSets: Int,
        activeSeconds: Int
    ) {
        dao.insertSession(
            WorkoutSessionEntity(
                workoutId = workoutId,
                workoutName = workoutName,
                startedAt = startedAt,
                endedAt = endedAt,
                epochDay = TimeUtils.epochDayOf(startedAt),
                completedSets = completedSets,
                totalSets = totalSets,
                activeSeconds = activeSeconds
            )
        )
    }
}

class MeditationRepository(private val dao: MeditationDao) {

    fun recent(limit: Int = 30): Flow<List<MeditationSessionEntity>> = dao.recent(limit)

    fun secondsToday(): Flow<Int> = dao.secondsForDay(TimeUtils.todayEpochDay())

    fun lifetimeMinutes(): Flow<Int> = dao.lifetimeMinutes()

    fun lastDays(count: Int): Flow<List<DayTotal>> {
        val today = TimeUtils.todayEpochDay()
        return dao.dailyMinutes(today - (count - 1), today)
    }

    fun streak(): Flow<Int> {
        val today = TimeUtils.todayEpochDay()
        return dao.activeDays(today - 400, today).map { TimeUtils.currentStreak(it, today) }
    }

    suspend fun record(
        presetKey: String,
        presetName: String,
        startedAt: Long,
        plannedSeconds: Int,
        actualSeconds: Int
    ) {
        // Anything past three quarters of the plan counts as a finished sit; bailing at
        // minute nine of ten should not read as a failure.
        val completed = actualSeconds >= (plannedSeconds * 3) / 4
        dao.insert(
            MeditationSessionEntity(
                presetKey = presetKey,
                presetName = presetName,
                startedAt = startedAt,
                epochDay = TimeUtils.epochDayOf(startedAt),
                plannedSeconds = plannedSeconds,
                actualSeconds = actualSeconds,
                completed = completed
            )
        )
    }
}
