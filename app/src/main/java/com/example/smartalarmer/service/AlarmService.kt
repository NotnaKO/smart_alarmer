package com.example.smartalarmer.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.smartalarmer.alarm.AlarmIntentContract
import com.example.smartalarmer.alarm.AlarmLaunchType
import com.example.smartalarmer.alarm.AlarmProgressContract
import com.example.smartalarmer.alarm.AlarmProgressEventType
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

    @Volatile
    private var volumeController: AlarmVolumeController? = null
    private var activeAlarmId: Int? = null
    private var activeTaskIndex = 0
    private var activeNotificationId: Int? = null
    private lateinit var sessionStore: AlarmSessionStore
    private lateinit var wakeLockController: AlarmWakeLockController
    private lateinit var deliveryFollowUp: AlarmDeliveryFollowUp
    private lateinit var audioPlayback: AlarmAudioPlayback
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val progressReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                val event = intent?.let(AlarmProgressContract::read) ?: return
                if (event.alarmId != activeAlarmId) return
                if (event.taskIndex != activeTaskIndex) return
                val controller = volumeController ?: return
                val now = android.os.SystemClock.elapsedRealtime()
                val targetVolume =
                    when (event.type) {
                        AlarmProgressEventType.VERIFIED_PROGRESS ->
                            controller.onVerifiedProgress(event.progress, now)
                        AlarmProgressEventType.INTERMEDIATE_TASK_COMPLETED ->
                            controller.onIntermediateTaskCompleted(now).also {
                                activeTaskIndex++
                                activeAlarmId?.let { alarmId ->
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
        wakeLockController = AlarmWakeLockController(this, serviceScope)
        deliveryFollowUp = AlarmDeliveryFollowUp(this)
        audioPlayback = AlarmAudioPlayback(this, manager, serviceScope)
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

        val payload = AlarmIntentContract.read(intent)
        if (payload.isPreview && activeAlarmId != null) {
            return START_REDELIVER_INTENT
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val foregroundNotification = AlarmForegroundNotificationFactory(this).create(payload)
        val notificationId = foregroundNotification.id
        val previousNotificationId = activeNotificationId

        startForeground(notificationId, foregroundNotification.notification)
        activeNotificationId = notificationId
        if (previousNotificationId != null && previousNotificationId != notificationId) {
            notificationManager.cancel(previousNotificationId)
        }

        if (payload.isPreview) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!shouldReplaceActiveAlarm(activeAlarmId, payload.alarmId)) {
            return START_REDELIVER_INTENT
        }

        activeAlarmId = payload.alarmId

        audioPlayback.release()
        volumeJob?.cancel()
        volumeJob = null
        volumeController = null
        val session = captureOriginalAlarmVolume(payload)
        activeTaskIndex = session?.activeTaskIndex ?: 0
        audioPlayback.requestAudioFocus()
        wakeLockController.acquire()

        val userUri = payload.soundUri?.let(Uri::parse)
        val fallbackUris =
            listOfNotNull(
                userUri,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ).distinct()
        audioPlayback.start(fallbackUris)

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
                    val targetVolume = controller.targetVolume(android.os.SystemClock.elapsedRealtime())
                    if (!applyAlarmVolume(targetVolume)) break
                    delay(1000)
                }
            }

        if (payload.launchType == AlarmLaunchType.MAIN) {
            deliveryFollowUp.start(payload.alarmId)
        }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        volumeJob?.cancel()
        volumeJob = null
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
        activeAlarmId = null
        activeTaskIndex = 0
        runCatching { unregisterReceiver(progressReceiver) }
        super.onDestroy()
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

    private fun captureOriginalAlarmVolume(payload: com.example.smartalarmer.alarm.AlarmLaunchPayload): AlarmAudioSession? {
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
        internal const val WAKE_LOCK_RENEWAL_INTERVAL_MILLIS = AlarmWakeLockController.RENEWAL_INTERVAL_MILLIS
        internal const val WAKE_LOCK_TIMEOUT_MILLIS = AlarmWakeLockController.TIMEOUT_MILLIS

        internal fun shouldReplaceActiveAlarm(
            activeAlarmId: Int?,
            incomingAlarmId: Int
        ): Boolean = activeAlarmId == null || incomingAlarmId < 0 || activeAlarmId != incomingAlarmId

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
