package com.example.smartalarmer.ui.main

import android.content.Context
import android.widget.Toast

internal fun handleMainUiEvent(
    context: Context,
    event: MainUiEvent
) {
    when (event) {
        is MainUiEvent.AlarmScheduled -> showScheduledToast(context, event.triggerAtMillis)
        MainUiEvent.ExactAlarmPermissionRequired ->
            Toast.makeText(
                context,
                com.example.smartalarmer.R.string.exact_alarm_permission_required,
                Toast.LENGTH_LONG
            ).show()
        MainUiEvent.NotificationCapabilityRequired ->
            Toast.makeText(
                context,
                com.example.smartalarmer.R.string.notification_delivery_required,
                Toast.LENGTH_LONG
            ).show()
        is MainUiEvent.AlarmScheduleFailed -> {
            android.util.Log.e("MainActivity", "Unable to schedule alarm", event.exception)
            Toast.makeText(
                context,
                com.example.smartalarmer.R.string.alarm_schedule_failed,
                Toast.LENGTH_LONG
            ).show()
        }
        is MainUiEvent.AlarmOperationFailed -> {
            android.util.Log.e("MainActivity", "Unable to update alarm state", event.exception)
            Toast.makeText(
                context,
                com.example.smartalarmer.R.string.alarm_operation_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private fun showScheduledToast(
    context: Context,
    triggerAtMillis: Long
) {
    val diffMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0)
    val hours = diffMs / (3600 * 1000)
    val minutes = (diffMs % (3600 * 1000)) / (60 * 1000)
    val hoursText =
        if (hours > 0) {
            context.resources.getQuantityString(
                com.example.smartalarmer.R.plurals.hours_plural,
                hours.toInt(),
                hours.toInt()
            )
        } else {
            ""
        }
    val minutesText =
        context.resources.getQuantityString(
            com.example.smartalarmer.R.plurals.minutes_plural,
            minutes.toInt(),
            minutes.toInt()
        )
    val timeText =
        if (hours > 0) {
            context.getString(
                com.example.smartalarmer.R.string.hours_and_minutes_connector,
                hoursText,
                minutesText
            )
        } else {
            minutesText
        }
    Toast.makeText(
        context,
        context.getString(com.example.smartalarmer.R.string.alarm_set_toast, timeText),
        Toast.LENGTH_LONG
    ).show()
}
