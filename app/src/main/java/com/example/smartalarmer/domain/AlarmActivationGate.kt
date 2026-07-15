package com.example.smartalarmer.domain

fun interface AlarmActivationGate {
    fun isNotificationDeliveryReady(): Boolean

    companion object {
        val ALWAYS_READY = AlarmActivationGate { true }
    }
}
