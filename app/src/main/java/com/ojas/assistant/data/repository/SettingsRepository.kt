package com.ojas.assistant.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ojas_settings")

/** Every user-tunable knob in one immutable snapshot. */
data class OjasSettings(
    val userName: String = "",
    val onboarded: Boolean = false,
    val use24h: Boolean = false,
    val hapticsEnabled: Boolean = true,

    val waterGoalMl: Int = 2500,
    val waterCupMl: Int = 250,
    val waterRemindersEnabled: Boolean = true,
    val waterIntervalMinutes: Int = 90,
    val waterWindowStartHour: Int = 8,
    val waterWindowEndHour: Int = 22,

    val meditationDefaultMinutes: Int = 10,
    val meditationPresetKey: String = "box",
    val meditationBell: Boolean = true,

    val workoutRestCue: Boolean = true,

    val starCount: Int = 6000,
    val reduceMotion: Boolean = false,
    val galaxyInteractive: Boolean = true,

    val screenTimeEnabled: Boolean = false,
    val screenTimeGoalMinutes: Int = 240,
    val focusDefaultMinutes: Int = 25
) {
    val cupsToGoal: Int get() = if (waterCupMl <= 0) 0 else (waterGoalMl + waterCupMl - 1) / waterCupMl
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val userName = stringPreferencesKey("user_name")
        val onboarded = booleanPreferencesKey("onboarded")
        val use24h = booleanPreferencesKey("use_24h")
        val haptics = booleanPreferencesKey("haptics")

        val waterGoal = intPreferencesKey("water_goal_ml")
        val waterCup = intPreferencesKey("water_cup_ml")
        val waterReminders = booleanPreferencesKey("water_reminders")
        val waterInterval = intPreferencesKey("water_interval_min")
        val waterStart = intPreferencesKey("water_window_start")
        val waterEnd = intPreferencesKey("water_window_end")

        val medMinutes = intPreferencesKey("med_default_minutes")
        val medPreset = stringPreferencesKey("med_preset")
        val medBell = booleanPreferencesKey("med_bell")

        val restCue = booleanPreferencesKey("workout_rest_cue")

        val starCount = intPreferencesKey("galaxy_star_count")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
        val galaxyInteractive = booleanPreferencesKey("galaxy_interactive")

        val screenTimeEnabled = booleanPreferencesKey("screen_time_enabled")
        val screenTimeGoal = intPreferencesKey("screen_time_goal_min")
        val focusMinutes = intPreferencesKey("focus_default_minutes")

        val lastWaterNudge = longPreferencesKey("last_water_nudge")
    }

    val settings: Flow<OjasSettings> = context.dataStore.data
        .catch { e ->
            // A corrupt or unreadable preferences file must never take the app down;
            // fall back to defaults and let the user re-save.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { p ->
        val d = OjasSettings()
        OjasSettings(
            userName = p[Keys.userName] ?: d.userName,
            onboarded = p[Keys.onboarded] ?: d.onboarded,
            use24h = p[Keys.use24h] ?: d.use24h,
            hapticsEnabled = p[Keys.haptics] ?: d.hapticsEnabled,
            waterGoalMl = p[Keys.waterGoal] ?: d.waterGoalMl,
            waterCupMl = p[Keys.waterCup] ?: d.waterCupMl,
            waterRemindersEnabled = p[Keys.waterReminders] ?: d.waterRemindersEnabled,
            waterIntervalMinutes = p[Keys.waterInterval] ?: d.waterIntervalMinutes,
            waterWindowStartHour = p[Keys.waterStart] ?: d.waterWindowStartHour,
            waterWindowEndHour = p[Keys.waterEnd] ?: d.waterWindowEndHour,
            meditationDefaultMinutes = p[Keys.medMinutes] ?: d.meditationDefaultMinutes,
            meditationPresetKey = p[Keys.medPreset] ?: d.meditationPresetKey,
            meditationBell = p[Keys.medBell] ?: d.meditationBell,
            workoutRestCue = p[Keys.restCue] ?: d.workoutRestCue,
            starCount = p[Keys.starCount] ?: d.starCount,
            reduceMotion = p[Keys.reduceMotion] ?: d.reduceMotion,
            galaxyInteractive = p[Keys.galaxyInteractive] ?: d.galaxyInteractive,
            screenTimeEnabled = p[Keys.screenTimeEnabled] ?: d.screenTimeEnabled,
            screenTimeGoalMinutes = p[Keys.screenTimeGoal] ?: d.screenTimeGoalMinutes,
            focusDefaultMinutes = p[Keys.focusMinutes] ?: d.focusDefaultMinutes
        )
    }

    suspend fun setName(value: String) = put(Keys.userName, value.trim())
    suspend fun setOnboarded(value: Boolean) = put(Keys.onboarded, value)
    suspend fun setUse24h(value: Boolean) = put(Keys.use24h, value)
    suspend fun setHaptics(value: Boolean) = put(Keys.haptics, value)

    suspend fun setWaterGoal(ml: Int) = put(Keys.waterGoal, ml.coerceIn(250, 8000))
    suspend fun setWaterCup(ml: Int) = put(Keys.waterCup, ml.coerceIn(50, 1500))
    suspend fun setWaterReminders(value: Boolean) = put(Keys.waterReminders, value)
    suspend fun setWaterInterval(min: Int) = put(Keys.waterInterval, min.coerceIn(15, 480))
    suspend fun setWaterWindow(startHour: Int, endHour: Int) {
        context.dataStore.edit {
            it[Keys.waterStart] = startHour.coerceIn(0, 23)
            it[Keys.waterEnd] = endHour.coerceIn(1, 24)
        }
    }

    suspend fun setMeditationMinutes(min: Int) = put(Keys.medMinutes, min.coerceIn(1, 180))
    suspend fun setMeditationPreset(key: String) = put(Keys.medPreset, key)
    suspend fun setMeditationBell(value: Boolean) = put(Keys.medBell, value)

    suspend fun setRestCue(value: Boolean) = put(Keys.restCue, value)

    suspend fun setStarCount(count: Int) = put(Keys.starCount, count.coerceIn(1200, 14000))
    suspend fun setReduceMotion(value: Boolean) = put(Keys.reduceMotion, value)
    suspend fun setGalaxyInteractive(value: Boolean) = put(Keys.galaxyInteractive, value)

    suspend fun setScreenTimeEnabled(value: Boolean) = put(Keys.screenTimeEnabled, value)
    suspend fun setScreenTimeGoal(min: Int) = put(Keys.screenTimeGoal, min.coerceIn(30, 1440))
    suspend fun setFocusMinutes(min: Int) = put(Keys.focusMinutes, min.coerceIn(5, 240))

    suspend fun lastWaterNudge(): Long = readOnce(Keys.lastWaterNudge) ?: 0L
    suspend fun setLastWaterNudge(at: Long) = put(Keys.lastWaterNudge, at)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    private suspend fun <T> readOnce(key: Preferences.Key<T>): T? =
        context.dataStore.data.first()[key]
}
