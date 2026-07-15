package com.example.smartalarmer.utils

import android.content.Context
import com.example.smartalarmer.domain.AlarmActivationGate

class AndroidAlarmActivationGate(
    context: Context
) : AlarmActivationGate {
    private val applicationContext = context.applicationContext

    override fun isNotificationDeliveryReady(): Boolean = AlarmCapabilityChecker.check(applicationContext).notificationDeliveryReady
}
