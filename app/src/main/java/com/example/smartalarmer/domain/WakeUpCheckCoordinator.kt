package com.example.smartalarmer.domain

import com.example.smartalarmer.data.AlarmRepository
import com.example.smartalarmer.data.WakeUpCheckDao
import com.example.smartalarmer.data.WakeUpCheckSession
import com.example.smartalarmer.scheduler.AlarmCancelResult
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.WakeUpCheckSchedulingGateway
import java.time.Clock
import java.util.UUID

class WakeUpCheckCoordinator(
    private val alarmRepository: AlarmRepository,
    private val sessionDao: WakeUpCheckDao,
    private val scheduler: WakeUpCheckSchedulingGateway,
    private val clock: Clock = Clock.systemUTC(),
    private val tokenFactory: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun start(alarmId: Int): AlarmScheduleResult? {
        val alarm = alarmRepository.getAlarmById(alarmId) ?: return null
        if (!alarm.wakeUpChecksEnabled) return null

        cancel(alarmId)
        val intervalMinutes =
            alarm.wakeUpCheckIntervalMinutes.takeIf {
                it in WakeUpCheckConfig.INTERVAL_OPTIONS_MINUTES
            } ?: WakeUpCheckConfig.DEFAULT_INTERVAL_MINUTES
        val session =
            WakeUpCheckSession(
                alarmId = alarm.id,
                token = tokenFactory(),
                nextCheckNumber = 1,
                totalChecks = alarm.wakeUpCheckCount.coerceIn(WakeUpCheckConfig.COUNT_RANGE),
                intervalMinutes = intervalMinutes,
                nextTriggerAtMillis =
                clock.instant().plusSeconds(intervalMinutes * 60L).toEpochMilli(),
                puzzlesList = alarm.puzzleSelection.encoded,
                soundUri = alarm.soundUri,
                alarmLabel = alarm.label
            )
        sessionDao.upsertSession(session)
        return scheduler.schedule(session).also { result ->
            if (result !is AlarmScheduleResult.Scheduled) sessionDao.deleteSession(alarmId)
        }
    }

    suspend fun complete(
        alarmId: Int,
        token: String,
        checkNumber: Int
    ): AlarmScheduleResult? {
        val current = sessionDao.getSession(alarmId) ?: return null
        if (current.token != token || current.nextCheckNumber != checkNumber) return null
        if (checkNumber >= current.totalChecks) {
            scheduler.cancel(alarmId)
            sessionDao.deleteSession(alarmId)
            return null
        }

        val next =
            current.copy(
                nextCheckNumber = checkNumber + 1,
                nextTriggerAtMillis =
                clock.instant().plusSeconds(current.intervalMinutes * 60L).toEpochMilli()
            )
        sessionDao.upsertSession(next)
        return scheduler.schedule(next).also { result ->
            if (result !is AlarmScheduleResult.Scheduled) sessionDao.deleteSession(alarmId)
        }
    }

    suspend fun cancel(alarmId: Int): AlarmCancelResult {
        val result = scheduler.cancel(alarmId)
        if (result is AlarmCancelResult.Cancelled) sessionDao.deleteSession(alarmId)
        return result
    }

    suspend fun restoreAll(): List<AlarmScheduleResult> = buildList {
        sessionDao.getAllSessions().forEach { session ->
            if (alarmRepository.getAlarmById(session.alarmId) == null) {
                cancel(session.alarmId)
            } else {
                add(scheduler.schedule(session))
            }
        }
    }
}
