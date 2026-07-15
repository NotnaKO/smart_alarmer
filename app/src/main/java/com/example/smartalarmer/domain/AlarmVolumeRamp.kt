package com.example.smartalarmer.domain

object AlarmVolumeRamp {
    const val DEFAULT_SECONDS = 60
    val OPTIONS_SECONDS = listOf(30, 60, 120, 240)

    fun sanitize(seconds: Int): Int = seconds.takeIf { it in OPTIONS_SECONDS } ?: DEFAULT_SECONDS
}
