package com.example.smartalarmer.service

import android.app.*
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
import androidx.core.app.NotificationCompat
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque

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
    private var playbackGeneration = 0
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private val alarmAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> runCatching {
                mediaPlayer?.takeUnless { it.isPlaying }?.start()
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> runCatching {
                mediaPlayer?.takeIf { it.isPlaying }?.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val isPreview = intent.getBooleanExtra("IS_PREVIEW", false)
        val alarmId = intent.getIntExtra("ALARM_ID", -1)
        if (isPreview && activeAlarmId != null) {
            return START_REDELIVER_INTENT
        }
        val channelId = "AlarmChannel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(com.example.smartalarmer.R.string.active_alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val dismissIntent = Intent(this, AlarmDismissActivity::class.java).apply {
            putExtra("ALARM_ID", alarmId)
            putExtra("PUZZLES_LIST", intent.getStringExtra("PUZZLES_LIST"))
            putExtra("PUZZLE_COUNT", intent.getIntExtra("PUZZLE_COUNT", 2))
            putExtra("ALARM_LABEL", intent.getStringExtra("ALARM_LABEL") ?: "")
            putExtra("IS_PREVIEW", isPreview)
            data = Uri.parse("smartalarmer://dismiss/${if (isPreview) "preview" else alarmId}")
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val options = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            android.app.ActivityOptions.makeBasic().apply {
                setPendingIntentCreatorBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            }
        } else {
            null
        }
        val dismissPendingIntent = PendingIntent.getActivity(
            this,
            pendingIntentRequestCode(alarmId, isPreview),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            options?.toBundle()
        )

        val alarmLabel = intent.getStringExtra("ALARM_LABEL").orEmpty()
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(com.example.smartalarmer.R.string.wake_up_title))
            .setContentText(alarmLabel.ifBlank { getString(com.example.smartalarmer.R.string.wake_up_desc) })
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(dismissPendingIntent)
            .setOngoing(!isPreview)

        if (!isPreview) {
            notificationBuilder.setFullScreenIntent(dismissPendingIntent, true)
        }

        val notification = notificationBuilder.build()
        val notificationId = notificationIdForAlarm(alarmId, isPreview)
        val previousNotificationId = activeNotificationId

        startForeground(notificationId, notification)
        activeNotificationId = notificationId
        if (previousNotificationId != null && previousNotificationId != notificationId) {
            notificationManager.cancel(previousNotificationId)
        }

        if (isPreview) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!shouldReplaceActiveAlarm(activeAlarmId, alarmId)) {
            return START_REDELIVER_INTENT
        }

        activeAlarmId = alarmId

        releasePlaybackResources()
        volumeJob?.cancel()
        volumeJob = null
        captureOriginalAlarmVolume()
        requestAlarmAudioFocus()

        // Launch the activity directly to take over the screen immediately
        try {
            startActivity(dismissIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val soundUriString = intent.getStringExtra("SOUND_URI")
        val userUri = soundUriString?.let { Uri.parse(it) }
        val fallbackUris = listOfNotNull(
            userUri,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ).distinct()
        startAlarmPlayback(fallbackUris)

        val isGradualVolume = intent.getBooleanExtra("IS_GRADUAL_VOLUME", true)

        // Lock Volume / Gradual Volume Crescendo
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
        val startTime = System.currentTimeMillis()

        volumeJob = serviceScope.launch {
            while (isActive) {
                val targetVolume = if (isGradualVolume) {
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

    private fun startNextPlayback(generation: Int, remainingUris: ArrayDeque<Uri>) {
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
            toneJob = serviceScope.launch {
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
            originalAlarmVolume = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM)
        }
    }

    private fun restoreOriginalAlarmVolume() {
        val volume = originalAlarmVolume ?: return
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, volume, 0)
        } catch (e: SecurityException) {
            android.util.Log.e("AlarmService", "Unable to restore alarm volume", e)
        } finally {
            originalAlarmVolume = null
        }
    }

    private fun releasePlaybackResources() {
        playbackGeneration++
        toneJob?.cancel()
        toneJob = null

        mediaPlayer?.also { player ->
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
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
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
        internal fun shouldReplaceActiveAlarm(activeAlarmId: Int?, incomingAlarmId: Int): Boolean {
            return activeAlarmId == null || incomingAlarmId < 0 || activeAlarmId != incomingAlarmId
        }

        internal fun notificationIdForAlarm(alarmId: Int, isPreview: Boolean = false): Int {
            if (isPreview) return PREVIEW_NOTIFICATION_ID
            return ALARM_NOTIFICATION_ID_BASE + Math.floorMod(alarmId, ALARM_NOTIFICATION_ID_RANGE)
        }

        private fun pendingIntentRequestCode(alarmId: Int, isPreview: Boolean): Int {
            return notificationIdForAlarm(alarmId, isPreview)
        }

        fun calculateGradualVolume(elapsedSeconds: Long, maxVolume: Int, durationSeconds: Long = 60L): Int {
            if (elapsedSeconds < durationSeconds) {
                val progress = elapsedSeconds.toDouble() / durationSeconds.toDouble()
                val volumeRange = maxVolume - 1
                return 1 + (progress * volumeRange).toInt()
            }
            return maxVolume
        }

        private const val PREVIEW_NOTIFICATION_ID = 1
        private const val ALARM_NOTIFICATION_ID_BASE = 10_000
        private const val ALARM_NOTIFICATION_ID_RANGE = 1_000_000
    }
}
