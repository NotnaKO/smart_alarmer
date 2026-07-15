package com.example.smartalarmer.service

import android.content.Context
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class AlarmWakeLockController(
    context: Context,
    private val scope: CoroutineScope
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val tag = "${context.packageName}:active-alarm"
    private var wakeLock: PowerManager.WakeLock? = null
    private var renewalJob: Job? = null

    fun acquire() {
        if (wakeLock == null) {
            wakeLock =
                powerManager
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
                    .apply { setReferenceCounted(false) }
        }
        renew()
        renewalJob?.cancel()
        renewalJob =
            scope.launch {
                while (isActive) {
                    delay(RENEWAL_INTERVAL_MILLIS)
                    renew()
                }
            }
    }

    @Synchronized
    private fun renew() {
        wakeLock?.run {
            if (isHeld) release()
            acquire(TIMEOUT_MILLIS)
        }
    }

    @Synchronized
    fun release() {
        renewalJob?.cancel()
        renewalJob = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val RENEWAL_INTERVAL_MILLIS = 9 * 60 * 1000L
        const val TIMEOUT_MILLIS = 10 * 60 * 1000L
    }
}
