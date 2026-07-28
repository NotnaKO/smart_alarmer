package com.example.smartalarmer

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.scheduler.AlarmTimeCalculator
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmTimeCalculatorTest {
    @Test
    fun oneTimeAlarmUsesTodayWhenTimeIsStillFuture() {
        val result = calculator("2026-06-18T10:00:00Z").nextTrigger(alarm(10, 30))

        assertEquals(Instant.parse("2026-06-18T10:30:00Z"), result)
    }

    @Test
    fun oneTimeAlarmUsesTomorrowWhenTimeHasPassed() {
        val result = calculator("2026-06-18T10:00:00Z").nextTrigger(alarm(9, 30))

        assertEquals(Instant.parse("2026-06-19T09:30:00Z"), result)
    }

    @Test
    fun oneTimeAlarmUsesTomorrowWhenNowExactlyMatchesAlarmTime() {
        val result = calculator("2026-06-18T10:00:00Z").nextTrigger(alarm(10, 0))

        assertEquals(Instant.parse("2026-06-19T10:00:00Z"), result)
    }

    @Test
    fun oneTimeAlarmCrossesLeapYearBoundary() {
        val result = calculator("2028-02-29T23:59:59Z").nextTrigger(alarm(23, 59))

        assertEquals(Instant.parse("2028-03-01T23:59:00Z"), result)
    }

    @Test
    fun datedOneTimeAlarmUsesSelectedFutureLocalDate() {
        val result =
            calculator("2026-06-18T10:00:00Z").nextTrigger(
                alarm(9, 30).copy(
                    oneTimeDateEpochDay = LocalDate.parse("2026-06-21").toEpochDay()
                )
            )

        assertEquals(Instant.parse("2026-06-21T09:30:00Z"), result)
    }

    @Test
    fun datedOneTimeAlarmNeverRollsExpiredDateForward() {
        assertThrows(IllegalStateException::class.java) {
            calculator("2026-06-18T10:00:00Z").nextTrigger(
                alarm(9, 30).copy(
                    oneTimeDateEpochDay = LocalDate.parse("2026-06-18").toEpochDay()
                )
            )
        }
    }

    @Test
    fun recurringAlarmIgnoresUnexpectedOneTimeDate() {
        val result =
            calculator("2026-06-18T10:00:00Z").nextTrigger(
                alarm(hour = 9, minute = 30, days = "1")
                    .copy(oneTimeDateEpochDay = LocalDate.parse("2030-01-01").toEpochDay())
            )

        assertEquals(Instant.parse("2026-06-22T09:30:00Z"), result)
    }

    @Test
    fun datedOneTimeAlarmInDstGapMovesForwardByGapLength() {
        val zone = ZoneId.of("America/New_York")
        val result =
            calculator("2026-03-07T12:00:00Z", zone).nextTrigger(
                alarm(2, 30).copy(
                    oneTimeDateEpochDay = LocalDate.parse("2026-03-08").toEpochDay()
                )
            )

        assertEquals(
            ZonedDateTime.parse("2026-03-08T03:30:00-04:00[America/New_York]").toInstant(),
            result
        )
    }

    @Test
    fun recurringAlarmFindsNextConfiguredWeekday() {
        val result =
            calculator("2026-06-18T10:00:00Z").nextTrigger(
                alarm(hour = 9, minute = 30, days = "1,4")
            )

        assertEquals(Instant.parse("2026-06-22T09:30:00Z"), result)
    }

    @Test
    fun recurringAlarmUsesTodayWhenConfiguredTimeIsFuture() {
        val result =
            calculator("2026-06-18T10:00:00Z").nextTrigger(
                alarm(hour = 10, minute = 1, days = "4")
            )

        assertEquals(Instant.parse("2026-06-18T10:01:00Z"), result)
    }

    @Test
    fun oddWeekAlarmSkipsMatchingDayInEvenIsoWeek() {
        val result =
            calculator("2026-06-18T10:00:00Z").nextTrigger(
                alarm(hour = 9, minute = 30, days = "1", weekParity = "ODD")
            )

        assertEquals(Instant.parse("2026-06-29T09:30:00Z"), result)
    }

    @Test
    fun evenWeekAlarmCanScheduleFourteenDaysAheadAfterTodaysTimePasses() {
        val result =
            calculator("2026-06-22T10:00:00Z").nextTrigger(
                alarm(hour = 9, minute = 30, days = "1", weekParity = "EVEN")
            )

        assertEquals(Instant.parse("2026-07-06T09:30:00Z"), result)
    }

    @Test
    fun oddWeekAlarmUsesIsoWeekNumberAcrossWeekBasedYearBoundary() {
        val result =
            calculator("2020-12-28T10:00:00Z").nextTrigger(
                alarm(hour = 9, minute = 30, days = "1", weekParity = "ODD")
            )

        assertEquals(Instant.parse("2021-01-04T09:30:00Z"), result)
    }

    @Test
    fun malformedRepeatDaysFallBackToOneTimeScheduling() {
        val result =
            calculator("2026-06-18T10:00:00Z").nextTrigger(
                alarm(hour = 11, minute = 0, days = "invalid,0,8")
            )

        assertEquals(Instant.parse("2026-06-18T11:00:00Z"), result)
    }

    @Test
    fun springDstGapMovesAlarmForwardByGapLength() {
        val zone = ZoneId.of("America/New_York")
        val result =
            calculator("2026-03-08T06:00:00Z", zone).nextTrigger(
                alarm(hour = 2, minute = 30, days = "7")
            )

        assertEquals(
            ZonedDateTime.parse("2026-03-08T03:30:00-04:00[America/New_York]").toInstant(),
            result
        )
    }

    @Test
    fun fallDstOverlapUsesSecondOccurrenceWhenFirstHasPassed() {
        val zone = ZoneId.of("America/New_York")
        val firstOffsetNow =
            ZonedDateTime
                .ofLocal(
                    LocalDateTime.of(2026, 11, 1, 1, 45),
                    zone,
                    ZoneOffset.ofHours(-4)
                ).toInstant()
        val result =
            AlarmTimeCalculator(Clock.fixed(firstOffsetNow, ZoneOffset.UTC), zone).nextTrigger(
                alarm(hour = 1, minute = 30, days = "7")
            )

        assertEquals(
            ZonedDateTime
                .ofLocal(
                    LocalDateTime.of(2026, 11, 1, 1, 30),
                    zone,
                    ZoneOffset.ofHours(-5)
                ).toInstant(),
            result
        )
    }

    @Test
    fun fallDstOverlapUsesFirstOccurrenceWhenBothAreFuture() {
        val zone = ZoneId.of("America/New_York")
        val result =
            calculator("2026-11-01T04:00:00Z", zone).nextTrigger(
                alarm(hour = 1, minute = 30, days = "7")
            )

        assertEquals(
            ZonedDateTime
                .ofLocal(
                    LocalDateTime.of(2026, 11, 1, 1, 30),
                    zone,
                    ZoneOffset.ofHours(-4)
                ).toInstant(),
            result
        )
    }

    @Test
    fun injectedZoneControlsWallClockInterpretation() {
        val now = "2026-06-18T00:00:00Z"
        val utcResult = calculator(now, ZoneId.of("UTC")).nextTrigger(alarm(8, 0))
        val tokyoResult = calculator(now, ZoneId.of("Asia/Tokyo")).nextTrigger(alarm(8, 0))

        assertEquals(Instant.parse("2026-06-18T08:00:00Z"), utcResult)
        assertEquals(Instant.parse("2026-06-18T23:00:00Z"), tokyoResult)
    }

    @Test
    fun suppressionSkipsNextRecurringLocalDate() {
        val result =
            calculator("2026-06-22T06:00:00Z").nextTrigger(
                alarm(hour = 7, minute = 0, days = "1,3,5")
                    .copy(suppressedThroughEpochDay = LocalDate.parse("2026-06-22").toEpochDay())
            )

        assertEquals(Instant.parse("2026-06-24T07:00:00Z"), result)
    }

    @Test
    fun suppressionFindsFollowingAlternatingWeekOccurrence() {
        val result =
            calculator("2026-06-22T06:00:00Z").nextTrigger(
                alarm(hour = 7, minute = 0, days = "1", weekParity = "EVEN")
                    .copy(suppressedThroughEpochDay = LocalDate.parse("2026-06-22").toEpochDay())
            )

        assertEquals(Instant.parse("2026-07-06T07:00:00Z"), result)
    }

    @Test
    fun suppressionExpiresWhenSkippedOccurrenceTimeArrives() {
        val alarm =
            alarm(hour = 23, minute = 52, days = "1,2,3,4,5,6,7")
                .copy(suppressedThroughEpochDay = LocalDate.parse("2026-07-27").toEpochDay())

        val result = calculator("2026-07-27T23:55:00Z").suppressionExpiresAt(alarm)

        assertEquals(Instant.parse("2026-07-27T23:52:00Z"), result)
    }

    @Test
    fun suppressionInDstOverlapExpiresAfterSecondOccurrence() {
        val zone = ZoneId.of("America/New_York")
        val alarm =
            alarm(hour = 1, minute = 30, days = "7")
                .copy(suppressedThroughEpochDay = LocalDate.parse("2026-11-01").toEpochDay())

        val result = calculator("2026-11-01T04:00:00Z", zone).suppressionExpiresAt(alarm)

        assertEquals(
            ZonedDateTime
                .ofLocal(
                    LocalDateTime.of(2026, 11, 1, 1, 30),
                    zone,
                    ZoneOffset.ofHours(-5)
                ).toInstant(),
            result
        )
    }

    @Test
    fun midnightSuppressionIsConsumedAtTwelveTwentyFourAm() {
        val zone = ZoneId.of("Europe/Moscow")
        val alarm =
            alarm(hour = 0, minute = 23, days = "1,2,3,4,5,6,7")
                .copy(suppressedThroughEpochDay = LocalDate.parse("2026-07-28").toEpochDay())

        val isPending =
            calculator("2026-07-27T21:24:00Z", zone)
                .isSuppressionPending(alarm)

        assertFalse(isPending)
    }

    @Test
    fun noonSuppressionIsStillPendingAtTwelveTwentyFourAm() {
        val zone = ZoneId.of("Europe/Moscow")
        val alarm =
            alarm(hour = 12, minute = 23, days = "1,2,3,4,5,6,7")
                .copy(suppressedThroughEpochDay = LocalDate.parse("2026-07-28").toEpochDay())

        val isPending =
            calculator("2026-07-27T21:24:00Z", zone)
                .isSuppressionPending(alarm)

        assertTrue(isPending)
    }

    private fun calculator(
        now: String,
        zoneId: ZoneId = ZoneId.of("UTC")
    ): AlarmTimeCalculator = AlarmTimeCalculator(Clock.fixed(Instant.parse(now), ZoneOffset.UTC), zoneId)

    private fun alarm(
        hour: Int,
        minute: Int,
        days: String = "",
        weekParity: String = "EVERY"
    ) = Alarm(
        hour = hour,
        minute = minute,
        daysOfWeek = days,
        weekParity = weekParity,
        puzzlesList = "MATH"
    )
}
