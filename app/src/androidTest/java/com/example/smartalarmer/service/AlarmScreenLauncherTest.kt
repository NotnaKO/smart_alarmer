package com.example.smartalarmer.service

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import com.example.smartalarmer.alarm.AlarmLaunchPayload
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmScreenLauncherTest {
    @Test
    fun unlockedInteractiveDeviceLaunchesDismissActivityImmediately() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        SystemClock.sleep(250)

        val powerManager = context.getSystemService(PowerManager::class.java)
        val keyguardManager = context.getSystemService(KeyguardManager::class.java)
        assertTrue("The test device must be interactive", powerManager.isInteractive)
        assertFalse("The test device must be unlocked", keyguardManager.isKeyguardLocked)

        val payload =
            AlarmLaunchPayload(
                alarmId = 812_437,
                alarmLabel = "Unlocked wake-up check",
                isPreview = true
            )
        val pendingIntent = AlarmNotification.dismissPendingIntent(context, payload)

        try {
            assertTrue(AlarmScreenLauncher.launchIfUnlocked(context, pendingIntent))

            val deadline = SystemClock.elapsedRealtime() + 5_000
            var launchedActivity: AlarmDismissActivity? = null
            while (SystemClock.elapsedRealtime() < deadline && launchedActivity == null) {
                instrumentation.runOnMainSync {
                    launchedActivity =
                        ActivityLifecycleMonitorRegistry
                            .getInstance()
                            .getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<AlarmDismissActivity>()
                            .firstOrNull()
                }
                if (launchedActivity == null) SystemClock.sleep(50)
            }

            val activity = requireNotNull(launchedActivity)
            assertEquals(payload, com.example.smartalarmer.alarm.AlarmIntentContract.read(activity.intent))
        } finally {
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry
                    .getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<AlarmDismissActivity>()
                    .forEach { it.finish() }
            }
            pendingIntent.cancel()
        }
    }
}
