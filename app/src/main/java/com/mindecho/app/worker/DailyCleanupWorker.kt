package com.mindecho.app.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mindecho.app.data.local.AppDatabase
import com.mindecho.app.data.util.TaskDateUtils
import com.mindecho.app.ui.widget.MindEchoGlanceWidget
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker responsible for maintaining the rolling 6-day retention window.
 *
 * Runs once every 24 hours to automatically purge all tasks created before the 6th-day
 * calendar cutoff (Today minus 5 full days at midnight).
 */
class DailyCleanupWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "DailyCleanupWorker"
        const val UNIQUE_WORK_NAME = "mindecho_daily_cleanup_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<DailyCleanupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "DailyCleanupWorker periodic job enqueued with 24-hour interval.")
        }

        fun enqueuePeriodicCleanup(context: Context) {
            schedule(context)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting 6-day rolling retention purge...")
        return try {
            val db = AppDatabase.getDatabase(appContext)
            val taskDao = db.taskDao()

            val cutoffMillis = TaskDateUtils.getSixDayPurgeCutoff()
            val deletedCount = taskDao.deleteOlderThan(cutoffMillis)

            Log.d(TAG, "Cleanup completed: $deletedCount stale tasks older than cutoff ($cutoffMillis) purged.")

            // Refresh Glance Home Widget
            MindEchoGlanceWidget.refreshAll(appContext)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error during daily task cleanup worker execution", e)
            Result.retry()
        }
    }
}
