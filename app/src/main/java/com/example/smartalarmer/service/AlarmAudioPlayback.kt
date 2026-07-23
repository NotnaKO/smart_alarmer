package com.example.smartalarmer.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class AlarmAudioPlayback(
    private val context: Context,
    private val audioManager: AudioManager,
    private val scope: CoroutineScope
) {
    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null
    private var ringtoneReplayJob: Job? = null
    private var toneGenerator: ToneGenerator? = null
    private var toneJob: Job? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocusRequest = false
    private var focusLost = false
    private var generation = 0
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    private val focusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> {
                    focusLost = false
                    runCatching { ringtone?.play() }
                    runCatching { mediaPlayer?.takeUnless { it.isPlaying }?.start() }
                }
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                -> {
                    focusLost = true
                    runCatching { ringtone?.stop() }
                    runCatching { mediaPlayer?.takeIf { it.isPlaying }?.pause() }
                }
            }
        }

    fun requestAudioFocus() {
        abandonAudioFocus()
        focusLost = false
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        audioFocusRequest = request
        hasAudioFocusRequest = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasAudioFocusRequest) {
            android.util.Log.w(TAG, "Alarm audio focus request was not granted")
        }
    }

    fun start(
        uris: List<Uri>,
        preferRingtoneApi: Boolean = false
    ) {
        generation++
        if (preferRingtoneApi) {
            uris.firstOrNull()?.let { uri ->
                if (startRingtone(generation, uri)) return
            }
        }
        startNext(generation, ArrayDeque(uris))
    }

    private fun startRingtone(
        expectedGeneration: Int,
        uri: Uri
    ): Boolean {
        val candidate =
            runCatching { RingtoneManager.getRingtone(context, uri) }
                .getOrNull()
                ?: return false
        return runCatching {
            candidate.audioAttributes = attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                candidate.isLooping = true
            }
            candidate.play()
            ringtone = candidate
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                ringtoneReplayJob =
                    scope.launch {
                        while (isActive && expectedGeneration == generation) {
                            delay(1_000)
                            if (!focusLost && runCatching { !candidate.isPlaying }.getOrDefault(false)) {
                                runCatching { candidate.play() }
                            }
                        }
                    }
            }
            android.util.Log.d(TAG, "Successfully playing selected alarm URI through Ringtone: $uri")
            true
        }.getOrElse { error ->
            runCatching { candidate.stop() }
            android.util.Log.e(TAG, "Unable to play selected alarm URI through Ringtone: $uri", error)
            false
        }
    }

    private fun startNext(
        expectedGeneration: Int,
        remainingUris: ArrayDeque<Uri>
    ) {
        if (expectedGeneration != generation) return
        val uri = remainingUris.pollFirst()
        if (uri == null) {
            startToneFallback(expectedGeneration)
            return
        }
        val candidate = MediaPlayer()
        mediaPlayer = candidate
        try {
            candidate.apply {
                setDataSource(context, uri)
                setAudioAttributes(attributes)
                isLooping = true
                setOnPreparedListener { player ->
                    if (expectedGeneration != generation || mediaPlayer !== player) {
                        releasePlayer(player)
                        return@setOnPreparedListener
                    }
                    runCatching { player.start() }
                        .onSuccess { android.util.Log.d(TAG, "Successfully playing alarm URI: $uri") }
                        .onFailure { error ->
                            android.util.Log.e(TAG, "Unable to start alarm URI $uri", error)
                            releasePlayer(player)
                            startNext(expectedGeneration, remainingUris)
                        }
                }
                setOnErrorListener { player, _, _ ->
                    android.util.Log.e(TAG, "MediaPlayer error for alarm URI $uri")
                    releasePlayer(player)
                    startNext(expectedGeneration, remainingUris)
                    true
                }
                prepareAsync()
            }
        } catch (error: Exception) {
            android.util.Log.e(TAG, "Failed to prepare alarm URI $uri", error)
            releasePlayer(candidate)
            startNext(expectedGeneration, remainingUris)
        }
    }

    private fun startToneFallback(expectedGeneration: Int) {
        if (expectedGeneration != generation) return
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneJob =
                scope.launch {
                    while (isActive && expectedGeneration == generation) {
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 5000)
                        delay(5000)
                    }
                }
            android.util.Log.d(TAG, "Successfully started ToneGenerator as final fallback")
        } catch (error: Exception) {
            android.util.Log.e(TAG, "ToneGenerator fallback failed too", error)
        }
    }

    fun release() {
        generation++
        ringtoneReplayJob?.cancel()
        ringtoneReplayJob = null
        runCatching { ringtone?.stop() }
        ringtone = null
        focusLost = false
        toneJob?.cancel()
        toneJob = null
        mediaPlayer
            ?.also { player -> runCatching { if (player.isPlaying) player.stop() } }
            ?.let(::releasePlayer)
        runCatching { toneGenerator?.stopTone() }
        runCatching { toneGenerator?.release() }
        toneGenerator = null
    }

    private fun releasePlayer(player: MediaPlayer) {
        if (mediaPlayer === player) mediaPlayer = null
        runCatching { player.setOnPreparedListener(null) }
        runCatching { player.setOnErrorListener(null) }
        runCatching { player.release() }
    }

    fun abandonAudioFocus() {
        if (hasAudioFocusRequest) {
            audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
        }
        hasAudioFocusRequest = false
        audioFocusRequest = null
    }

    private companion object {
        const val TAG = "AlarmService"
    }
}
