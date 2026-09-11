package com.ojas.assistant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.ojas.assistant.data.local.entity.AlarmEntity
import com.ojas.assistant.data.local.entity.AppLimitEntity
import com.ojas.assistant.data.local.entity.CalendarEventEntity
import com.ojas.assistant.data.local.entity.FocusSessionEntity
import com.ojas.assistant.data.local.entity.ReminderEntity
import com.ojas.assistant.data.local.entity.ScreenTimeDayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarm ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarm WHERE enabled = 1")
    suspend fun allEnabled(): List<AlarmEntity>

    @Query("SELECT * FROM alarm WHERE id = :id")
    suspend fun byId(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Query("UPDATE alarm SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE alarm SET skipUntil = :until WHERE id = :id")
    suspend fun setSkipUntil(id: Long, until: Long)

    @Query("DELETE FROM alarm WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminder ORDER BY enabled DESC, triggerAt")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE enabled = 1 AND triggerAt BETWEEN :from AND :to ORDER BY triggerAt LIMIT :limit")
    fun observeUpcoming(from: Long, to: Long, limit: Int): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE enabled = 1")
    suspend fun allEnabled(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun byId(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("UPDATE reminder SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM reminder WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminder WHERE repeat = 'NONE' AND enabled = 0 AND triggerAt < :before")
    suspend fun pruneFinished(before: Long): Int
}

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendar_event WHERE epochDay BETWEEN :from AND :to ORDER BY startAt")
    fun observeRange(from: Long, to: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_event WHERE epochDay = :day ORDER BY allDay DESC, startAt")
    fun observeDay(day: Long): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_event WHERE startAt >= :from ORDER BY startAt LIMIT :limit")
    fun observeUpcoming(from: Long, limit: Int): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_event WHERE reminderMinutesBefore >= 0 AND startAt >= :from")
    suspend fun withAlertsAfter(from: Long): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_event WHERE id = :id")
    suspend fun byId(id: Long): CalendarEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEventEntity): Long

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_event WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ScreenTimeDao {

    @Query("SELECT * FROM app_limit ORDER BY appLabel COLLATE NOCASE")
    fun observeLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limit WHERE enabled = 1")
    suspend fun activeLimits(): List<AppLimitEntity>

    @Upsert
    suspend fun upsertLimit(limit: AppLimitEntity)

    @Query("DELETE FROM app_limit WHERE packageName = :pkg")
    suspend fun deleteLimit(pkg: String)

    @Upsert
    suspend fun upsertDay(row: ScreenTimeDayEntity)

    @Upsert
    suspend fun upsertDays(rows: List<ScreenTimeDayEntity>)

    @Query("SELECT * FROM screen_time_day WHERE epochDay = :day ORDER BY minutes DESC")
    fun observeDay(day: Long): Flow<List<ScreenTimeDayEntity>>

    @Query(
        """
        SELECT epochDay AS epochDay, SUM(minutes) AS total
        FROM screen_time_day
        WHERE epochDay BETWEEN :from AND :to
        GROUP BY epochDay
        ORDER BY epochDay
        """
    )
    fun dailyTotals(from: Long, to: Long): Flow<List<DayTotal>>

    @Query("DELETE FROM screen_time_day WHERE epochDay < :beforeDay")
    suspend fun prune(beforeDay: Long): Int

    // --- focus sessions

    @Insert
    suspend fun insertFocus(session: FocusSessionEntity): Long

    @Query("SELECT * FROM focus_session ORDER BY startedAt DESC LIMIT :limit")
    fun recentFocus(limit: Int): Flow<List<FocusSessionEntity>>

    @Query("SELECT COALESCE(SUM((endedAt - startedAt) / 60000), 0) FROM focus_session WHERE epochDay = :day AND completed = 1")
    fun focusMinutesForDay(day: Long): Flow<Int>
}
