package com.example.smartalarmer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmService : Service() {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = "AlarmChannel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Active Alarm", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val dismissIntent = Intent(this, AlarmDismissActivity::class.java).apply {
            putExtra("PUZZLES_LIST", intent?.getStringExtra("PUZZLES_LIST"))
            putExtra("PUZZLE_COUNT", intent?.getIntExtra("PUZZLE_COUNT", 2))
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
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options?.toBundle()
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("WAKE UP NOW!")
            .setContentText("Complete tasks to silence the alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        startForeground(1, notification)

        // Launch the activity directly to take over the screen immediately
        try {
            startActivity(dismissIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Play Loud Sound with fallbacks and correct audio routing
        val fallbackUris = listOf(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )

        var successfullyStarted = false
        for (uri in fallbackUris) {
            if (uri == null) continue
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmService, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
                successfullyStarted = true
                android.util.Log.d("AlarmService", "Successfully playing alarm URI: $uri")
                break
            } catch (e: Exception) {
                android.util.Log.e("AlarmService", "Failed to play sound for URI $uri", e)
            }
        }

        if (!successfullyStarted) {
            try {
                val toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_ALARM, 100)
                toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 5000)
                android.util.Log.d("AlarmService", "Successfully started ToneGenerator as final fallback")
            } catch (e: Exception) {
                android.util.Log.e("AlarmService", "ToneGenerator fallback failed too", e)
            }
        }

        val isGradualVolume = intent?.getBooleanExtra("IS_GRADUAL_VOLUME", true) ?: true

        // Lock Volume / Gradual Volume Crescendo
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
        val startTime = System.currentTimeMillis()

        serviceScope.launch {
            while (isActive) {
                val targetVolume = if (isGradualVolume) {
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000L
                    calculateGradualVolume(elapsedSeconds, maxVolume)
                } else {
                    maxVolume
                }
                audioManager?.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    targetVolume,
                    0
                )
                delay(1000)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun calculateGradualVolume(elapsedSeconds: Long, maxVolume: Int, durationSeconds: Long = 30L): Int {
            if (elapsedSeconds < durationSeconds) {
                val progress = elapsedSeconds.toDouble() / durationSeconds.toDouble()
                val volumeRange = maxVolume - 1
                return 1 + (progress * volumeRange).toInt()
            }
            return maxVolume
        }
    }
}
