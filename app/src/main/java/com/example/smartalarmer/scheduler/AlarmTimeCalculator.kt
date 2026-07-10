package com.example.smartalarmer.scheduler

import com.example.smartalarmer.data.Alarm
import com.example.smartalarmer.domain.repeatDays
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimeCalculator(
    private val clock: Clock,
    private val zoneId: ZoneId
) {
    fun nextTrigger(alarm: Alarm): Instant {
        val now = clock.instant()
        val today = now.atZone(zoneId).toLocalDate()
        val repeatDays = alarm.repeatDays
        val searchRange = if (repeatDays.isOneTime) 0..1 else 0..7

        for (daysAhead in searchRange) {
            val date = today.plusDays(daysAhead.toLong())
            if (!repeatDays.isOneTime && repeatDays.values.none { it.isoValue == date.dayOfWeek.value }) {
                continue
            }

            resolveOccurrences(date, alarm, now).firstOrNull()?.let { return it }
        }

        error("Unable to calculate a future trigger for alarm ${alarm.id}")
    }

    private fun resolveOccurrences(date: LocalDate, alarm: Alarm, now: Instant): List<Instant> {
        val localDateTime = LocalDateTime.of(date.year, date.month, date.dayOfMonth, alarm.hour, alarm.minute)
        val validOffsets = zoneId.rules.getValidOffsets(localDateTime)
        val occurrences = if (validOffsets.isEmpty()) {
            // A DST gap has no exact wall-clock match. java.time shifts it forward by the gap length.
            listOf(localDateTime.atZone(zoneId).toInstant())
        } else {
            validOffsets.map { offset ->
                ZonedDateTime.ofLocal(localDateTime, zoneId, offset).toInstant()
            }
        }
        return occurrences.filter { it > now }.sorted()
    }
}
