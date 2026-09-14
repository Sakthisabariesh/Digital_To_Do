package com.mindecho.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [TaskEntity].
 * Provides optimized Room queries for calendar-day filtering, exact alarm restoration,
 * and automated rolling 6-day cutoff deletion.
 */
@Dao
interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: Long): TaskEntity?

    /**
     * Emits tasks created within the specified epoch millisecond calendar window (e.g. Day Start to Day End).
     * Results are ordered chronologically with incomplete tasks prioritized.
     */
    @Query("""
        SELECT * FROM tasks 
        WHERE created_at >= :startOfDay AND created_at <= :endOfDay 
        ORDER BY is_completed ASC, created_at DESC
    """)
    fun getTasksForDay(startOfDay: Long, endOfDay: Long): Flow<List<TaskEntity>>

    /**
     * Retrieves all pending, uncompleted scheduled tasks with triggers in the future.
     * Essential for re-registering exact alarms with AlarmManager on device boot.
     */
    @Query("""
        SELECT * FROM tasks 
        WHERE trigger_timestamp > :now 
          AND is_completed = 0 
        ORDER BY trigger_timestamp ASC
    """)
    suspend fun getPendingTasks(now: Long): List<TaskEntity>

    /**
     * Purges stale tasks older than the specified epoch millisecond cutoff.
     * Used by DailyCleanupWorker for maintaining the rolling 6-day retention window.
     */
    @Query("DELETE FROM tasks WHERE created_at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    /**
     * Quick status toggle for task completion directly by ID.
     */
    @Query("UPDATE tasks SET is_completed = :isCompleted WHERE id = :id")
    suspend fun setTaskCompletion(id: Long, isCompleted: Boolean): Int

    /**
     * Update trigger timestamp (used when snoozing a task).
     */
    @Query("UPDATE tasks SET trigger_timestamp = :newTriggerEpochMs WHERE id = :id")
    suspend fun updateTriggerTimestamp(id: Long, newTriggerEpochMs: Long): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long): Int

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTotalTaskCount(): Int
}
