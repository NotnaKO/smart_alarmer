package com.example.smartalarmer

import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.smartalarmer.receiver.AlarmReceiver
import com.example.smartalarmer.receiver.BootReceiver
import com.example.smartalarmer.service.AlarmService
import com.example.smartalarmer.ui.dismiss.AlarmDismissActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectBootManifestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun alarmDeliveryComponentsAreDirectBootAware() {
        val packageManager = context.packageManager

        assertTrue(
            packageManager
                .getReceiverInfo(ComponentName(context, BootReceiver::class.java), 0)
                .directBootAware
        )
        assertTrue(
            packageManager
                .getReceiverInfo(ComponentName(context, AlarmReceiver::class.java), 0)
                .directBootAware
        )
        assertTrue(
            packageManager
                .getServiceInfo(ComponentName(context, AlarmService::class.java), 0)
                .directBootAware
        )
        assertTrue(
            packageManager
                .getActivityInfo(ComponentName(context, AlarmDismissActivity::class.java), 0)
                .directBootAware
        )
    }
}
