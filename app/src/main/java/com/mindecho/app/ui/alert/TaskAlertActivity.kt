package com.mindecho.app.ui.alert

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.mindecho.app.alarm.AlarmReceiver
import com.mindecho.app.alarm.AlarmScheduler
import com.mindecho.app.data.local.AppDatabase
import com.mindecho.app.data.local.TaskEntity
import com.mindecho.app.ui.widget.MindEchoGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full-Screen Alert Activity that wakes the screen and displays over the keyguard/lock screen.
 *
 * Implements a dual-audio engine:
 * 1. Looping alarm audio stream (`AudioAttributes.USAGE_ALARM`).
 * 2. Android TextToSpeech speaking the task prompt with audio ducking.
 */
class TaskAlertActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TaskAlertActivity"
        private const val TTS_UTTERANCE_ID = "task_alert_speech_utterance"
    }

    private var taskId: Long = -1L
    private var taskTitle: String = ""
    private var rawSentence: String = ""

    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWake()

        taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L)
        taskTitle = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE) ?: "Task Reminder"
        rawSentence = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_RAW_SENTENCE) ?: taskTitle

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initVibrator()
        startAlarmAudio()
        initTextToSpeech()

        val timeFormatted = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEE, MMM d • h:mm a", Locale.getDefault()))

        setContent {
            TaskAlertScreen(
                cleanedTitle = taskTitle,
                rawSentence = rawSentence,
                currentTimeFormatted = timeFormatted,
                onCompleteClicked = { handleCompleteTask() },
                onSnoozeClicked = { handleSnoozeTask() }
            )
        }
    }

    private fun configureLockScreenWake() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager?.requestDismissKeyguard(this, null)
        }
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(AlarmReceiver.VIBRATION_PATTERN, 0)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(AlarmReceiver.VIBRATION_PATTERN, 0)
            }
        }
    }

    private fun startAlarmAudio() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            requestSystemAudioFocus()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.d(TAG, "Alarm MediaPlayer started.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start alarm MediaPlayer", e)
        }
    }

    private fun requestSystemAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()

            audioManager?.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    mediaPlayer?.setVolume(0.15f, 0.15f)
                }

                override fun onDone(utteranceId: String?) {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mediaPlayer?.setVolume(1.0f, 1.0f)
                }
            })

            val speechText = "You have a task: $taskTitle"
            tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, TTS_UTTERANCE_ID)
        } else {
            Log.w(TAG, "TTS Initialization failed with status: $status")
        }
    }

    private fun handleCompleteTask() {
        Log.d(TAG, "Marking task $taskId as complete.")
        lifecycleScope.launch(Dispatchers.IO) {
            if (taskId > 0) {
                val db = AppDatabase.getDatabase(applicationContext)
                db.taskDao().setTaskCompletion(taskId, true)
            }
            // Update Glance Widget
            MindEchoGlanceWidget.refreshAll(applicationContext)
        }
        teardownAndFinish()
    }

    private fun handleSnoozeTask() {
        val snoozeEpochMs = System.currentTimeMillis() + (5 * 60 * 1000L)
        Log.d(TAG, "Snoozing task $taskId for 5 minutes until $snoozeEpochMs")

        lifecycleScope.launch(Dispatchers.IO) {
            if (taskId > 0) {
                val db = AppDatabase.getDatabase(applicationContext)
                db.taskDao().updateTriggerTimestamp(taskId, snoozeEpochMs)

                val snoozedTask = TaskEntity(
                    id = taskId,
                    rawSentence = rawSentence,
                    cleanedTitle = taskTitle,
                    triggerTimestamp = snoozeEpochMs,
                    isCompleted = false
                )
                AlarmScheduler(applicationContext).schedule(snoozedTask)
            }
            // Update Glance Widget
            MindEchoGlanceWidget.refreshAll(applicationContext)
        }
        teardownAndFinish()
    }

    private fun cancelAlarmNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = if (taskId > 0) taskId.toInt() else System.currentTimeMillis().toInt()
        notificationManager.cancel(notificationId)
    }

    private fun teardownAndFinish() {
        cancelAlarmNotification()
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        super.onDestroy()
        vibrator?.cancel()

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        }

        try {
            tts?.let {
                it.stop()
                it.shutdown()
            }
            tts = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }
}
