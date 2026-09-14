package com.mindecho.app

import android.app.Application
import android.util.Log
import com.mindecho.app.data.local.AppDatabase
import com.mindecho.app.worker.DailyCleanupWorker

/**
 * Application class for MindEcho. Initializes database and periodic 6-day retention cleanup.
 */
class MindEchoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("MindEchoApp", "Initializing MindEcho Application...")

        AppDatabase.getDatabase(this)
        DailyCleanupWorker.schedule(this)
    }
}
