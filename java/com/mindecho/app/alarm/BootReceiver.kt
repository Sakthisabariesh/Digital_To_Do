package com.mindecho.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mindecho.app.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles device boot and app update broadcasts to re-register pending exact alarms.
 *
 * Essential for Android 14+ since exact alarms scheduled with AlarmManager
 * are wiped when the device is powered off or rebooted.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val pendingResult = goAsync()
            val appContext = context.applicationContext

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(appContext)
                    val taskDao = db.taskDao()
                    val scheduler = AlarmScheduler(appContext)

                    val now = System.currentTimeMillis()
                    val pendingTasks = taskDao.getPendingTasks(now)
                    Log.d(TAG, "Restoring ${pendingTasks.size} pending alarms post-boot...")

                    for (task in pendingTasks) {
                        scheduler.schedule(task)
                    }
                    Log.d(TAG, "All pending alarms successfully restored.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error restoring alarms on boot", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
