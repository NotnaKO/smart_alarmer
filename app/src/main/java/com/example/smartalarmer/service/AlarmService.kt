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

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocusRequest = false
    private var originalAlarmVolume: Int? = null
    private var toneJob: Job? = null
    private var volumeJob: Job? = null
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
        val isPreview = intent?.getBooleanExtra("IS_PREVIEW", false) ?: false
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
            putExtra("PUZZLES_LIST", intent?.getStringExtra("PUZZLES_LIST"))
            putExtra("PUZZLE_COUNT", intent?.getIntExtra("PUZZLE_COUNT", 2))
            putExtra("ALARM_LABEL", intent?.getStringExtra("ALARM_LABEL") ?: "")
            putExtra("IS_PREVIEW", isPreview)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
            this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options?.toBundle()
        )

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(com.example.smartalarmer.R.string.wake_up_title))
            .setContentText(getString(com.example.smartalarmer.R.string.wake_up_desc))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(dismissPendingIntent)

        if (!isPreview) {
            notificationBuilder.setFullScreenIntent(dismissPendingIntent, true)
        }

        val notification = notificationBuilder.build()

        startForeground(1, notification)

        if (isPreview) {
            return START_NOT_STICKY
        }

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

        // Play Loud Sound with fallbacks and correct audio routing
        val soundUriString = intent?.getStringExtra("SOUND_URI")
        val userUri = soundUriString?.let { Uri.parse(it) }

        val fallbackUris = mutableListOf<Uri?>()
        if (userUri != null) {
            fallbackUris.add(userUri)
        }
        fallbackUris.addAll(listOf(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ))

        var successfullyStarted = false
        for (uri in fallbackUris) {
            if (uri == null) continue
            val candidate = MediaPlayer()
            try {
                candidate.apply {
                    setDataSource(this@AlarmService, uri)
                    setAudioAttributes(alarmAudioAttributes)
                    isLooping = true
                    prepare()
                    start()
                }
                mediaPlayer = candidate
                successfullyStarted = true
                android.util.Log.d("AlarmService", "Successfully playing alarm URI: $uri")
                break
            } catch (e: Exception) {
                candidate.release()
                android.util.Log.e("AlarmService", "Failed to play sound for URI $uri", e)
            }
        }

        if (!successfullyStarted) {
            try {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneJob = serviceScope.launch {
                    while (isActive) {
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 5000)
                        delay(5000)
                    }
                }
                android.util.Log.d("AlarmService", "Successfully started ToneGenerator as final fallback")
            } catch (e: Exception) {
                android.util.Log.e("AlarmService", "ToneGenerator fallback failed too", e)
            }
        }

        val isGradualVolume = intent?.getBooleanExtra("IS_GRADUAL_VOLUME", true) ?: true

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

        return START_STICKY
    }

    override fun onDestroy() {
        volumeJob?.cancel()
        volumeJob = null
        serviceJob.cancel()
        releasePlaybackResources()
        restoreOriginalAlarmVolume()
        abandonAlarmAudioFocus()
        super.onDestroy()
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
        toneJob?.cancel()
        toneJob = null

        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            player.release()
        }
        mediaPlayer = null

        toneGenerator?.stopTone()
        toneGenerator?.release()
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
        fun calculateGradualVolume(elapsedSeconds: Long, maxVolume: Int, durationSeconds: Long = 60L): Int {
            if (elapsedSeconds < durationSeconds) {
                val progress = elapsedSeconds.toDouble() / durationSeconds.toDouble()
                val volumeRange = maxVolume - 1
                return 1 + (progress * volumeRange).toInt()
            }
            return maxVolume
        }
    }
}
