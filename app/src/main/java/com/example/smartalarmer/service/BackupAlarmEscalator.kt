package com.example.smartalarmer.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class BackupAlarmEscalator(
    private val scope: CoroutineScope,
    private val timeoutMillis: Long,
    private val repeatCount: Int,
    private val reinforcementIntervalMillis: Long = REINFORCEMENT_INTERVAL_MILLIS,
    private val onEscalationAttempt: (Int) -> Unit
) {
    private var job: Job? = null

    fun start() {
        stop()
        job =
            scope.launch {
                delay(timeoutMillis)
                repeat(repeatCount.coerceAtLeast(1)) { index ->
                    onEscalationAttempt(index + 1)
                    if (index + 1 < repeatCount) delay(reinforcementIntervalMillis)
                }
            }
    }

    fun onInteraction() {
        start()
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val REINFORCEMENT_INTERVAL_MILLIS = 60_000L
    }
}

internal class AlarmVibrationController(
    context: Context
) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    fun reinforce() {
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
    }

    fun cancel() {
        vibrator.cancel()
    }

    private companion object {
        val VIBRATION_PATTERN =
            longArrayOf(
                0,
                1_000,
                500,
                1_000,
                500,
                1_000,
                500,
                1_000,
                500,
                1_000,
                500,
                1_000
            )
    }
}
