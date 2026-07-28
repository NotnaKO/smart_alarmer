package com.example.smartalarmer.scheduler

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.domain.repeatDays
import com.example.smartalarmer.domain.repeatWeekParity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.IsoFields

class AlarmTimeCalculator(
    private val clock: Clock,
    private val zoneId: ZoneId
) {
    fun nextTrigger(alarm: Alarm): Instant {
        val now = clock.instant()
        val today = now.atZone(zoneId).toLocalDate()
        val repeatDays = alarm.repeatDays
        val repeatWeekParity = alarm.repeatWeekParity
        val firstSearchDate =
            if (repeatDays.isOneTime) {
                alarm.oneTimeDateEpochDay?.let(LocalDate::ofEpochDay) ?: today
            } else {
                alarm.suppressedThroughEpochDay
                    ?.let(LocalDate::ofEpochDay)
                    ?.plusDays(1)
                    ?.takeIf { it > today }
                    ?: today
            }
        val searchRange =
            when {
                !repeatDays.isOneTime -> 0..14
                alarm.oneTimeDateEpochDay != null -> 0..0
                else -> 0..1
            }

        for (daysAhead in searchRange) {
            val date = firstSearchDate.plusDays(daysAhead.toLong())
            if (!repeatDays.isOneTime) {
                val matchesDay = repeatDays.values.any { it.isoValue == date.dayOfWeek.value }
                val isoWeekNumber = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                if (!matchesDay || !repeatWeekParity.includes(isoWeekNumber)) continue
            }

            resolveOccurrences(date, alarm).firstOrNull { it > now }?.let { return it }
        }

        error("Unable to calculate a future trigger for alarm ${alarm.id}")
    }

    fun suppressionExpiresAt(alarm: Alarm): Instant? {
        val suppressedDate =
            alarm.suppressedThroughEpochDay
                ?.let(LocalDate::ofEpochDay)
                ?: return null
        return resolveOccurrences(suppressedDate, alarm).maxOrNull()
    }

    fun isSuppressionPending(alarm: Alarm): Boolean = suppressionExpiresAt(alarm)?.isAfter(clock.instant()) == true

    private fun resolveOccurrences(
        date: LocalDate,
        alarm: Alarm
    ): List<Instant> {
        val localDateTime = LocalDateTime.of(date.year, date.month, date.dayOfMonth, alarm.hour, alarm.minute)
        val validOffsets = zoneId.rules.getValidOffsets(localDateTime)
        val occurrences =
            if (validOffsets.isEmpty()) {
                // A DST gap has no exact wall-clock match. java.time shifts it forward by the gap length.
                listOf(localDateTime.atZone(zoneId).toInstant())
            } else {
                validOffsets.map { offset ->
                    ZonedDateTime.ofLocal(localDateTime, zoneId, offset).toInstant()
                }
            }
        return occurrences.sorted()
    }
}
