package com.example.smartalarmer.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.IBinder
import android.os.UserManager
import androidx.core.content.ContextCompat
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.alarm.AlarmProgressContract
import com.example.smartalarmer.alarm.AlarmProgressEventType
import com.example.smartalarmer.alarm.AlarmSoundResolver
import com.example.smartalarmer.alarm.sessionIdentity
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.scheduler.AlarmScheduleResult
import com.example.smartalarmer.scheduler.AlarmScheduler
import com.example.smartalarmer.scheduler.DirectBootAlarmStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmService : Service() {
    private var audioManager: AudioManager? = null
    private var originalAlarmVolume: Int? = null
    private var volumeJob: Job? = null
    private var backupEscalator: BackupAlarmEscalator? = null

    @Volatile
    private var volumeController: AlarmVolumeController? = null

    @Volatile
    private var backupEscalated = false
    private var activePayload: AlarmLaunchPayload? = null
    private var activeTaskIndex = 0
    private var activeNotificationId: Int? = null
    private lateinit var sessionStore: AlarmSessionStore
    private lateinit var pendingAlarmQueue: PendingAlarmQueueStore
    private lateinit var directBootStore: DirectBootAlarmStore
    private lateinit var wakeLockController: AlarmWakeLockController
    private var deliveryFollowUp: AlarmDeliveryFollowUp? = null
    private lateinit var audioPlayback: AlarmAudioPlayback
    private lateinit var vibrationController: AlarmVibrationController
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val progressReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val event = intent?.let(AlarmProgressContract::read) ?: return
                if (event.alarmId != activePayload?.alarmId) return
                if (event.taskIndex != activeTaskIndex) return
                val controller = volumeController ?: return
                backupEscalator?.onInteraction()
                backupEscalated = false
                vibrationController.cancel()
                val now = android.os.SystemClock.elapsedRealtime()
                val targetVolume =
                    when (event.type) {
                        AlarmProgressEventType.VERIFIED_PROGRESS ->
                            controller.onVerifiedProgress(event.progress, now)
                        AlarmProgressEventType.INTERMEDIATE_TASK_COMPLETED ->
                            controller.onIntermediateTaskCompleted(now).also {
                                activeTaskIndex++
                                activePayload?.alarmId?.let { alarmId ->
                                    runCatching { sessionStore.updateActiveTaskIndex(alarmId, activeTaskIndex) }
                                }
                            }
                    }
                applyAlarmVolume(targetVolume)
            }
        }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = manager
        sessionStore = AlarmSessionStore(this)
        pendingAlarmQueue = PendingAlarmQueueStore(this)
        directBootStore = DirectBootAlarmStore(this)
        wakeLockController = AlarmWakeLockController(this, serviceScope)
        if (getSystemService(UserManager::class.java).isUserUnlocked) {
            deliveryFollowUp = AlarmDeliveryFollowUp(this)
        }
        audioPlayback = AlarmAudioPlayback(this, manager, serviceScope)
        vibrationController = AlarmVibrationController(this)
        ContextCompat.registerReceiver(
            this,
            progressReceiver,
            IntentFilter(AlarmProgressContract.ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val incomingPayload = AlarmIntentContract.read(intent)
        val recoveredPayload =
            if (activePayload == null) {
                sessionStore
                    .current()
                    ?.takeUnless(AlarmAudioSession::dismissRequested)
                    ?.payload
            } else {
                null
            }
        val queuedHead =
            if (activePayload == null && recoveredPayload == null) {
                pendingAlarmQueue.peek()
            } else {
                null
            }
        val payload =
            recoveredPayload ?: queuedHead ?: incomingPayload
        val deliveryAlreadyRecorded =
            queuedHead?.sessionIdentity == payload.sessionIdentity
        if (
            payload.sessionIdentity != incomingPayload.sessionIdentity &&
            !incomingPayload.isPreview &&
            pendingAlarmQueue.enqueue(incomingPayload)
        ) {
            recordMainAlarmDelivery(incomingPayload)
        }
        if (payload.isPreview && activePayload != null) {
            return START_REDELIVER_INTENT
        }

        val current = activePayload
        if (current != null) {
            return when (overlapDecision(current, payload)) {
                AlarmOverlapDecision.REDELIVERY -> START_REDELIVER_INTENT
                AlarmOverlapDecision.QUEUE -> {
                    if (pendingAlarmQueue.enqueue(payload)) {
                        recordMainAlarmDelivery(payload)
                    }
                    START_REDELIVER_INTENT
                }
                AlarmOverlapDecision.START -> error("An active alarm cannot start in place")
            }
        }

        val foregroundNotification =
            startAlarm(
                payload = payload,
                deliveryAlreadyRecorded = deliveryAlreadyRecorded
            )
        pendingAlarmQueue.removeHead(payload)
        if (payload.isPreview) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        AlarmScreenLauncher.launchIfUnlocked(this, foregroundNotification.dismissPendingIntent)
        return START_REDELIVER_INTENT
    }

    private fun startAlarm(
        payload: AlarmLaunchPayload,
        deliveryAlreadyRecorded: Boolean = false
    ): AlarmForegroundNotification {
        val isRecoveredSession =
            sessionStore
                .current()
                ?.takeUnless(AlarmAudioSession::dismissRequested)
                ?.payload
                ?.sessionIdentity == payload.sessionIdentity
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val foregroundNotification = AlarmForegroundNotificationFactory(this).create(payload)
        val previousNotificationId = activeNotificationId
        startForeground(foregroundNotification.id, foregroundNotification.notification)
        activeNotificationId = foregroundNotification.id
        if (previousNotificationId != null && previousNotificationId != foregroundNotification.id) {
            notificationManager.cancel(previousNotificationId)
        }
        if (payload.isPreview) return foregroundNotification

        activePayload = payload
        audioPlayback.release()
        backupEscalator?.stop()
        vibrationController.cancel()
        backupEscalated = false
        volumeJob?.cancel()
        volumeJob = null
        volumeController = null
        val session = captureOriginalAlarmVolume(payload)
        activeTaskIndex = session?.activeTaskIndex ?: 0
        audioPlayback.requestAudioFocus()
        wakeLockController.acquire()

        val userUri = payload.soundUri?.let(Uri::parse)
        val fallbackUris = AlarmSoundResolver.playbackCandidates(this, userUri)
        audioPlayback.start(
            uris = fallbackUris,
            preferRingtoneApi = true
        )

        // Lock volume while allowing verified puzzle progress to reduce it.
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
        val controller =
            AlarmVolumeController(
                maxVolume = maxVolume,
                startedAtMillis = android.os.SystemClock.elapsedRealtime(),
                rampDurationMillis = payload.volumeRampSeconds * 1_000L
            )
        volumeController = controller
        applyAlarmVolume(controller.targetVolume(android.os.SystemClock.elapsedRealtime()))

        volumeJob =
            serviceScope.launch {
                while (isActive) {
                    val targetVolume =
                        if (backupEscalated) {
                            maxVolume
                        } else {
                            controller.targetVolume(android.os.SystemClock.elapsedRealtime())
                        }
                    if (!applyAlarmVolume(targetVolume)) break
                    delay(1000)
                }
            }

        if (payload.launchType == AlarmLaunchType.MAIN) {
            backupEscalator =
                BackupAlarmEscalator(
                    scope = serviceScope,
                    timeoutMillis = BackupAlarmEscalator.DEFAULT_TIMEOUT_MINUTES * 60_000L,
                    repeatCount = BackupAlarmEscalator.DEFAULT_REPEAT_COUNT
                ) { attempt ->
                    backupEscalated = true
                    if (attempt == 1) {
                        audioPlayback.release()
                        audioPlayback.start(AlarmSoundResolver.playbackCandidates(this, null))
                    }
                    applyAlarmVolume(maxVolume)
                    vibrationController.reinforce()
                }.also { it.start() }
            if (!isRecoveredSession && !deliveryAlreadyRecorded) {
                recordMainAlarmDelivery(payload)
            }
        }
        return foregroundNotification
    }

    override fun onDestroy() {
        val shouldAdvanceQueue = sessionStore.current()?.dismissRequested == true
        volumeJob?.cancel()
        volumeJob = null
        backupEscalator?.stop()
        backupEscalator = null
        backupEscalated = false
        vibrationController.cancel()
        volumeController = null
        serviceJob.cancel()
        audioPlayback.release()
        restoreOriginalAlarmVolume()
        audioPlayback.abandonAudioFocus()
        wakeLockController.release()
        activeNotificationId?.let { notificationId ->
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
        }
        activeNotificationId = null
        activePayload = null
        activeTaskIndex = 0
        runCatching { unregisterReceiver(progressReceiver) }
        super.onDestroy()
        if (shouldAdvanceQueue) {
            pendingAlarmQueue.peek()?.let { payload ->
                runCatching {
                    ContextCompat.startForegroundService(
                        applicationContext,
                        AlarmIntentContract.write(
                            Intent(applicationContext, AlarmService::class.java),
                            payload
                        )
                    )
                }
            }
        }
    }

    private fun recordMainAlarmDelivery(payload: AlarmLaunchPayload) {
        if (payload.launchType != AlarmLaunchType.MAIN) return
        if (getSystemService(UserManager::class.java).isUserUnlocked) {
            runCatching { directBootStore.remove(payload.alarmId) }
            deliveryFollowUp?.start(payload.alarmId)
        } else {
            rollForwardDirectBootSchedule(payload)
            runCatching { directBootStore.markDeliveryForUnlock(payload.alarmId) }
        }
    }

    private fun rollForwardDirectBootSchedule(payload: AlarmLaunchPayload) {
        val snapshot =
            directBootStore
                .snapshots()
                .firstOrNull {
                    it.alarm.id == payload.alarmId &&
                        (
                            payload.occurrenceTriggerAtMillis == AlarmLaunchPayload.NO_OCCURRENCE ||
                                it.triggerAtMillis == payload.occurrenceTriggerAtMillis
                            )
                } ?: return
        if (snapshot.alarm.repeatDays.isOneTime) {
            runCatching { directBootStore.remove(payload.alarmId) }
            return
        }
        when (val result = AlarmScheduler.schedule(this, snapshot.alarm)) {
            is AlarmScheduleResult.Scheduled ->
                runCatching {
                    directBootStore.upsert(snapshot.alarm, result.triggerAtMillis)
                }.onFailure { error ->
                    AlarmScheduler.cancel(this, snapshot.alarm)
                    android.util.Log.e(TAG, "Unable to mirror the next locked alarm occurrence", error)
                }
            AlarmScheduleResult.PermissionRequired ->
                android.util.Log.w(TAG, "Exact alarm access unavailable before unlock")
            is AlarmScheduleResult.Failure ->
                android.util.Log.e(TAG, "Unable to schedule the next locked alarm occurrence", result.exception)
        }
    }

    private fun applyAlarmVolume(targetVolume: Int): Boolean = try {
        audioManager?.setStreamVolume(
            AudioManager.STREAM_ALARM,
            targetVolume,
            0
        )
        true
    } catch (e: SecurityException) {
        android.util.Log.e("AlarmService", "Unable to change alarm volume", e)
        false
    }

    private fun captureOriginalAlarmVolume(payload: AlarmLaunchPayload): AlarmAudioSession? {
        val currentVolume = originalAlarmVolume ?: audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: return null
        val session = sessionStore.begin(payload = payload, currentVolume = currentVolume)
        originalAlarmVolume = session.originalVolume
        return session
    }

    private fun restoreOriginalAlarmVolume() {
        val volume = originalAlarmVolume ?: sessionStore.current()?.originalVolume ?: return
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        } catch (e: SecurityException) {
            android.util.Log.e("AlarmService", "Unable to restore alarm volume", e)
        } finally {
            originalAlarmVolume = null
            runCatching { sessionStore.clear() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AlarmService"
        internal const val WAKE_LOCK_RENEWAL_INTERVAL_MILLIS = AlarmWakeLockController.RENEWAL_INTERVAL_MILLIS
        internal const val WAKE_LOCK_TIMEOUT_MILLIS = AlarmWakeLockController.TIMEOUT_MILLIS

        internal fun overlapDecision(
            active: AlarmLaunchPayload?,
            incoming: AlarmLaunchPayload
        ): AlarmOverlapDecision = when {
            active == null -> AlarmOverlapDecision.START
            active.sessionIdentity == incoming.sessionIdentity -> AlarmOverlapDecision.REDELIVERY
            else -> AlarmOverlapDecision.QUEUE
        }

        internal fun notificationIdForAlarm(
            alarmId: Int,
            isPreview: Boolean = false
        ): Int = AlarmNotification.notificationIdForAlarm(alarmId, isPreview)

        fun calculateGradualVolume(
            elapsedSeconds: Long,
            maxVolume: Int,
            durationSeconds: Long = 60L
        ): Int = AlarmVolumeController.calculateRampVolume(
            startVolume = if (maxVolume > 0) 1 else 0,
            maxVolume = maxVolume,
            elapsedMillis = elapsedSeconds * 1_000L,
            durationMillis = durationSeconds * 1_000L
        )
    }
}

internal enum class AlarmOverlapDecision {
    START,
    REDELIVERY,
    QUEUE
}
