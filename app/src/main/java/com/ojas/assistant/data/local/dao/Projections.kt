package com.ojas.assistant.data.local.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.ojas.assistant.data.local.entity.ExerciseEntity
import com.ojas.assistant.data.local.entity.WorkoutEntity

/** Aggregate row used by every "last N days" strip chart in the app. */
data class DayTotal(
    val epochDay: Long,
    val total: Int
)

data class WorkoutWithExercises(
    @Embedded val workout: WorkoutEntity,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val exercises: List<ExerciseEntity>
) {
    val totalSets: Int get() = exercises.sumOf { it.sets }

    /** Rough wall-clock estimate: work time plus rest between every set. */
    val estimatedMinutes: Int
        get() = exercises.sumOf { ex ->
            val work = if (ex.isTimed) ex.durationSec else ex.reps * 3
            (work + ex.restSec) * ex.sets
        }.let { (it + 59) / 60 }
}
