package com.example.smartalarmer.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.data.WakeUpCheckSession
import com.example.smartalarmer.receiver.AlarmReceiver
import com.example.smartalarmer.ui.main.MainActivity

interface WakeUpCheckSchedulingGateway {
    fun schedule(session: WakeUpCheckSession): AlarmScheduleResult

    fun cancel(alarmId: Int): AlarmCancelResult
}

class AndroidWakeUpCheckSchedulingGateway(
    context: Context
) : WakeUpCheckSchedulingGateway {
    private val applicationContext = context.applicationContext

    override fun schedule(session: WakeUpCheckSession): AlarmScheduleResult = WakeUpCheckScheduler.schedule(applicationContext, session)

    override fun cancel(alarmId: Int): AlarmCancelResult = WakeUpCheckScheduler.cancel(applicationContext, alarmId)
}

object WakeUpCheckScheduler {
    private const val ACTION_DELIVER = "com.example.smartalarmer.action.DELIVER_WAKE_UP_CHECK"
    private const val VOLUME_RAMP_SECONDS = 30

    @SuppressLint("ScheduleExactAlarm")
    fun schedule(
        context: Context,
        session: WakeUpCheckSession
    ): AlarmScheduleResult = try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            return AlarmScheduleResult.PermissionRequired
        }
        val deliveryIntent =
            AlarmIntentContract.write(
                Intent(context, AlarmReceiver::class.java).apply {
                    action = ACTION_DELIVER
                    data = deliveryUri(session.alarmId)
                },
                session.toPayload()
            )
        val delivery =
            PendingIntent.getBroadcast(
                context,
                session.alarmId,
                deliveryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        val showIntent =
            PendingIntent.getActivity(
                context,
                session.alarmId,
                Intent(context, MainActivity::class.java).apply { data = deliveryUri(session.alarmId) },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(session.nextTriggerAtMillis, showIntent),
            delivery
        )
        AlarmScheduleResult.Scheduled(session.nextTriggerAtMillis)
    } catch (_: SecurityException) {
        AlarmScheduleResult.PermissionRequired
    } catch (error: Exception) {
        AlarmScheduleResult.Failure(error)
    }

    fun cancel(
        context: Context,
        alarmId: Int
    ): AlarmCancelResult = try {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                alarmId,
                Intent(context, AlarmReceiver::class.java).apply {
                    action = ACTION_DELIVER
                    data = deliveryUri(alarmId)
                },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        AlarmCancelResult.Cancelled
    } catch (error: Exception) {
        AlarmCancelResult.Failure(error)
    }

    private fun WakeUpCheckSession.toPayload() = AlarmLaunchPayload(
        alarmId = alarmId,
        puzzlesList = puzzlesList,
        puzzleCount = 1,
        soundUri = soundUri,
        alarmLabel = alarmLabel,
        volumeRampSeconds = VOLUME_RAMP_SECONDS,
        launchType = AlarmLaunchType.WAKE_UP_CHECK,
        wakeUpCheckNumber = nextCheckNumber,
        wakeUpCheckTotal = totalChecks,
        wakeUpCheckToken = token,
        wakeUpChecksEnabled = true,
        wakeUpCheckIntervalMinutes = intervalMinutes
    )

    private fun deliveryUri(alarmId: Int): Uri = Uri.Builder().scheme("smartalarmer").authority("wake-up-check").appendPath(alarmId.toString()).build()
}
