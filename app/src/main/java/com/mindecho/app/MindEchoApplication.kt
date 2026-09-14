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

        // Initialize local Room database singleton
        AppDatabase.getDatabase(this)

        // Ensure 24-hour rolling retention worker is active
        DailyCleanupWorker.schedule(this)
    }
}
