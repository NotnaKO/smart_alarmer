package com.example.smartalarmer.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.receiver.AlarmReceiver
import com.example.smartalarmer.ui.main.MainActivity

interface DeliveryTestSchedulingGateway {
    fun schedule(alarm: Alarm): AlarmScheduleResult

    fun cancel(): AlarmCancelResult
}

class AndroidDeliveryTestSchedulingGateway(
    context: Context
) : DeliveryTestSchedulingGateway {
    private val applicationContext = context.applicationContext

    override fun schedule(alarm: Alarm): AlarmScheduleResult = DeliveryTestScheduler.schedule(applicationContext, alarm)

    override fun cancel(): AlarmCancelResult = DeliveryTestScheduler.cancel(applicationContext)
}

object DeliveryTestScheduler {
    const val DELAY_MILLIS = 15_000L

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(
        context: Context,
        alarm: Alarm,
        triggerAtMillis: Long = System.currentTimeMillis() + DELAY_MILLIS
    ): AlarmScheduleResult = try {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            return AlarmScheduleResult.PermissionRequired
        }
        val operation =
            requireNotNull(
                operation(
                    context = context,
                    payload =
                    AlarmLaunchPayload
                        .fromAlarm(alarm, occurrenceTriggerAtMillis = triggerAtMillis)
                        .copy(
                            alarmId = AlarmLaunchPayload.NO_ALARM_ID,
                            launchType = AlarmLaunchType.DELIVERY_TEST
                        ),
                    flags = PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        val showIntent =
            PendingIntent.getActivity(
                context,
                SHOW_REQUEST_CODE,
                Intent(context, MainActivity::class.java).setAction(ACTION_SHOW_TEST),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent),
            operation
        )
        AlarmScheduleResult.Scheduled(triggerAtMillis)
    } catch (_: SecurityException) {
        AlarmScheduleResult.PermissionRequired
    } catch (error: Exception) {
        AlarmScheduleResult.Failure(error)
    }

    fun cancel(context: Context): AlarmCancelResult = try {
        val operation =
            operation(
                context = context,
                payload = AlarmLaunchPayload(launchType = AlarmLaunchType.DELIVERY_TEST),
                flags = PendingIntent.FLAG_NO_CREATE
            )
        if (operation != null) {
            context.getSystemService(AlarmManager::class.java).cancel(operation)
            operation.cancel()
        }
        AlarmCancelResult.Cancelled
    } catch (error: Exception) {
        AlarmCancelResult.Failure(error)
    }

    private fun operation(
        context: Context,
        payload: AlarmLaunchPayload,
        flags: Int
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        AlarmIntentContract
            .write(
                Intent(context, AlarmReceiver::class.java).setAction(ACTION_DELIVERY_TEST),
                payload
            ),
        flags or PendingIntent.FLAG_IMMUTABLE
    )

    private const val REQUEST_CODE = -91_337
    private const val SHOW_REQUEST_CODE = -91_338
    private const val ACTION_DELIVERY_TEST = "com.notnako.smartalarmer.action.DELIVERY_TEST"
    private const val ACTION_SHOW_TEST = "com.notnako.smartalarmer.action.SHOW_DELIVERY_TEST"
}
