package com.mindecho.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a voice-captured task or idea in the MindEcho local database.
 *
 * Indices are placed on [triggerTimestamp] and [createdAt] to ensure ultra-fast
 * lookups for scheduled alarms and rolling calendar date filters on Snapdragon 8 Gen 3.
 */
@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["triggerTimestamp"], name = "idx_task_trigger_timestamp"),
        Index(value = ["createdAt"], name = "idx_task_created_at")
    ]
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "raw_sentence")
    val rawSentence: String,

    @ColumnInfo(name = "cleaned_title")
    val cleanedTitle: String,

    @ColumnInfo(name = "trigger_timestamp")
    val triggerTimestamp: Long = 0L,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false
) {
    val isScheduled: Boolean
        get() = triggerTimestamp > 0L

    fun isExpired(currentEpochMs: Long = System.currentTimeMillis()): Boolean {
        return isScheduled && triggerTimestamp <= currentEpochMs
    }
}
