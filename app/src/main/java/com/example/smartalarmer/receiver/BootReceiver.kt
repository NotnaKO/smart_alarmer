package com.example.smartalarmer.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.data.AlarmDatabase
import com.example.smartalarmer.data.RoomAlarmRepository
import com.example.smartalarmer.domain.WakeUpCheckCoordinator
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmScheduler
import com.example.smartalarmer.scheduler.AndroidAlarmSchedulingGateway
import com.example.smartalarmer.scheduler.AndroidWakeUpCheckSchedulingGateway
import com.example.smartalarmer.scheduler.DirectBootAlarmStore
import com.example.smartalarmer.scheduler.RescheduleEnabledAlarms
import com.example.smartalarmer.service.AlarmDeliveryCoordinator
import com.example.smartalarmer.service.AlarmService
import com.example.smartalarmer.service.AlarmSessionStore
import com.example.smartalarmer.service.PendingAlarmQueueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val isUserUnlocked = context.getSystemService(UserManager::class.java).isUserUnlocked
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED || !isUserUnlocked) {
            if (shouldReschedule(intent.action, canScheduleExactAlarms = true)) {
                restoreBeforeUnlock(
                    context = context,
                    recalculateFromWallClock = shouldRecalculateLockedTrigger(intent.action)
                )
            }
            return
        }
        val isExactAlarmPermissionEvent =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        val canScheduleExactAlarms =
            if (isExactAlarmPermissionEvent) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

        if (shouldReschedule(intent.action, canScheduleExactAlarms)) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AlarmDatabase.getDatabase(context)
                    val repository = RoomAlarmRepository(database.alarmDao())
                    val directBootStore = DirectBootAlarmStore(context)
                    val mainScheduler = AndroidAlarmSchedulingGateway(context)
                    val deliveryCoordinator =
                        AlarmDeliveryCoordinator(
                            repository = repository,
                            scheduler = mainScheduler
                        )
                    directBootStore.deliveredAlarmIds().forEach { alarmId ->
                        runCatching { deliveryCoordinator.onAlarmSessionStarted(alarmId) }
                            .onSuccess { directBootStore.clearDeliveredAlarmId(alarmId) }
                    }
                    val wakeUpCoordinator =
                        WakeUpCheckCoordinator(
                            alarmRepository = repository,
                            sessionDao = database.wakeUpCheckDao(),
                            scheduler = AndroidWakeUpCheckSchedulingGateway(context)
                        )
                    directBootStore.dismissedAlarmIds().forEach { alarmId ->
                        runCatching { wakeUpCoordinator.start(alarmId) }
                            .onSuccess { result ->
                                if (result == null || result is AlarmScheduleResult.Scheduled) {
                                    directBootStore.clearDismissedAlarmId(alarmId)
                                }
                            }
                    }
                    val report =
                        RescheduleEnabledAlarms(
                            repository,
                            mainScheduler
                        )()
                    directBootStore.retainAlarmIds(
                        repository.getEnabledAlarms().mapTo(mutableSetOf()) { it.id }
                    )
                    wakeUpCoordinator.restoreAll()
                    report.failures.forEach { failure ->
                        when (val result = failure.result) {
                            AlarmScheduleResult.PermissionRequired ->
                                android.util.Log.w(
                                    TAG,
                                    "Exact alarm permission is required while restoring alarm ${failure.alarmId}"
                                )
                            is AlarmScheduleResult.Failure ->
                                android.util.Log.e(
                                    TAG,
                                    "Unable to restore alarm ${failure.alarmId}",
                                    result.exception
                                )
                            is AlarmScheduleResult.Scheduled -> Unit
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to restore alarms", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun restoreBeforeUnlock(
        context: Context,
        recalculateFromWallClock: Boolean
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = DirectBootAlarmStore(context)
                resumeInterruptedSession(context)
                val now = System.currentTimeMillis()
                store.snapshots().forEach { snapshot ->
                    when (
                        val result =
                            if (recalculateFromWallClock) {
                                AlarmScheduler.schedule(
                                    context = context,
                                    alarm = snapshot.alarm
                                )
                            } else {
                                AlarmScheduler.scheduleAt(
                                    context = context,
                                    alarm = snapshot.alarm,
                                    triggerAtMillis = maxOf(snapshot.triggerAtMillis, now + 1_000L)
                                )
                            }
                    ) {
                        is AlarmScheduleResult.Scheduled ->
                            store.upsert(snapshot.alarm, result.triggerAtMillis)
                        AlarmScheduleResult.PermissionRequired ->
                            Log.w(TAG, "Exact alarm access unavailable during locked boot")
                        is AlarmScheduleResult.Failure ->
                            Log.e(
                                TAG,
                                "Unable to restore alarm ${snapshot.alarm.id} before unlock",
                                result.exception
                            )
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "Unable to restore alarms before unlock", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun resumeInterruptedSession(context: Context) {
        val sessionStore = AlarmSessionStore(context)
        val session = sessionStore.current()
        val queue = PendingAlarmQueueStore(context)
        val payload =
            when {
                session == null -> queue.peek()
                session.dismissRequested -> {
                    sessionStore.clear()
                    queue.peek()
                }
                else -> session.payload
            } ?: return
        runCatching {
            ContextCompat.startForegroundService(
                context,
                AlarmIntentContract.write(
                    Intent(context, AlarmService::class.java),
                    payload
                )
            )
        }.onFailure {
            Log.e(TAG, "Unable to resume interrupted alarm session", it)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"

        internal fun shouldReschedule(
            action: String?,
            canScheduleExactAlarms: Boolean
        ): Boolean = when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
            -> true
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED ->
                canScheduleExactAlarms
            else -> false
        }

        internal fun shouldRecalculateLockedTrigger(action: String?): Boolean = action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
    }
}
