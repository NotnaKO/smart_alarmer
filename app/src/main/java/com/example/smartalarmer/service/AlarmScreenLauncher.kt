package com.example.smartalarmer.service

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Opens the puzzle immediately while the user is already using an unlocked device.
 *
 * Android may intentionally reduce a full-screen notification to a short heads-up notification
 * in this state. Locked and non-interactive devices continue through the full-screen notification,
 * which is the platform-supported path for waking and covering the lock screen.
 */
internal object AlarmScreenLauncher {
    fun launchIfUnlocked(
        context: Context,
        dismissPendingIntent: PendingIntent
    ): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        if (!shouldLaunchImmediately(powerManager.isInteractive, keyguardManager.isKeyguardLocked)) {
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                dismissPendingIntent.send(senderActivityOptions().toBundle())
            } else {
                dismissPendingIntent.send()
            }
            true
        } catch (error: PendingIntent.CanceledException) {
            Log.w(TAG, "Unable to launch the alarm screen immediately", error)
            false
        } catch (error: SecurityException) {
            Log.w(TAG, "Immediate alarm screen launch was blocked", error)
            false
        }
    }

    internal fun shouldLaunchImmediately(
        isInteractive: Boolean,
        isKeyguardLocked: Boolean
    ): Boolean = isInteractive && !isKeyguardLocked

    internal fun senderBackgroundStartMode(sdkInt: Int): Int? = when {
        sdkInt >= 36 -> ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            @Suppress("DEPRECATION")
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        else -> null
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun senderActivityOptions(): ActivityOptions = ActivityOptions.makeBasic().apply {
        pendingIntentBackgroundActivityStartMode =
            requireNotNull(senderBackgroundStartMode(Build.VERSION.SDK_INT))
    }

    private const val TAG = "AlarmScreenLauncher"
}
