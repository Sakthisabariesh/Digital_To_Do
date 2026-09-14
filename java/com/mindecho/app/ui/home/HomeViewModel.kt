package com.mindecho.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mindecho.app.alarm.AlarmScheduler
import com.mindecho.app.data.local.AppDatabase
import com.mindecho.app.data.local.TaskEntity
import com.mindecho.app.ui.widget.MindEchoGlanceWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val taskDao = db.taskDao()
    private val alarmScheduler = AlarmScheduler(application)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val tasks: StateFlow<List<TaskEntity>> = _selectedDate.flatMapLatest { date ->
        val zoneId = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L

        taskDao.getTasksForDay(startOfDay, endOfDay)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun toggleTaskCompletion(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedStatus = !task.isCompleted
            taskDao.setTaskCompletion(task.id, updatedStatus)

            if (updatedStatus && task.isScheduled) {
                alarmScheduler.cancel(task.id)
            } else if (!updatedStatus && task.isScheduled && task.triggerTimestamp > System.currentTimeMillis()) {
                alarmScheduler.schedule(task.copy(isCompleted = false))
            }

            MindEchoGlanceWidget.refreshAll(getApplication())
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.deleteTaskById(task.id)
            if (task.isScheduled) {
                alarmScheduler.cancel(task.id)
            }

            MindEchoGlanceWidget.refreshAll(getApplication())
        }
    }

    fun saveVoiceTask(rawSentence: String, cleanedTitle: String, triggerTimestamp: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = TaskEntity(
                rawSentence = rawSentence,
                cleanedTitle = cleanedTitle,
                triggerTimestamp = triggerTimestamp,
                createdAt = System.currentTimeMillis(),
                isCompleted = false
            )

            val insertedId = taskDao.insertTask(entity)
            if (triggerTimestamp > System.currentTimeMillis()) {
                alarmScheduler.schedule(entity.copy(id = insertedId))
            }

            MindEchoGlanceWidget.refreshAll(getApplication())
        }
    }
}
