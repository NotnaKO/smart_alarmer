package com.example.smartalarmer.service

import kotlin.math.floor

internal class AlarmVolumeController(
    private val maxVolume: Int,
    startedAtMillis: Long,
    private val rampDurationMillis: Long = DEFAULT_RAMP_DURATION_MILLIS,
    private val inactivityTimeoutMillis: Long = DEFAULT_INACTIVITY_TIMEOUT_MILLIS
) {
    private sealed interface State {
        data class Ramp(
            val startVolume: Int,
            val startedAtMillis: Long
        ) : State

        data class ProgressFade(
            val anchorVolume: Int,
            val anchorProgress: Float,
            val progress: Float,
            val lastProgressAtMillis: Long
        ) : State
    }

    private var highestProgress = 0f
    private var state: State = State.Ramp(initialVolume(), startedAtMillis)

    @Synchronized
    fun targetVolume(nowMillis: Long): Int = volumeFor(state, nowMillis)

    @Synchronized
    fun onVerifiedProgress(
        progress: Float,
        nowMillis: Long
    ): Int {
        if (!progress.isFinite()) return volumeFor(state, nowMillis)
        val boundedProgress = progress.coerceIn(0f, 1f)
        if (boundedProgress <= highestProgress) return volumeFor(state, nowMillis)

        val currentState = state
        state =
            if (
                currentState is State.ProgressFade &&
                nowMillis - currentState.lastProgressAtMillis <= inactivityTimeoutMillis
            ) {
                currentState.copy(
                    progress = boundedProgress,
                    lastProgressAtMillis = nowMillis
                )
            } else {
                State.ProgressFade(
                    anchorVolume = volumeFor(currentState, nowMillis),
                    anchorProgress = highestProgress,
                    progress = boundedProgress,
                    lastProgressAtMillis = nowMillis
                )
            }
        highestProgress = boundedProgress
        return volumeFor(state, nowMillis)
    }

    @Synchronized
    fun onIntermediateTaskCompleted(nowMillis: Long): Int {
        highestProgress = 0f
        state = State.Ramp(startVolume = 0, startedAtMillis = nowMillis)
        return 0
    }

    private fun volumeFor(
        currentState: State,
        nowMillis: Long
    ): Int = when (currentState) {
        is State.Ramp ->
            calculateRampVolume(
                startVolume = currentState.startVolume,
                maxVolume = maxVolume,
                elapsedMillis = nowMillis - currentState.startedAtMillis,
                durationMillis = rampDurationMillis
            )
        is State.ProgressFade -> {
            val fadedVolume =
                calculateProgressVolume(
                    anchorVolume = currentState.anchorVolume,
                    anchorProgress = currentState.anchorProgress,
                    progress = currentState.progress
                )
            val rampStartedAt = currentState.lastProgressAtMillis + inactivityTimeoutMillis
            if (nowMillis <= rampStartedAt) {
                fadedVolume
            } else {
                calculateRampVolume(
                    startVolume = fadedVolume,
                    maxVolume = maxVolume,
                    elapsedMillis = nowMillis - rampStartedAt,
                    durationMillis = rampDurationMillis
                )
            }
        }
    }

    private fun initialVolume(): Int = if (maxVolume > 0) 1 else 0

    companion object {
        const val DEFAULT_RAMP_DURATION_MILLIS = 60_000L
        const val DEFAULT_INACTIVITY_TIMEOUT_MILLIS = 5_000L

        internal fun calculateRampVolume(
            startVolume: Int,
            maxVolume: Int,
            elapsedMillis: Long,
            durationMillis: Long
        ): Int {
            val boundedMax = maxVolume.coerceAtLeast(0)
            val boundedStart = startVolume.coerceIn(0, boundedMax)
            if (durationMillis <= 0L || elapsedMillis >= durationMillis) return boundedMax
            if (elapsedMillis <= 0L) return boundedStart
            val progress = elapsedMillis.toDouble() / durationMillis.toDouble()
            return (boundedStart + floor(progress * (boundedMax - boundedStart))).toInt()
                .coerceIn(boundedStart, boundedMax)
        }

        private fun calculateProgressVolume(
            anchorVolume: Int,
            anchorProgress: Float,
            progress: Float
        ): Int {
            if (progress >= 1f) return 0
            val remainingAtAnchor = (1f - anchorProgress).coerceAtLeast(0f)
            if (remainingAtAnchor == 0f) return 0
            val remainingFraction = ((1f - progress) / remainingAtAnchor).coerceIn(0f, 1f)
            return floor(anchorVolume * remainingFraction).toInt().coerceAtLeast(0)
        }
    }
}
