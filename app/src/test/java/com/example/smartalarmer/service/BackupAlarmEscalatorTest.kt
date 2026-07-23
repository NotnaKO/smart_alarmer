package com.example.smartalarmer.service

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BackupAlarmEscalatorTest {
    @Test
    fun ignoredAlarmEscalatesAndReinforcesUpToConfiguredCount() = runTest {
        val attempts = mutableListOf<Int>()
        val escalator =
            BackupAlarmEscalator(
                scope = this,
                timeoutMillis = 10_000,
                repeatCount = 3,
                reinforcementIntervalMillis = 1_000,
                onEscalationAttempt = attempts::add
            )

        escalator.start()
        advanceTimeBy(10_001)
        assertEquals(listOf(1), attempts)
        advanceTimeBy(2_001)

        assertEquals(listOf(1, 2, 3), attempts)
    }

    @Test
    fun interactionRestartsTheInactivityTimeout() = runTest {
        val attempts = mutableListOf<Int>()
        val escalator =
            BackupAlarmEscalator(
                scope = this,
                timeoutMillis = 10_000,
                repeatCount = 3,
                reinforcementIntervalMillis = 1_000,
                onEscalationAttempt = attempts::add
            )

        escalator.start()
        advanceTimeBy(5_000)
        escalator.onInteraction()
        advanceTimeBy(9_999)
        assertEquals(emptyList<Int>(), attempts)
        advanceTimeBy(2)

        assertEquals(listOf(1), attempts)
    }
}
