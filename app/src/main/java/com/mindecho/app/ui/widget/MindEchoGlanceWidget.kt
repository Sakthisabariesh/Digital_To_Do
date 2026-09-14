package com.mindecho.app.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mindecho.app.MainActivity
import com.mindecho.app.data.local.AppDatabase
import com.mindecho.app.data.local.TaskEntity
import com.mindecho.app.data.util.TaskDateUtils
import com.mindecho.app.nlp.TimeIntentParser
import kotlinx.coroutines.flow.firstOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Ultra-lightweight, battery-efficient Jetpack Glance Home Screen Widget for MindEcho.
 * Offers 1-tap immediate voice capture and pending task statistics.
 */
class MindEchoGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getDatabase(context)
        val taskDao = db.taskDao()

        val startOfDay = TaskDateUtils.getStartOfDayEpochMs(0L)
        val endOfDay = TaskDateUtils.getEndOfDayEpochMs(0L)
        val todayTasks = taskDao.getTasksForDay(startOfDay, endOfDay).firstOrNull() ?: emptyList()

        val pendingTasksCount = todayTasks.count { !it.isCompleted }
        val nextScheduledTask = todayTasks
            .filter { !it.isCompleted && it.triggerTimestamp > System.currentTimeMillis() }
            .minByOrNull { it.triggerTimestamp }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    context = context,
                    pendingCount = pendingTasksCount,
                    nextTask = nextScheduledTask
                )
            }
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            try {
                MindEchoGlanceWidget().updateAll(context)
            } catch (e: Exception) {
                // Ignore widget update race conditions
            }
        }
    }
}

@Composable
private fun WidgetContent(
    context: Context,
    pendingCount: Int,
    nextTask: TaskEntity?
) {
    val voiceLaunchIntent = Intent(context, MainActivity::class.java).apply {
        action = "com.mindecho.app.ACTION_TRIGGER_VOICE"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }

    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    val nextReminderStr = if (nextTask != null && nextTask.triggerTimestamp > 0L) {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nextTask.triggerTimestamp), ZoneId.systemDefault())
        dt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    } else {
        "No alarms"
    }

    // AMOLED Black Surface container
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0C0C0E)))
            .cornerRadius(18.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(openAppIntent))
    ) {
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Left Information Column
            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // System Monospace Tag
                Text(
                    text = "[MINDECHO OFFLINE]",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF64B5F6)),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Pending Task Summary
                Text(
                    text = if (pendingCount == 1) "1 Pending Task" else "$pendingCount Pending Tasks",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFF5F5F7)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Next Alarm Chip
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏰ Next: $nextReminderStr",
                        style = TextStyle(
                            color = ColorProvider(if (nextTask != null) Color(0xFF81C784) else Color(0xFF8E8E93)),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            // Right 1-Tap Instant Voice Trigger Button
            Box(
                modifier = GlanceModifier
                    .size(52.dp)
                    .background(ColorProvider(Color(0xFF64B5F6)))
                    .cornerRadius(26.dp)
                    .clickable(actionStartActivity(voiceLaunchIntent)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MIC",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF0A1929)),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
