package com.example.smartalarmer.alarm

import android.content.Context
import android.content.Intent

internal enum class AlarmProgressEventType {
    VERIFIED_PROGRESS,
    INTERMEDIATE_TASK_COMPLETED
}

internal data class AlarmProgressEvent(
    val alarmId: Int,
    val taskIndex: Int,
    val type: AlarmProgressEventType,
    val progress: Float = 0f
)

internal object AlarmProgressContract {
    const val ACTION = "com.example.smartalarmer.action.ALARM_PROGRESS"

    private const val EXTRA_ALARM_ID = "alarm_progress_alarm_id"
    private const val EXTRA_TASK_INDEX = "alarm_progress_task_index"
    private const val EXTRA_EVENT_TYPE = "alarm_progress_event_type"
    private const val EXTRA_PROGRESS = "alarm_progress_value"

    fun intent(
        context: Context,
        event: AlarmProgressEvent
    ): Intent = Intent(ACTION)
        .setPackage(context.packageName)
        .putExtra(EXTRA_ALARM_ID, event.alarmId)
        .putExtra(EXTRA_TASK_INDEX, event.taskIndex)
        .putExtra(EXTRA_EVENT_TYPE, event.type.name)
        .putExtra(EXTRA_PROGRESS, event.progress)

    fun read(intent: Intent): AlarmProgressEvent? {
        if (intent.action != ACTION) return null
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, AlarmLaunchPayload.NO_ALARM_ID)
        if (alarmId == AlarmLaunchPayload.NO_ALARM_ID) return null
        val taskIndex = intent.getIntExtra(EXTRA_TASK_INDEX, -1)
        if (taskIndex < 0) return null
        val type =
            intent
                .getStringExtra(EXTRA_EVENT_TYPE)
                ?.let { runCatching { AlarmProgressEventType.valueOf(it) }.getOrNull() }
                ?: return null
        val progress = intent.getFloatExtra(EXTRA_PROGRESS, 0f)
        if (type == AlarmProgressEventType.VERIFIED_PROGRESS && (!progress.isFinite() || progress !in 0f..1f)) {
            return null
        }
        return AlarmProgressEvent(alarmId = alarmId, taskIndex = taskIndex, type = type, progress = progress)
    }
}
