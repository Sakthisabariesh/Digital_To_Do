package com.mindecho.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mindecho.app.data.local.TaskEntity

/**
 * Robust scheduler for exact task alarms on Android 14+ (SDK 34).
 * Handles exact alarm permission checks and PendingIntent construction.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AlarmScheduler"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_TASK_RAW_SENTENCE = "extra_task_raw_sentence"
        const val EXTRA_TRIGGER_TIMESTAMP = "extra_trigger_timestamp"
    }

    fun schedule(task: TaskEntity) {
        if (task.triggerTimestamp <= System.currentTimeMillis()) {
            Log.w(TAG, "Task ${task.id} has trigger timestamp in the past, skipping alarm scheduling.")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TASK_TITLE, task.cleanedTitle)
            putExtra(EXTRA_TASK_RAW_SENTENCE, task.rawSentence)
            putExtra(EXTRA_TRIGGER_TIMESTAMP, task.triggerTimestamp)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        task.triggerTimestamp,
                        pendingIntent
                    )
                    Log.d(TAG, "Exact alarm scheduled for task ${task.id} at ${task.triggerTimestamp}")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        task.triggerTimestamp,
                        pendingIntent
                    )
                    Log.w(TAG, "Exact alarm permission missing. Inexact alarm scheduled for task ${task.id}")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    task.triggerTimestamp,
                    pendingIntent
                )
                Log.d(TAG, "Exact alarm scheduled for task ${task.id} at ${task.triggerTimestamp}")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while scheduling exact alarm for task ${task.id}", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                task.triggerTimestamp,
                pendingIntent
            )
        }
    }

    fun cancel(taskId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Alarm cancelled for task $taskId")
    }
}
