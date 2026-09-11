package com.ojas.assistant.data.local

import com.ojas.assistant.data.local.dao.WorkoutDao
import com.ojas.assistant.data.local.entity.ExerciseEntity
import com.ojas.assistant.data.local.entity.WorkoutEntity

/**
 * Starter routines written into the database the first time it is created, so a fresh
 * install has something to do on day one. They are ordinary rows — editable and
 * deletable like anything the user creates.
 */
internal object WorkoutSeed {

    private data class Move(
        val name: String,
        val sets: Int = 3,
        val reps: Int = 12,
        val seconds: Int = 0,
        val rest: Int = 45,
        val note: String = ""
    )

    private val plans: List<Triple<String, Pair<String, String>, List<Move>>> = listOf(
        Triple(
            "Orbit Reset", "Full body" to "nebula",
            listOf(
                Move("Jumping jacks", sets = 2, reps = 0, seconds = 45, rest = 20, note = "Wake the system up"),
                Move("Bodyweight squats", sets = 3, reps = 15, rest = 45),
                Move("Push-ups", sets = 3, reps = 12, rest = 60, note = "Knees down is a full rep"),
                Move("Reverse lunges", sets = 3, reps = 10, rest = 45, note = "Per leg"),
                Move("Plank", sets = 3, reps = 0, seconds = 40, rest = 40),
                Move("Dead bug", sets = 2, reps = 12, rest = 30)
            )
        ),
        Triple(
            "Core Collapse", "Core" to "ember",
            listOf(
                Move("Hollow hold", sets = 3, reps = 0, seconds = 30, rest = 40),
                Move("Bicycle crunches", sets = 3, reps = 20, rest = 35),
                Move("Side plank", sets = 2, reps = 0, seconds = 35, rest = 30, note = "Per side"),
                Move("Leg raises", sets = 3, reps = 12, rest = 40),
                Move("Superman hold", sets = 3, reps = 0, seconds = 25, rest = 30)
            )
        ),
        Triple(
            "Solar Flare", "Conditioning" to "solar",
            listOf(
                Move("High knees", sets = 4, reps = 0, seconds = 30, rest = 25),
                Move("Burpees", sets = 4, reps = 8, rest = 50),
                Move("Mountain climbers", sets = 4, reps = 0, seconds = 30, rest = 30),
                Move("Squat jumps", sets = 3, reps = 12, rest = 45),
                Move("Slow breathing", sets = 1, reps = 0, seconds = 60, rest = 0, note = "Cool down")
            )
        ),
        Triple(
            "Gravity Well", "Strength" to "violet",
            listOf(
                Move("Pike push-ups", sets = 4, reps = 8, rest = 70),
                Move("Bulgarian split squat", sets = 3, reps = 10, rest = 60, note = "Per leg"),
                Move("Glute bridge", sets = 3, reps = 15, rest = 45),
                Move("Chair dips", sets = 3, reps = 12, rest = 50),
                Move("Calf raises", sets = 3, reps = 20, rest = 30)
            )
        ),
        Triple(
            "Zero-G Mobility", "Mobility" to "aurora",
            listOf(
                Move("Cat-cow", sets = 2, reps = 0, seconds = 45, rest = 15),
                Move("World's greatest stretch", sets = 2, reps = 0, seconds = 60, rest = 20, note = "Per side"),
                Move("Thoracic rotations", sets = 2, reps = 10, rest = 20),
                Move("Hip 90/90 switch", sets = 2, reps = 0, seconds = 50, rest = 20),
                Move("Hamstring scoop", sets = 2, reps = 12, rest = 20),
                Move("Child's pose", sets = 1, reps = 0, seconds = 60, rest = 0)
            )
        )
    )

    suspend fun seed(dao: WorkoutDao) {
        if (dao.count() > 0) return
        plans.forEach { (name, meta, moves) ->
            val (focus, accent) = meta
            val id = dao.insertWorkout(
                WorkoutEntity(name = name, focus = focus, accentKey = accent, builtIn = true)
            )
            dao.insertExercises(
                moves.mapIndexed { index, m ->
                    ExerciseEntity(
                        workoutId = id,
                        position = index,
                        name = m.name,
                        sets = m.sets,
                        reps = m.reps,
                        durationSec = m.seconds,
                        restSec = m.rest,
                        note = m.note
                    )
                }
            )
        }
    }
}
