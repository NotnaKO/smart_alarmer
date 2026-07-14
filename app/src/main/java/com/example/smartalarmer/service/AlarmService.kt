package com.example.smartalarmer.service

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.smartalarmer.R
import com.example.smartalarmer.alarm.AlarmIntentContract
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocusRequest = false
    private var originalAlarmVolume: Int? = null
    private var toneJob: Job? = null
    private var volumeJob: Job? = null
    private var activeAlarmId: Int? = null
    private var activeNotificationId: Int? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var sessionStore: AlarmSessionStore
    private var playbackGeneration = 0
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val alarmAudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    private val audioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN ->
                    runCatching {
                        mediaPlayer?.takeUnless { it.isPlaying }?.start()
                    }
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ->
                    runCatching {
                        mediaPlayer?.takeIf { it.isPlaying }?.pause()
                    }
            }
        }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sessionStore = AlarmSessionStore(this)
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
        AlarmNotification.ensureChannel(this)
        val dismissPendingIntent = AlarmNotification.dismissPendingIntent(this, payload)
        val notificationBuilder =
            NotificationCompat
                .Builder(this, AlarmNotification.CHANNEL_ID)
                .setContentTitle(getString(R.string.wake_up_title))
                .setContentText(payload.alarmLabel.ifBlank { getString(R.string.wake_up_desc) })
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(dismissPendingIntent)
                .setOngoing(!payload.isPreview)

        if (!payload.isPreview) {
            notificationBuilder.setFullScreenIntent(dismissPendingIntent, true)
        }

        val notification = notificationBuilder.build()
        val notificationId = notificationIdForAlarm(payload.alarmId, payload.isPreview)
        val previousNotificationId = activeNotificationId

        startForeground(notificationId, notification)
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

        releasePlaybackResources()
        volumeJob?.cancel()
        volumeJob = null
        captureOriginalAlarmVolume()
        requestAlarmAudioFocus()
        acquireWakeLock()

        val userUri = payload.soundUri?.let(Uri::parse)
        val fallbackUris =
            listOfNotNull(
                userUri,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ).distinct()
        startAlarmPlayback(fallbackUris)

        // Lock Volume / Gradual Volume Crescendo
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
        val startTime = System.currentTimeMillis()

        volumeJob =
            serviceScope.launch {
                while (isActive) {
                    val targetVolume =
                        if (payload.isGradualVolume) {
                            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000L
                            calculateGradualVolume(elapsedSeconds, maxVolume)
                        } else {
                            maxVolume
                        }
                    try {
                        audioManager?.setStreamVolume(
                            AudioManager.STREAM_ALARM,
                            targetVolume,
                            0
                        )
                    } catch (e: SecurityException) {
                        android.util.Log.e("AlarmService", "Unable to change alarm volume", e)
                        break
                    }
                    delay(1000)
                }
            }

        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        volumeJob?.cancel()
        volumeJob = null
        serviceJob.cancel()
        releasePlaybackResources()
        restoreOriginalAlarmVolume()
        abandonAlarmAudioFocus()
        releaseWakeLock()
        activeNotificationId?.let { notificationId ->
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(notificationId)
        }
        activeNotificationId = null
        activeAlarmId = null
        super.onDestroy()
    }

    private fun startAlarmPlayback(uris: List<Uri>) {
        playbackGeneration++
        startNextPlayback(
            generation = playbackGeneration,
            remainingUris = ArrayDeque(uris)
        )
    }

    private fun startNextPlayback(
        generation: Int,
        remainingUris: ArrayDeque<Uri>
    ) {
        if (generation != playbackGeneration) return
        val uri = remainingUris.pollFirst()
        if (uri == null) {
            startToneFallback(generation)
            return
        }

        val candidate = MediaPlayer()
        mediaPlayer = candidate
        try {
            candidate.apply {
                setDataSource(this@AlarmService, uri)
                setAudioAttributes(alarmAudioAttributes)
                isLooping = true
                setOnPreparedListener { player ->
                    if (generation != playbackGeneration || mediaPlayer !== player) {
                        releasePlayer(player)
                        return@setOnPreparedListener
                    }
                    try {
                        player.start()
                        android.util.Log.d("AlarmService", "Successfully playing alarm URI: $uri")
                    } catch (e: Exception) {
                        android.util.Log.e("AlarmService", "Unable to start alarm URI $uri", e)
                        releasePlayer(player)
                        startNextPlayback(generation, remainingUris)
                    }
                }
                setOnErrorListener { player, _, _ ->
                    android.util.Log.e("AlarmService", "MediaPlayer error for alarm URI $uri")
                    releasePlayer(player)
                    startNextPlayback(generation, remainingUris)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmService", "Failed to prepare alarm URI $uri", e)
            releasePlayer(candidate)
            startNextPlayback(generation, remainingUris)
        }
    }

    private fun releasePlayer(player: MediaPlayer) {
        if (mediaPlayer === player) mediaPlayer = null
        runCatching { player.setOnPreparedListener(null) }
        runCatching { player.setOnErrorListener(null) }
        runCatching { player.release() }
    }

    private fun startToneFallback(generation: Int) {
        if (generation != playbackGeneration) return
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneJob =
                serviceScope.launch {
                    while (isActive && generation == playbackGeneration) {
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 5000)
                        delay(5000)
                    }
                }
            android.util.Log.d("AlarmService", "Successfully started ToneGenerator as final fallback")
        } catch (e: Exception) {
            android.util.Log.e("AlarmService", "ToneGenerator fallback failed too", e)
        }
    }

    private fun captureOriginalAlarmVolume() {
        if (originalAlarmVolume == null) {
            val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: return
            originalAlarmVolume =
                sessionStore
                    .begin(
                        alarmId = activeAlarmId ?: -1,
                        currentVolume = currentVolume
                    ).originalVolume
        }
    }

    private fun restoreOriginalAlarmVolume() {
        val volume = originalAlarmVolume ?: sessionStore.current()?.originalVolume ?: return
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        } catch (e: SecurityException) {
            android.util.Log.e("AlarmService", "Unable to restore alarm volume", e)
        } finally {
            originalAlarmVolume = null
            sessionStore.clear()
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
            powerManager
                .newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "$packageName:active-alarm"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun releasePlaybackResources() {
        playbackGeneration++
        toneJob?.cancel()
        toneJob = null

        mediaPlayer
            ?.also { player ->
                runCatching {
                    if (player.isPlaying) player.stop()
                }
            }?.let(::releasePlayer)

        runCatching { toneGenerator?.stopTone() }
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    private fun requestAlarmAudioFocus() {
        abandonAlarmAudioFocus()
        val manager = audioManager ?: return
        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request =
                    AudioFocusRequest
                        .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(alarmAudioAttributes)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build()
                audioFocusRequest = request
                manager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        hasAudioFocusRequest = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasAudioFocusRequest) {
            android.util.Log.w("AlarmService", "Alarm audio focus request was not granted")
        }
    }

    private fun abandonAlarmAudioFocus() {
        if (!hasAudioFocusRequest) {
            audioFocusRequest = null
            return
        }
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(manager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocusRequest = false
        audioFocusRequest = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
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
        ): Int {
            if (elapsedSeconds < durationSeconds) {
                val progress = elapsedSeconds.toDouble() / durationSeconds.toDouble()
                val volumeRange = maxVolume - 1
                return 1 + (progress * volumeRange).toInt()
            }
            return maxVolume
        }
    }
}
