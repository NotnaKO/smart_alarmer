package com.example.smartalarmer.utils

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.util.Calendar

object AlarmTimeFormatter {
    fun formatTime(
        context: Context,
        hour: Int,
        minute: Int
    ): String {
        val calendar =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        return DateFormat.getTimeFormat(context).format(calendar.time)
    }

    fun formatNextTrigger(
        context: Context,
        triggerAtMillis: Long
    ): String = DateUtils.formatDateTime(
        context,
        triggerAtMillis,
        DateUtils.FORMAT_SHOW_DATE or
            DateUtils.FORMAT_SHOW_WEEKDAY or
            DateUtils.FORMAT_SHOW_TIME or
            DateUtils.FORMAT_ABBREV_MONTH
    )
}
